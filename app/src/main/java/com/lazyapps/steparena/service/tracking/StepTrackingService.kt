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
import com.lazyapps.steparena.activity.DailyStepGoal
import com.lazyapps.steparena.game.DetectorEvidence
import com.lazyapps.steparena.game.MotionSample
import com.lazyapps.steparena.tracking.BootSession
import com.lazyapps.steparena.tracking.DailyStepSummary
import com.lazyapps.steparena.tracking.DiagnosticLogEntry
import com.lazyapps.steparena.tracking.DiagnosticLogRepository
import com.lazyapps.steparena.tracking.NotificationUpdatePolicy
import com.lazyapps.steparena.tracking.NotificationStepPreview
import com.lazyapps.steparena.tracking.NotificationStepPreviewDiagnostics
import com.lazyapps.steparena.tracking.MotionCaptureDiagnosticSnapshot
import com.lazyapps.steparena.tracking.MotionCaptureDiagnostics
import com.lazyapps.steparena.tracking.StepCounter
import com.lazyapps.steparena.tracking.StepEventResult
import com.lazyapps.steparena.tracking.StepTrackingState
import com.lazyapps.steparena.tracking.RealtimeSensorEventClock
import com.lazyapps.steparena.tracking.TrackingStateRepository
import com.lazyapps.steparena.tracking.TrackingStatus
import com.lazyapps.steparena.tracking.TrackingStopReason
import com.lazyapps.steparena.tracking.readPreviousExit
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.text.NumberFormat
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
    private val notificationPreview = NotificationStepPreview()
    private val setupGate = ServiceSetupGate()
    private val stateUpdates = ServiceStateUpdateGate()
    private var state = StepTrackingState()
    private var lastPersistedSteps = 0L
    private var lastPersistedAt = Instant.EPOCH
    private var lastNotifiedSteps = 0L
    private var lastNotifiedAt = Instant.EPOCH
    private var heartbeatJob: Job? = null
    private var fakeSensorMode = false
    private var stepCounterRegistered = false
    private var stepDetectorRegistered = false
    private val sensorSamples = Channel<SensorSample>(SENSOR_CHANNEL_CAPACITY)
    private var sensorConsumerJob: Job? = null
    private val sensorEventClock = RealtimeSensorEventClock()
    private var sessionTimeoutJob: Job? = null
    private var pendingNotificationJob: Job? = null
    private var previewExpiryJob: Job? = null
    private val previewExpiryLock = Any()
    private var manualStartRequested = false
    private var dailyStepGoal = DailyStepGoal.DEFAULT
    private val motionCapture = MotionCaptureController()
    private var motionFinishJob: Job? = null
    private var motionMaximumJob: Job? = null
    private var gyroscopeSensor: Sensor? = null
    private var accelerationSensor: Sensor? = null
    private var accelerometerFallback = false
    private val gravity = FloatArray(3)
    private var motionSensorsRegistered = false

    override fun onCreate() {
        super.onCreate()
        TrackingServiceProcessRegistry.serviceAlive = true
        NotificationStepPreviewDiagnostics.clear()
        repository = TrackingStateRepository(applicationContext)
        diagnosticLog = DiagnosticLogRepository(applicationContext)
        activityRepository = (application as StepArenaApplication).activityRepository
        val goalRepository = (application as StepArenaApplication).dailyStepGoalRepository
        dailyStepGoal = goalRepository.current()
        sensorManager = getSystemService(SensorManager::class.java)
        MotionCaptureDiagnostics.update(
            MotionCaptureDiagnosticSnapshot(
                gyroscopeAvailable = sensorManager.getDefaultSensor(Sensor.TYPE_GYROSCOPE) != null,
                accelerationMode = when {
                    sensorManager.getDefaultSensor(Sensor.TYPE_LINEAR_ACCELERATION) != null -> "LINEAR_ACCELERATION"
                    sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER) != null -> "ACCELEROMETER_FALLBACK"
                    else -> "UNAVAILABLE"
                },
            ),
        )
        createNotificationChannel()
        scope.launch {
            goalRepository.goalSteps.collect { goalSteps ->
                serializeStateUpdate {
                    val changed = dailyStepGoal != goalSteps
                    dailyStepGoal = goalSteps
                    if (changed && setupGate.isStarted && state.trackingRequested) {
                        updateNotificationIfNeeded(Instant.now(), force = true)
                    }
                }
            }
        }
        sensorConsumerJob = scope.launch {
            for (sample in sensorSamples) {
                serializeStateUpdate {
                    if (state.trackingRequested || fakeSensorMode) {
                        when (sample) {
                            is SensorSample.Counter -> acceptSensorValue(sample.raw, sample.at)
                            is SensorSample.Detector -> acceptDetectorEvent(sample.at)
                        }
                    }
                }
                if (sample is SensorSample.Counter) sample.completed?.complete(Unit)
            }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_END_MANUAL_WALK) {
            val requestedSession = intent.getStringExtra(EXTRA_MANUAL_SESSION_ID)
            scope.launch {
                serializeStateUpdate {
                    state = repository.current()
                    val ended = requestedSession != null &&
                        activityRepository.endManualSession(requestedSession, Instant.now())
                    logEvent(if (ended) "manual_walk_ended" else "stale_manual_end_ignored", detail = requestedSession)
                    updateNotificationIfNeeded(Instant.now(), force = true)
                }
            }
            return START_STICKY
        }
        if (intent?.action == ACTION_STOP) {
            val requestedSession = intent.getStringExtra(EXTRA_SESSION_ID)
            scope.launch {
                serializeStateUpdate {
                    val current = repository.current()
                    state = current
                    if (isCurrentSessionRequest(requestedSession, current.sessionId)) {
                        stopTracking(TrackingStopReason.USER_REQUEST)
                    } else {
                        logEvent("stale_stop_ignored", detail = requestedSession)
                    }
                }
            }
            return START_NOT_STICKY
        }
        if (intent?.action == ACTION_START_MANUAL_WALK) {
            scope.launch {
                serializeStateUpdate {
                    manualStartRequested = true
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
        }
        if (BuildConfig.DEBUG && intent?.action == debugAction()) {
            val firstStart = setupGate.claimInitialStart()
            if (firstStart) promote(NotificationModel(NotificationContent.Preparing))
            val value = intent.getFloatExtra(debugValueExtra(), Float.NaN)
            val keepFakeMode = intent.getBooleanExtra(debugKeepFakeModeExtra(), false)
            val sequenceCount = intent.getIntExtra(debugSequenceCountExtra(), 0)
            val sequenceSteps = intent.getLongExtra(debugSequenceStepsExtra(), 1L)
            val sequenceInterval = intent.getLongExtra(debugSequenceIntervalExtra(), 10L)
            scope.launch {
                val accepted = serializeStateUpdate {
                    if (firstStart) {
                        state = repository.current()
                        val debugZone = ZoneId.systemDefault()
                        val debugDate = Instant.now().atZone(debugZone).toLocalDate()
                        val officialSteps = activityRepository.officialDailySteps(debugDate, debugZone)
                        publishNotificationPreview(notificationPreview.reset(officialSteps, debugDate))
                        if (state.trackingRequested) {
                            val now = Instant.now()
                            state = repository.update {
                                it.copy(
                                    trackingStatus = TrackingStatus.TRACKING,
                                    lastServiceStartedAt = now,
                                    sessionId = it.sessionId ?: UUID.randomUUID().toString(),
                                )
                            }
                            promote(
                                NotificationModel(
                                    NotificationContent.Tracking(officialSteps, dailyStepGoal.toLong()),
                                    state.lastSensorEventAt,
                                ),
                            )
                            startHeartbeat()
                        }
                    } else {
                        // The debug bridge can reset the repository before sending a new
                        // sequence. Re-read it inside the same serialization boundary so an
                        // already-running service cannot apply the new raw value to stale local state.
                        state = repository.current()
                    }
                    if (!state.trackingRequested) {
                        logEvent("fake_sensor_ignored", detail = "tracking_not_requested")
                        ServiceCompat.stopForeground(this@StepTrackingService, ServiceCompat.STOP_FOREGROUND_REMOVE)
                        stopSelf()
                        false
                    } else {
                        fakeSensorMode = true
                        logEvent("fake_sensor_enabled", detail = "current_intent_only")
                        true
                    }
                } ?: false
                if (!accepted) return@launch
                var sequenceValue = value
                repeat(sequenceCount + 1) { index ->
                    if (index > 0) {
                        delay(sequenceInterval)
                        sequenceValue += sequenceSteps.toFloat()
                    }
                    val completed = CompletableDeferred<Unit>()
                    sensorSamples.send(SensorSample.Counter(sequenceValue, Instant.now(), completed))
                    completed.await()
                }
                serializeStateUpdate {
                    if (keepFakeMode) {
                        logEvent("fake_sensor_retained", detail = "sequence_active")
                    } else {
                        fakeSensorMode = false
                        logEvent("fake_sensor_disabled", detail = "intent_completed")
                        if (!stepCounterRegistered && state.trackingRequested) restoreAndRegister()
                    }
                }
            }
            return START_STICKY
        }
        if (setupGate.claimInitialStart()) {
            promote(NotificationModel(NotificationContent.Preparing))
            scope.launch { serializeStateUpdate { restoreAndRegister() } }
        }
        return START_STICKY
    }

    private suspend fun restoreAndRegister() {
        state = repository.current()
        val restoreZone = ZoneId.systemDefault()
        val restoreDate = Instant.now().atZone(restoreZone).toLocalDate()
        val officialSteps = activityRepository.officialDailySteps(restoreDate, restoreZone)
        publishNotificationPreview(
            notificationPreview.reset(officialSteps, restoreDate),
        )
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
        fakeSensorMode = false
        val sensor = sensorManager.getDefaultSensor(Sensor.TYPE_STEP_COUNTER)
        if (!permissionGranted || sensor == null) {
            val status = if (sensor == null) {
                TrackingStatus.SENSOR_UNSUPPORTED
            } else {
                TrackingStatus.PERMISSION_REQUIRED
            }
            state = repository.update {
                it.copy(
                    trackingStatus = status,
                    trackingRequested = false,
                    stepCounterRegistered = false,
                    stepDetectorRegistered = false,
                    serviceRunning = false,
                )
            }
            logEvent("sensor_registration_blocked", detail = status.name)
            stopSelf()
            return
        }
        stepCounterRegistered =
            sensorManager.registerListener(this, sensor, SensorManager.SENSOR_DELAY_NORMAL)
        stepDetectorRegistered = sensorManager.getDefaultSensor(Sensor.TYPE_STEP_DETECTOR)?.let {
            sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_NORMAL)
        } ?: false
        state = repository.update {
            it.copy(
                trackingStatus = if (stepCounterRegistered) TrackingStatus.TRACKING else TrackingStatus.ERROR,
                stepCounterRegistered = stepCounterRegistered,
                stepDetectorRegistered = stepDetectorRegistered,
                serviceRunning = stepCounterRegistered,
            )
        }
        logEvent("sensor_registration", detail = stepCounterRegistered.toString())
        if (!stepCounterRegistered) {
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
                serializeStateUpdate {
                    if (!state.trackingRequested) return@serializeStateUpdate
                    val heartbeat = Instant.now()
                    state = repository.update { heartbeatState(it, heartbeat) }
                    logEvent("heartbeat")
                    updateNotificationIfNeeded(heartbeat, force = true)
                }
                delay(HEARTBEAT_INTERVAL_MILLIS)
            }
        }
        sessionTimeoutJob?.cancel()
        sessionTimeoutJob = scope.launch {
            while (true) {
                delay(SESSION_TIMEOUT_CHECK_INTERVAL_MILLIS)
                serializeStateUpdate {
                    if (state.trackingRequested) activityRepository.checkSessionTimeouts(Instant.now())
                }
            }
        }
    }

    override fun onSensorChanged(event: SensorEvent) {
        val eventAt = sensorEventClock.toInstant(event.timestamp)
        when (event.sensor.type) {
            Sensor.TYPE_GYROSCOPE -> {
                motionCapture.addGyroscope(event.toMotionSample())
                return
            }
            Sensor.TYPE_LINEAR_ACCELERATION -> {
                motionCapture.addLinearAcceleration(event.toMotionSample())
                return
            }
            Sensor.TYPE_ACCELEROMETER -> if (accelerometerFallback) {
                val alpha = 0.8f
                val linear = FloatArray(3) { index ->
                    gravity[index] = alpha * gravity[index] + (1f - alpha) * event.values[index]
                    event.values[index] - gravity[index]
                }
                motionCapture.addLinearAcceleration(
                    MotionSample(event.timestamp, linear[0], linear[1], linear[2]),
                )
                return
            }
        }
        if (event.sensor.type == Sensor.TYPE_STEP_DETECTOR) {
            sensorSamples.trySend(SensorSample.Detector(eventAt))
            return
        }
        if (event.sensor.type != Sensor.TYPE_STEP_COUNTER) return
        val raw = event.values.firstOrNull() ?: return
        sensorSamples.trySend(SensorSample.Counter(raw, eventAt))
    }

    private suspend fun acceptDetectorEvent(eventAt: Instant) {
        state = repository.update { recoverStatusOnDetectorEvent(it, eventAt) }
        val localDate = eventAt.atZone(ZoneId.systemDefault()).toLocalDate()
        val dateChanged = notificationPreview.snapshot.localDate != localDate
        publishNotificationPreview(notificationPreview.onDetector(localDate, eventAt))
        updateNotificationIfNeeded(eventAt, force = dateChanged)
        ensurePreviewExpiryJob()
        val captureStarted = !motionCapture.isCapturing()
        val windowId = motionCapture.onDetector(eventAt)
        activityRepository.recordDetector(DetectorEvidence(at = eventAt, evidenceWindowId = windowId))
        registerMotionSensors()
        motionFinishJob?.cancel()
        motionFinishJob = scope.launch {
            delay(MOTION_CAPTURE_TAIL_MILLIS)
            serializeStateUpdate { finishMotionCapture() }
        }
        if (captureStarted) {
            motionMaximumJob?.cancel()
            motionMaximumJob = scope.launch {
                delay(MOTION_CAPTURE_MAX_MILLIS)
                motionFinishJob?.cancel()
                serializeStateUpdate { finishMotionCapture() }
            }
        }
        logEvent("step_detector")
    }

    private fun registerMotionSensors() {
        if (motionSensorsRegistered || !state.trackingRequested) return
        gyroscopeSensor = sensorManager.getDefaultSensor(Sensor.TYPE_GYROSCOPE)
        accelerationSensor = sensorManager.getDefaultSensor(Sensor.TYPE_LINEAR_ACCELERATION)
        accelerometerFallback = accelerationSensor == null
        if (accelerationSensor == null) accelerationSensor = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
        val gyroRegistered = gyroscopeSensor?.let {
            sensorManager.registerListener(this, it, MOTION_SAMPLE_PERIOD_MICROS)
        } ?: false
        val accelerationRegistered = accelerationSensor?.let {
            sensorManager.registerListener(this, it, MOTION_SAMPLE_PERIOD_MICROS)
        } ?: false
        motionSensorsRegistered = gyroRegistered && accelerationRegistered
        MotionCaptureDiagnostics.update(
            MotionCaptureDiagnosticSnapshot(
                gyroscopeAvailable = gyroscopeSensor != null,
                accelerationMode = when {
                    accelerationSensor == null -> "UNAVAILABLE"
                    accelerometerFallback -> "ACCELEROMETER_FALLBACK"
                    else -> "LINEAR_ACCELERATION"
                },
                capturing = motionSensorsRegistered,
            ),
        )
        if (!motionSensorsRegistered) {
            unregisterMotionSensors()
        }
    }

    private suspend fun finishMotionCapture() {
        val window = motionCapture.finish()
        motionMaximumJob?.cancel()
        motionMaximumJob = null
        unregisterMotionSensors()
        if (window != null) {
            activityRepository.applyMotionEvidence(
                window.id, window.evidence.assessment, window.evidence.confidence,
            )
            MotionCaptureDiagnostics.update(
                MotionCaptureDiagnostics.snapshot.value.copy(
                    capturing = false,
                    lastAssessment = window.evidence.assessment,
                    lastEvaluatedAt = Instant.now(),
                ),
            )
            logEvent("motion_evaluated", detail = window.evidence.assessment.name)
        }
    }

    private fun unregisterMotionSensors() {
        gyroscopeSensor?.let { sensorManager.unregisterListener(this, it) }
        accelerationSensor?.let { sensorManager.unregisterListener(this, it) }
        gyroscopeSensor = null
        accelerationSensor = null
        accelerometerFallback = false
        motionSensorsRegistered = false
        MotionCaptureDiagnostics.clearCapture()
        gravity.fill(0f)
    }

    private fun SensorEvent.toMotionSample() = MotionSample(
        timestamp, values.getOrElse(0) { 0f }, values.getOrElse(1) { 0f }, values.getOrElse(2) { 0f },
    )

    private fun ensurePreviewExpiryJob() {
        synchronized(previewExpiryLock) {
            if (previewExpiryJob?.isActive == true) return
            previewExpiryJob = scope.launch {
                while (notificationPreview.snapshot.pendingDetectorSteps > 0) {
                    delay(1_000)
                    serializeStateUpdate { updateNotificationIfNeeded(Instant.now()) }
                }
                val restart = synchronized(previewExpiryLock) {
                    previewExpiryJob = null
                    notificationPreview.snapshot.pendingDetectorSteps > 0
                }
                if (restart) ensurePreviewExpiryJob()
            }
        }
    }

    private suspend fun acceptSensorValue(raw: Float, eventAt: Instant = Instant.now()) {
        val now = eventAt
        val zone = ZoneId.systemDefault()
        if (state.currentLocalDate != now.atZone(zone).toLocalDate()) {
            motionFinishJob?.cancel()
            motionMaximumJob?.cancel()
            motionCapture.reset()
            unregisterMotionSensors()
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
        val bootSession = BootSession.current(applicationContext)
        val previousState = state
        val result = counter.accept(raw, previousState, now, zone, bootSession)
        state = recoverStatusOnCounterEvent(previousState, result, raw)
        val delta = (result as? StepEventResult.Added)?.delta
        val persistedUpdate = if (delta != null) {
            activityRepository.recordCounterDelta(
                sensorValue = raw.toLong(),
                delta = delta,
                at = now,
                zoneId = zone,
                bootSessionId = bootSession,
                trackingServiceSessionId = state.sessionId,
                recovered = (result as? StepEventResult.Added)?.unusuallyLarge == true,
                detectorAvailable = stepDetectorRegistered,
                motionSensorAvailable = motionSensorsRegistered,
            )
        } else null
        when (result) {
            is StepEventResult.Added ->
                Log.d(TAG, "event=step_delta delta=${result.delta} review=${result.unusuallyLarge}")
            is StepEventResult.Baseline -> Log.i(TAG, "event=sensor_baseline")
            is StepEventResult.Reset -> Log.w(TAG, "event=sensor_value_decreased")
            is StepEventResult.Ignored -> Unit
        }
        // Publish after the Room transaction so Home and Records follow the same sample.
        val counterState = state
        state = repository.update { persisted ->
            mergeCounterStateWithPersistedDiagnostics(counterState, persisted)
        }
        lastPersistedSteps = state.accumulatedTodaySteps
        lastPersistedAt = now
        val previewDateChanged = notificationPreview.snapshot.localDate != state.currentLocalDate
        publishNotificationPreview(
            notificationPreview.onCounter(
                persistedUpdate?.officialDailySteps ?: activityRepository.officialDailySteps(
                    state.currentLocalDate,
                    ZoneId.of(state.currentZoneId),
                ),
                state.currentLocalDate,
                now,
            ),
        )
        updateNotificationIfNeeded(now, force = previewDateChanged)
        logEvent(
            "sensor",
            sensorValue = raw.takeIf(Float::isFinite)?.toLong(),
            delta = delta,
            detail = result::class.simpleName,
        )
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit
    override fun onBind(intent: Intent?): IBinder? = null

    private suspend fun stopTracking(reason: TrackingStopReason) {
        activityRepository.clearPendingMotionAllocations()
        motionFinishJob?.cancel()
        motionMaximumJob?.cancel()
        motionCapture.reset()
        unregisterMotionSensors()
        sensorManager.unregisterListener(this)
        stepCounterRegistered = false
        stepDetectorRegistered = false
        fakeSensorMode = false
        heartbeatJob?.cancel()
        sessionTimeoutJob?.cancel()
        pendingNotificationJob?.cancel()
        previewExpiryJob?.cancel()
        synchronized(previewExpiryLock) { previewExpiryJob = null }
        val now = Instant.now()
        activityRepository.finishAllActiveSessions(now)
        state = repository.update {
            it.copy(
                trackingRequested = false,
                trackingStatus = TrackingStatus.STOPPED,
                lastServiceStoppedAt = now,
                lastStopReason = reason,
                stepCounterRegistered = false,
                stepDetectorRegistered = false,
                serviceRunning = false,
                sessionId = null,
                sensorBaseline = null,
                lastSensorValue = null,
            )
        }
        logEvent("service_stopped", detail = reason.name)
        ServiceCompat.stopForeground(this@StepTrackingService, ServiceCompat.STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    override fun onDestroy() {
        TrackingServiceProcessRegistry.serviceAlive = false
        runBlocking { activityRepository.clearPendingMotionAllocations() }
        stateUpdates.destroy()
        motionFinishJob?.cancel()
        motionMaximumJob?.cancel()
        motionCapture.reset()
        unregisterMotionSensors()
        sensorManager.unregisterListener(this)
        sensorSamples.close()
        sensorConsumerJob?.cancel()
        heartbeatJob?.cancel()
        sessionTimeoutJob?.cancel()
        pendingNotificationJob?.cancel()
        previewExpiryJob?.cancel()
        synchronized(previewExpiryLock) { previewExpiryJob = null }
        NotificationStepPreviewDiagnostics.clear()
        scope.cancel()
        super.onDestroy()
    }

    private suspend fun updateNotificationIfNeeded(now: Instant, force: Boolean = false) {
        val beforeExpiry = notificationPreview.snapshot.displayedSteps
        val preview = notificationPreview.expire(now)
        publishNotificationPreview(preview)
        val expiryReducedDisplay = preview.displayedSteps < beforeExpiry
        if (
            notificationPolicy.shouldUpdate(
                preview.displayedSteps,
                lastNotifiedSteps,
                now,
                lastNotifiedAt,
                force || expiryReducedDisplay,
            )
        ) {
            val manual = activityRepository.currentManualSession()
            val content = if (manual == null) {
                NotificationContent.Tracking(preview.displayedSteps, dailyStepGoal.toLong())
            } else {
                NotificationContent.Walking(
                    sessionSteps = manual.steps,
                    elapsedMinutes = manual.elapsedDurationSeconds / 60,
                    todaySteps = preview.displayedSteps,
                    goalSteps = dailyStepGoal.toLong(),
                )
            }
            val model = NotificationModel(
                content,
                maxOfInstant(preview.lastDetectorAt, preview.lastCounterAt) ?: state.lastSensorEventAt,
                manual,
            )
            getSystemService(NotificationManager::class.java).notify(NOTIFICATION_ID, notification(model))
            lastNotifiedSteps = preview.displayedSteps
            lastNotifiedAt = now
            scope.launch {
                serializeStateUpdate {
                    repository.update { it.copy(lastNotificationAt = now) }
                    logEvent("notification_update")
                }
            }
            pendingNotificationJob?.cancel()
            pendingNotificationJob = null
        } else if (preview.displayedSteps != lastNotifiedSteps && pendingNotificationJob == null) {
            val remaining = notificationPolicy.remainingDelayMillis(now, lastNotifiedAt)
            pendingNotificationJob = scope.launch {
                delay(remaining)
                serializeStateUpdate {
                    pendingNotificationJob = null
                    updateNotificationIfNeeded(Instant.now())
                }
            }
        }
    }

    private suspend fun <T> serializeStateUpdate(block: suspend () -> T): T? =
        stateUpdates.run(block)

    private fun publishNotificationPreview(
        snapshot: com.lazyapps.steparena.tracking.NotificationStepPreviewSnapshot,
    ) {
        NotificationStepPreviewDiagnostics.publish(snapshot)
    }

    private fun maxOfInstant(first: Instant?, second: Instant?): Instant? = when {
        first == null -> second
        second == null -> first
        first >= second -> first
        else -> second
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
            if (model.content is NotificationContent.Walking) R.string.notification_walking_title
            else R.string.notification_tracking_title,
        )
        val text = when (val content = model.content) {
            NotificationContent.Preparing -> getString(R.string.notification_status_preparing)
            is NotificationContent.Tracking -> getString(
                R.string.notification_tracking_text,
                NumberFormat.getNumberInstance().format(content.todaySteps),
                NumberFormat.getNumberInstance().format(content.goalSteps),
                updated,
            )
            is NotificationContent.Walking -> getString(
                R.string.notification_walking_text,
                NumberFormat.getNumberInstance().format(content.sessionSteps),
                content.elapsedMinutes,
                NumberFormat.getNumberInstance().format(content.todaySteps),
                NumberFormat.getNumberInstance().format(content.goalSteps),
            )
        }
        val statusLabel = getString(
            when (model.content) {
                NotificationContent.Preparing -> R.string.notification_status_preparing
                is NotificationContent.Tracking -> R.string.notification_status_tracking
                is NotificationContent.Walking -> R.string.notification_status_walking
            },
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle(title)
            .setContentText(text)
            .setSubText(statusLabel)
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
        val content: NotificationContent,
        val lastUpdated: Instant? = null,
        val manualSession: com.lazyapps.steparena.core.database.entity.WalkingSessionEntity? = null,
    )

    private sealed interface SensorSample {
        data class Counter(
            val raw: Float,
            val at: Instant,
            val completed: CompletableDeferred<Unit>? = null,
        ) : SensorSample
        data class Detector(val at: Instant) : SensorSample
    }

    companion object {
        private const val SENSOR_CHANNEL_CAPACITY = 1_024
        private const val MOTION_SAMPLE_PERIOD_MICROS = 50_000
        private const val MOTION_CAPTURE_TAIL_MILLIS = 4_000L
        private const val MOTION_CAPTURE_MAX_MILLIS = 15_000L
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

        fun debugKeepFakeModeExtra(): String =
            listOf("debug", "keep", "fake", "mode").joinToString("_")

        fun debugSequenceCountExtra(): String =
            listOf("debug", "sequence", "count").joinToString("_")

        fun debugSequenceStepsExtra(): String =
            listOf("debug", "sequence", "steps").joinToString("_")

        fun debugSequenceIntervalExtra(): String =
            listOf("debug", "sequence", "interval").joinToString("_")
    }
}

internal fun isCurrentSessionRequest(requested: String?, current: String?): Boolean =
    requested == null || requested == current

/** Serializes every service-local mutation and rejects work after lifecycle destruction. */
internal class ServiceStateUpdateGate {
    private val mutex = Mutex()
    private val destroyed = AtomicBoolean(false)

    suspend fun <T> run(block: suspend () -> T): T? = mutex.withLock {
        if (destroyed.get()) null else block()
    }

    fun destroy() {
        destroyed.set(true)
    }
}

internal fun mergeCounterStateWithPersistedDiagnostics(
    counterState: StepTrackingState,
    persisted: StepTrackingState,
): StepTrackingState = counterState.copy(
    lastHeartbeatAt = listOfNotNull(counterState.lastHeartbeatAt, persisted.lastHeartbeatAt).maxOrNull(),
    lastNotificationAt = listOfNotNull(counterState.lastNotificationAt, persisted.lastNotificationAt).maxOrNull(),
)

internal fun heartbeatState(
    state: StepTrackingState,
    heartbeat: Instant,
): StepTrackingState = state.copy(
    lastHeartbeatAt = heartbeat,
    serviceRunning = true,
    trackingStatus = if (
        state.trackingRequested && state.trackingStatus == TrackingStatus.SERVICE_HEARTBEAT_STALE
    ) TrackingStatus.TRACKING else state.trackingStatus,
)

internal fun recoverStatusOnCounterEvent(
    previous: StepTrackingState,
    result: StepEventResult,
    rawValue: Float,
): StepTrackingState {
    val resultState = result.state
    val validSample =
        rawValue.isFinite() &&
            rawValue >= 0f &&
            resultState.trackingRequested &&
            result !is StepEventResult.Reset
    val recoverable = previous.trackingStatus in setOf(
        TrackingStatus.SERVICE_HEARTBEAT_STALE,
        TrackingStatus.SENSOR_DATA_STALE,
    )
    return resultState.copy(
        trackingStatus = if (validSample && recoverable) {
            TrackingStatus.TRACKING
        } else {
            resultState.trackingStatus
        },
        serviceRunning = if (resultState.trackingRequested) true else resultState.serviceRunning,
    )
}

internal fun recoverStatusOnDetectorEvent(
    state: StepTrackingState,
    eventAt: Instant,
): StepTrackingState {
    if (!state.trackingRequested || !state.stepDetectorRegistered || !state.serviceRunning) {
        return state
    }

    return state.copy(
        trackingStatus = if (
            state.trackingStatus in setOf(
                TrackingStatus.SERVICE_HEARTBEAT_STALE,
                TrackingStatus.SENSOR_DATA_STALE,
            )
        ) TrackingStatus.TRACKING else state.trackingStatus,
        lastSensorEventAt = eventAt,
    )
}
