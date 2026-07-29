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
import android.os.IBinder
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import com.lazyapps.steparena.R
import com.lazyapps.steparena.app.MainActivity
import com.lazyapps.steparena.tracking.BootSession
import com.lazyapps.steparena.tracking.DailyStepSummary
import com.lazyapps.steparena.tracking.StepCounter
import com.lazyapps.steparena.tracking.StepEventResult
import com.lazyapps.steparena.tracking.StepTrackingState
import com.lazyapps.steparena.tracking.TrackingStateRepository
import com.lazyapps.steparena.tracking.TrackingStatus
import com.lazyapps.steparena.tracking.TrackingStopReason
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

class StepTrackingService : Service(), SensorEventListener {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private lateinit var repository: TrackingStateRepository
    private lateinit var sensorManager: SensorManager
    private val counter = StepCounter()
    private var state = StepTrackingState()
    private var lastPersistedSteps = 0L
    private var lastPersistedAt = Instant.EPOCH
    private var lastNotifiedSteps = 0L
    private var lastNotifiedAt = Instant.EPOCH
    private var heartbeatJob: Job? = null

    override fun onCreate() {
        super.onCreate()
        repository = TrackingStateRepository(applicationContext)
        sensorManager = getSystemService(SensorManager::class.java)
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            stopTracking(TrackingStopReason.USER_REQUEST)
            return START_NOT_STICKY
        }
        promote(NotificationModel(0, null, "計測を準備中"))
        scope.launch { restoreAndRegister() }
        return START_STICKY
    }

    private suspend fun restoreAndRegister() {
        state = repository.current()
        if (!state.trackingRequested) {
            stopSelf()
            return
        }
        val now = Instant.now()
        state = repository.update {
            it.copy(
                trackingStatus = TrackingStatus.STARTING,
                lastServiceStartedAt = now,
                sessionId = it.sessionId ?: UUID.randomUUID().toString(),
            )
        }
        val permissionGranted = ContextCompat.checkSelfPermission(
            this, Manifest.permission.ACTIVITY_RECOGNITION,
        ) == PackageManager.PERMISSION_GRANTED
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
            Log.w(TAG, "event=sensor_registration status=blocked reason=$status")
            stopSelf()
            return
        }
        val registered = sensorManager.registerListener(this, sensor, SensorManager.SENSOR_DELAY_NORMAL)
        state = repository.update {
            it.copy(trackingStatus = if (registered) TrackingStatus.TRACKING else TrackingStatus.ERROR)
        }
        Log.i(TAG, "event=sensor_registration status=${if (registered) "success" else "failure"}")
        if (!registered) {
            stopSelf()
            return
        }
        heartbeatJob?.cancel()
        heartbeatJob = scope.launch {
            while (true) {
                delay(HEARTBEAT_INTERVAL_MILLIS)
                val heartbeat = Instant.now()
                state = repository.update {
                    it.copy(lastHeartbeatAt = heartbeat, trackingStatus = TrackingStatus.TRACKING)
                }
                Log.d(TAG, "event=heartbeat status=tracking")
                updateNotificationIfNeeded(heartbeat, force = true)
            }
        }
    }

    override fun onSensorChanged(event: SensorEvent) {
        val raw = event.values.firstOrNull() ?: return
        scope.launch {
            val now = Instant.now()
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
                Log.i(TAG, "event=date_changed")
            }
            val result = counter.accept(raw, state, now, zone, BootSession.current())
            state = result.state
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
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit
    override fun onBind(intent: Intent?): IBinder? = null

    private fun stopTracking(reason: TrackingStopReason) {
        sensorManager.unregisterListener(this)
        heartbeatJob?.cancel()
        scope.launch {
            val now = Instant.now()
            state = repository.update {
                state.copy(
                    trackingRequested = false,
                    trackingStatus = TrackingStatus.STOPPED,
                    lastServiceStoppedAt = now,
                    lastStopReason = reason,
                    sessionId = null,
                )
            }
            Log.i(TAG, "event=service_stopped reason=$reason")
            ServiceCompat.stopForeground(this@StepTrackingService, ServiceCompat.STOP_FOREGROUND_REMOVE)
            stopSelf()
        }
    }

    override fun onDestroy() {
        sensorManager.unregisterListener(this)
        heartbeatJob?.cancel()
        scope.cancel()
        super.onDestroy()
    }

    private fun updateNotificationIfNeeded(now: Instant, force: Boolean = false) {
        if (
            force || state.accumulatedTodaySteps - lastNotifiedSteps >= 10 ||
            Duration.between(lastNotifiedAt, now).seconds >= 15
        ) {
            val model = NotificationModel(state.accumulatedTodaySteps, state.lastSensorEventAt, "計測中")
            getSystemService(NotificationManager::class.java).notify(NOTIFICATION_ID, notification(model))
            lastNotifiedSteps = state.accumulatedTodaySteps
            lastNotifiedAt = now
        }
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
        val channel = NotificationChannel(CHANNEL_ID, "歩数計測", NotificationManager.IMPORTANCE_LOW).apply {
            description = "StepArenaの歩数計測状態"
            setSound(null, null)
            enableVibration(false)
            setShowBadge(false)
        }
        getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }

    private fun notification(model: NotificationModel): Notification {
        val open = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val stop = PendingIntent.getService(
            this, 1, Intent(this, StepTrackingService::class.java).setAction(ACTION_STOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val updated = model.lastUpdated?.atZone(ZoneId.systemDefault())
            ?.format(DateTimeFormatter.ofPattern("HH:mm")) ?: "--:--"
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle("StepArenaで歩数を計測中")
            .setContentText("今日 ${model.steps}歩・最終更新 $updated")
            .setSubText(model.statusLabel)
            .setContentIntent(open)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .addAction(0, "アプリを開く", open)
            .addAction(0, "計測を停止", stop)
            .build()
    }

    data class NotificationModel(
        val steps: Long,
        val lastUpdated: Instant?,
        val statusLabel: String,
    )

    companion object {
        const val ACTION_START = "com.lazyapps.steparena.action.START_TRACKING"
        const val ACTION_STOP = "com.lazyapps.steparena.action.STOP_TRACKING"
        private const val CHANNEL_ID = "step_tracking"
        private const val NOTIFICATION_ID = 2001
        private const val HEARTBEAT_INTERVAL_MILLIS = 5 * 60 * 1000L
        private const val TAG = "StepTracking"
    }
}
