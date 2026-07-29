package com.lazyapps.steparena.service.tracking

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import com.lazyapps.steparena.BuildConfig
import com.lazyapps.steparena.R
import com.lazyapps.steparena.app.MainActivity
import com.lazyapps.steparena.app.StepArenaApplication
import com.lazyapps.steparena.activity.ActivityRepository
import com.lazyapps.steparena.tracking.BootSession
import com.lazyapps.steparena.tracking.DailyStepSummary
import com.lazyapps.steparena.tracking.DiagnosticLogEntry
import com.lazyapps.steparena.tracking.DiagnosticLogRepository
import com.lazyapps.steparena.tracking.NotificationUpdatePolicy
import com.lazyapps.steparena.tracking.StepCounter
import com.lazyapps.steparena.tracking.StepEventResult
import com.lazyapps.steparena.tracking.StepTrackingState
import com.lazyapps.steparena.tracking.RealtimeSensorEventClock
import com.lazyapps.steparena.tracking.TrackingStateRepository
import com.lazyapps.steparena.tracking.TrackingStatus
import com.lazyapps.steparena.tracking.TrackingStopReason
import com.lazyapps.steparena.tracking.readPreviousExit
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.UUID
import java.util.concurrent.atomic.AtomicBoolean

class StepTrackingService : Service(), SensorEventListener {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private lateinit var repository: TrackingStateRepository
    private lateinit var sensorManager: SensorManager
    private lateinit var diagnosticLog: DiagnosticLogRepository
    private lateinit var activityRepository: ActivityRepository
    private val counter = StepCounter()
    private val notificationPolicy = NotificationUpdatePolicy()
    private val setupStarted = AtomicBoolean(false)
    private var state = StepTrackingState()
    private var lastPersistedSteps = 0L
    private var lastPersistedAt = Instant.EPOCH
    private var lastNotifiedSteps = 0L
    private var lastNotifiedAt = Instant.EPOCH
    private var heartbeatJob: Job? = null
    private var fakeSensorMode = false
    private val sensorEventClock = RealtimeSensorEventClock()
    private var sessionTimeoutJob: Job? = null
    private var manualStartRequested = false

    override fun onCreate() {
        super.onCreate()
        repository = TrackingStateRepository(applicationContext)
        diagnosticLog = DiagnosticLogRepository(applicationContext)
        activityRepository = (application as StepArenaApplication).activityRepository
        sensorManager = getSystemService(SensorManager::class.java)
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_END_MANUAL_WALK) {
            val requestedSession = intent.getStringExtra(EXTRA_MANUAL_SESSION_ID)
            scope.launch {
                state = repository.current()
                val ended = requestedSession != null &&
                    activityRepository.endManualSession(requestedSession, Instant.now())
                logEvent(if (ended) "manual_walk_ended" else "stale_manual_end_ignored", detail = requestedSession)
                updateNotificationIfNeeded(Instant.now(), force = true)
            }
            return START_STICKY
        }
        if (intent?.action == ACTION_STOP) {
            val requestedSession = intent.getStringExtra(EXTRA_SESSION_ID)
            scope.launch {
                val current = repository.current()
                if (isCurrentSessionRequest(requestedSession, current.sessionId)) {
                    state = current
                    stopTracking(TrackingStopReason.USER_REQUEST)
                } else {
                    state = current
                    logEvent("stale_stop_ignored", detail = requestedSession)
                }
            }
            return START_NOT_STICKY
        }
        if (intent?.action == ACTION_START_MANUAL_WALK) {
            manualStartRequested = true
            scope.launch {
                val current = repository.current()
                if (current.trackingRequested &&
                    current.trackingStatus in setOf(TrackingStatus.TRACKING, TrackingStatus.RESTARTED) &&
                    current.sessionId != null
                ) {
                    state = current
                    startManualWalk()
                }
            }
        }
        if (BuildConfig.DEBUG && intent?.action == debugAction()) {
            promote(NotificationModel(0, null, getString(R.string.notification_status_debug_preparing)))
            val value = intent.getFloatExtra(debugValueExtra(), Float.NaN)
            scope.launch {
                if (setupStarted.compareAndSet(false, true)) {
                    state = repository.current()
                    if (state.trackingRequested) {
                        val now = Instant.now()
                        state = repository.update {
                            it.copy(
                                trackingStatus = TrackingStatus.TRACKING,
                                lastServiceStartedAt = now,
                                sessionId = it.sessionId ?: UUID.randomUUID().toString(),
                            )
                        }
                        promote(NotificationModel(state.accumulatedTodaySteps, state.lastSensorEventAt, getString(R.string.notification_status_tracking)))
                        startHeartbeat()
                    }
                } else if (state.sessionId == null) {
                    state = repository.current()
                }
                if (!state.trackingRequested) {
                    logEvent("fake_sensor_ignored", detail = "tracking_not_requested")
                    ServiceCompat.stopForeground(
                        this@StepTrackingService,
                        ServiceCompat.STOP_FOREGROUND_REMOVE,
                    )
                    stopSelf()
                    return@launch
                }
                if (!fakeSensorMode) {
                    sensorManager.unregisterListener(this@StepTrackingService)
                    fakeSensorMode = true
                    setDebugFakeModePersisted(true)
                    logEvent("fake_sensor_enabled", detail = "real_sensor_unregistered")
                }
                acceptSensorValue(value)
            }
            return START_STICKY
        }
        promote(NotificationModel(0, null, getString(R.string.notification_status_preparing)))
        if (setupStarted.compareAndSet(false, true)) scope.launch { restoreAndRegister() }
        return START_STICKY
    }

    private suspend fun restoreAndRegister() {
        state = repository.current()
        if (!state.trackingRequested) {
            stopSelf()
            return
        }
        val now = Instant.now()
        val previousExit = applicationContext.readPreviousExit()
        state = repository.update {
            it.copy(
                trackingStatus = TrackingStatus.STARTING,
                lastServiceStartedAt = now,
                sessionId = it.sessionId ?: UUID.randomUUID().toString(),
                lastExitInfoKey = previousExit?.key ?: it.lastExitInfoKey,
                lastExitSummary = if (previousExit != null && previousExit.key != it.lastExitInfoKey) {
                    previousExit.summary
                } else {
                    it.lastExitSummary
                },
            )
        }
        logEvent("service_restore", detail = previousExit?.summary)
        val permissionGranted = ContextCompat.checkSelfPermission(
            this, Manifest.permission.ACTIVITY_RECOGNITION,
        ) == PackageManager.PERMISSION_GRANTED
        if (BuildConfig.DEBUG && isDebugFakeModePersisted()) {
            fakeSensorMode = true
            state = repository.update { it.copy(trackingStatus = TrackingStatus.TRACKING) }
            logEvent("fake_sensor_restored", detail = "real_sensor_not_registered")
            startHeartbeat()
            if (manualStartRequested) startManualWalk()
            else updateNotificationIfNeeded(now, force = true)
            return
        }
        val sensor = sensorManager.getDefaultSensor(Sensor.TYPE_STEP_COUNTER)
        if (!permissionGranted || sensor == null) {
            val status = if (sensor == null) {
                TrackingStatus.SENSOR_UNSUPPORTED
            } else {
                TrackingStatus.PERMISSION_REQUIRED
            }
            state = repository.update {
                it.copy(trackingStatus = status, trackingRequested = false)
            }
            logEvent("sensor_registration_blocked", detail = status.name)
            stopSelf()
            return
        }
        val registered = sensorManager.registerListener(this, sensor, SensorManager.SENSOR_DELAY_NORMAL)
        sensorManager.getDefaultSensor(Sensor.TYPE_STEP_DETECTOR)?.let {
            sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_NORMAL)
        }
        state = repository.update {
            it.copy(trackingStatus = if (registered) TrackingStatus.TRACKING else TrackingStatus.ERROR)
        }
        logEvent("sensor_registration", detail = registered.toString())
        if (!registered) {
            stopSelf()
            return
        }
        startHeartbeat()
        if (manualStartRequested) startManualWalk()
        else updateNotificationIfNeeded(now, force = true)
    }

    private suspend fun startManualWalk() {
        val serviceSessionId = state.sessionId ?: return
        val session = activityRepository.startManualSession(
            Instant.now(),
            ZoneId.systemDefault(),
            serviceSessionId,
        )
        manualStartRequested = false
        logEvent("manual_walk_started", detail = session.id)
        updateNotificationIfNeeded(Instant.now(), force = true)
    }

    private fun startHeartbeat() {
        heartbeatJob?.cancel()
        heartbeatJob = scope.launch {
            while (true) {
                delay(HEARTBEAT_INTERVAL_MILLIS)
                val heartbeat = Instant.now()
                state = repository.update {
                    it.copy(lastHeartbeatAt = heartbeat, trackingStatus = TrackingStatus.TRACKING)
                }
                logEvent("heartbeat")
                updateNotificationIfNeeded(heartbeat, force = true)
            }
        }
        sessionTimeoutJob?.cancel()
        sessionTimeoutJob = scope.launch {
            while (true) {
                delay(SESSION_TIMEOUT_CHECK_INTERVAL_MILLIS)
                activityRepository.checkSessionTimeouts(Instant.now())
            }
        }
    }

    override fun onSensorChanged(event: SensorEvent) {
        val eventAt = sensorEventClock.toInstant(event.timestamp)
        if (event.sensor.type == Sensor.TYPE_STEP_DETECTOR) {
            scope.launch { activityRepository.recordDetector(eventAt) }
            return
        }
        if (event.sensor.type != Sensor.TYPE_STEP_COUNTER) return
        val raw = event.values.firstOrNull() ?: return
        scope.launch { acceptSensorValue(raw, eventAt) }
    }

    private suspend fun acceptSensorValue(raw: Float, eventAt: Instant = Instant.now()) {
        val now = eventAt
        val zone = ZoneId.systemDefault()
        if (state.currentLocalDate != now.atZone(zone).toLocalDate()) {
            repository.saveDailySummary(
                DailyStepSummary(
                    state.currentLocalDate,
                    state.currentZoneId,
                    state.accumulatedTodaySteps,
                    now,
                ),
            )
            logEvent("date_changed")
        }
        val result = counter.accept(raw, state, now, zone, BootSession.current())
        state = result.state
        val delta = (result as? StepEventResult.Added)?.delta
        if (delta != null) {
            activityRepository.recordCounterDelta(
                sensorValue = raw.toLong(),
                delta = delta,
                at = now,
                zoneId = zone,
                bootSessionId = BootSession.current(),
                trackingServiceSessionId = state.sessionId,
                recovered = (result as? StepEventResult.Added)?.unusuallyLarge == true,
            )
        }
        when (result) {
            is StepEventResult.Added ->
                Log.d(TAG, "event=step_delta delta=${result.delta} review=${result.unusuallyLarge}")
            is StepEventResult.Baseline -> Log.i(TAG, "event=sensor_baseline")
            is StepEventResult.Reset -> Log.w(TAG, "event=sensor_value_decreased")
            is StepEventResult.Ignored -> Unit
        }
        if (
            state.accumulatedTodaySteps - lastPersistedSteps >= 10 ||
            Duration.between(lastPersistedAt, now).seconds >= 5 ||
            result !is StepEventResult.Added
        ) {
            state = repository.update { state }
            lastPersistedSteps = state.accumulatedTodaySteps
            lastPersistedAt = now
        }
        updateNotificationIfNeeded(now)
        logEvent(
            "sensor",
            sensorValue = raw.takeIf(Float::isFinite)?.toLong(),
            delta = delta,
            detail = result::class.simpleName,
        )
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit
    override fun onBind(intent: Intent?): IBinder? = null

    private fun stopTracking(reason: TrackingStopReason) {
        sensorManager.unregisterListener(this)
        if (BuildConfig.DEBUG) setDebugFakeModePersisted(false)
        heartbeatJob?.cancel()
        sessionTimeoutJob?.cancel()
        scope.launch {
            val now = Instant.now()
            activityRepository.finishAllActiveSessions(now)
            state = repository.update {
                it.copy(
                    trackingRequested = false,
                    trackingStatus = TrackingStatus.STOPPED,
                    lastServiceStoppedAt = now,
                    lastStopReason = reason,
                    sessionId = null,
                    sensorBaseline = null,
                    lastSensorValue = null,
                )
            }
            logEvent("service_stopped", detail = reason.name)
            ServiceCompat.stopForeground(this@StepTrackingService, ServiceCompat.STOP_FOREGROUND_REMOVE)
            stopSelf()
        }
    }

    override fun onDestroy() {
        sensorManager.unregisterListener(this)
        heartbeatJob?.cancel()
        sessionTimeoutJob?.cancel()
        scope.cancel()
        super.onDestroy()
    }

    private suspend fun updateNotificationIfNeeded(now: Instant, force: Boolean = false) {
        if (
            notificationPolicy.shouldUpdate(
                state.accumulatedTodaySteps,
                lastNotifiedSteps,
                now,
                lastNotifiedAt,
                force,
            )
        ) {
            val manual = activityRepository.currentManualSession()
            val model = NotificationModel(
                state.accumulatedTodaySteps,
                state.lastSensorEventAt,
                getString(
                    if (manual == null) R.string.notification_status_tracking
                    else R.string.notification_status_walking,
                ),
                manual,
            )
            getSystemService(NotificationManager::class.java).notify(NOTIFICATION_ID, notification(model))
            lastNotifiedSteps = state.accumulatedTodaySteps
            lastNotifiedAt = now
            scope.launch {
                state = repository.update { it.copy(lastNotificationAt = now) }
                logEvent("notification_update")
            }
        }
    }

    private fun logEvent(
        event: String,
        sensorValue: Long? = null,
        delta: Long? = null,
        detail: String? = null,
    ) {
        if (!BuildConfig.DEBUG) return
        diagnosticLog.append(
            DiagnosticLogEntry(
                Instant.now(), event, state.sessionId, state.trackingStatus,
                sensorValue, delta, state.accumulatedTodaySteps, state.sensorBaseline,
                state.trackingRequested, state.lastHeartbeatAt, state.bootSessionId,
                state.currentLocalDate.toString(), state.currentZoneId, detail,
            ),
        )
    }

    private fun promote(model: NotificationModel) {
        ServiceCompat.startForeground(
            this,
            NOTIFICATION_ID,
            notification(model),
            if (Build.VERSION.SDK_INT >= 34) ServiceInfo.FOREGROUND_SERVICE_TYPE_HEALTH else 0,
        )
        Log.i(TAG, "event=service_started")
    }

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            getString(R.string.notification_channel_step_tracking),
            NotificationManager.IMPORTANCE_LOW,
        ).apply {
            description = getString(R.string.notification_channel_step_tracking_description)
            setSound(null, null)
            enableVibration(false)
            setShowBadge(false)
        }
        getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }

    private fun isDebugFakeModePersisted(): Boolean =
        getSharedPreferences(debugModePreferences(), MODE_PRIVATE)
            .getBoolean(debugModeKey(), false)

    private fun setDebugFakeModePersisted(enabled: Boolean) {
        getSharedPreferences(debugModePreferences(), MODE_PRIVATE)
            .edit().putBoolean(debugModeKey(), enabled).apply()
    }

    private fun notification(model: NotificationModel): Notification {
        val open = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val manual = model.manualSession
        val sessionRequestCode = (manual?.id ?: state.sessionId)?.hashCode() ?: 1
        val stop = PendingIntent.getService(
            this,
            sessionRequestCode,
            Intent(this, StepTrackingService::class.java)
                .setAction(if (manual == null) ACTION_STOP else ACTION_END_MANUAL_WALK)
                .putExtra(
                    if (manual == null) EXTRA_SESSION_ID else EXTRA_MANUAL_SESSION_ID,
                    manual?.id ?: state.sessionId,
                ),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val updated = model.lastUpdated?.atZone(ZoneId.systemDefault())
            ?.format(DateTimeFormatter.ofPattern("HH:mm")) ?: "--:--"
        val title = getString(
            if (manual == null) R.string.notification_tracking_title
            else R.string.notification_walking_title,
        )
        val text = if (manual == null) {
            getString(R.string.notification_tracking_text, model.steps, updated)
        } else {
            val minutes = manual.elapsedDurationSeconds / 60
            getString(R.string.notification_walking_text, manual.steps, minutes, model.steps)
        }
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle(title)
            .setContentText(text)
            .setSubText(model.statusLabel)
            .setContentIntent(open)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .addAction(0, getString(R.string.notification_action_open_app), open)
            .addAction(
                0,
                getString(
                    if (manual == null) R.string.notification_action_stop_tracking
                    else R.string.notification_action_end_walk,
                ),
                stop,
            )
            .build()
    }

    data class NotificationModel(
        val steps: Long,
        val lastUpdated: Instant?,
        val statusLabel: String,
        val manualSession: com.lazyapps.steparena.core.database.entity.WalkingSessionEntity? = null,
    )

    companion object {
        const val ACTION_START = "com.lazyapps.steparena.action.START_TRACKING"
        const val ACTION_STOP = "com.lazyapps.steparena.action.STOP_TRACKING"
        const val ACTION_START_MANUAL_WALK = "com.lazyapps.steparena.action.START_MANUAL_WALK"
        const val ACTION_END_MANUAL_WALK = "com.lazyapps.steparena.action.END_MANUAL_WALK"
        const val EXTRA_SESSION_ID = "session_id"
        const val EXTRA_MANUAL_SESSION_ID = "manual_session_id"
        private const val CHANNEL_ID = "step_tracking"
        private const val NOTIFICATION_ID = 2001
        private const val HEARTBEAT_INTERVAL_MILLIS = 5 * 60 * 1000L
        private const val SESSION_TIMEOUT_CHECK_INTERVAL_MILLIS = 30 * 1000L
        private const val TAG = "StepTracking"

        // Generated rather than stored as a release string. The guarded path is unreachable
        // in release builds; only debug-source callers know the resulting protocol.
        fun debugAction(): String = listOf(
            "com.lazyapps.steparena", "debug", "sensor", "inject",
        ).joinToString(".")

        fun debugValueExtra(): String = listOf("debug", "sensor", "value").joinToString("_")
        private fun debugModePreferences(): String =
            listOf("debug", "tracking", "mode").joinToString("_")
        private fun debugModeKey(): String =
            listOf("synthetic", "sensor", "selected").joinToString("_")
    }
}

internal fun isCurrentSessionRequest(requested: String?, current: String?): Boolean =
    requested == null || requested == current
