package com.lazyapps.steparena.feature.home

import android.app.Application
import android.content.Intent
import androidx.core.content.ContextCompat
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.lazyapps.steparena.core.designsystem.motion.InMemoryMotionSettingsRepository
import com.lazyapps.steparena.core.designsystem.motion.MotionSettingsRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import com.lazyapps.steparena.service.tracking.StepTrackingService
import com.lazyapps.steparena.tracking.TrackingStateRepository
import com.lazyapps.steparena.tracking.TrackingStatus as PersistentTrackingStatus
import com.lazyapps.steparena.core.model.*
import java.time.Instant
import com.lazyapps.steparena.app.StepArenaApplication
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.combine
import com.lazyapps.steparena.core.database.model.DataQuality

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class HomeViewModel(
    application: Application,
    private val motionRepository: MotionSettingsRepository,
) : AndroidViewModel(application) {
    constructor(application: Application) : this(application, InMemoryMotionSettingsRepository())
    private val trackingRepository = TrackingStateRepository(application)
    private val activityRepository = (application as StepArenaApplication).activityRepository
    private val _uiState = MutableStateFlow(HomeUiState())
    val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()

    init {
        observeTracking()
    }

    fun onAction(action: HomeAction) {
        when (action) {
            HomeAction.StartSession -> startTracking()
            HomeAction.StartManualWalk -> startManualWalk()
            is HomeAction.EndManualWalk -> endManualWalk(action.sessionId)
            HomeAction.StopTracking -> stopTracking()
            HomeAction.OpenDiagnostics -> Unit
            HomeAction.Retry -> load()
            is HomeAction.SetMotion -> viewModelScope.launch {
                motionRepository.save(action.level)
                _uiState.update { it.copy(motionLevel = action.level) }
            }
        }
    }

    private fun observeTracking() {
        viewModelScope.launch {
            combine(
                trackingRepository.state,
                activityRepository.observeActiveManualSession(),
            ) { tracking, manual -> tracking to manual }.flatMapLatest { (tracking, manual) ->
                activityRepository.observeToday(
                    tracking.currentLocalDate,
                    java.time.ZoneId.of(tracking.currentZoneId),
                ).map { daily ->
                    Triple(tracking, manual, daily)
                }
            }.collect { (tracking, manual, daily) ->
                val status = when (tracking.trackingStatus) {
                    PersistentTrackingStatus.TRACKING, PersistentTrackingStatus.RESTARTED ->
                        TrackingStatus.ACTIVE
                    PersistentTrackingStatus.PERMISSION_REQUIRED ->
                        TrackingStatus.PERMISSION_REQUIRED
                    PersistentTrackingStatus.BATTERY_RESTRICTED ->
                        TrackingStatus.BATTERY_SETTING_REQUIRED
                    PersistentTrackingStatus.SERVICE_HEARTBEAT_STALE,
                    PersistentTrackingStatus.SENSOR_DATA_STALE -> TrackingStatus.MAY_BE_STOPPED
                    else -> TrackingStatus.NOT_STARTED
                }
                _uiState.update {
                    it.copy(
                        content = HomeContent.Ready(
                            realSnapshot(
                                steps = daily?.steps ?: tracking.accumulatedTodaySteps,
                                status = status,
                                lastHealthyAt = tracking.lastSensorEventAt,
                                distanceMeters = daily?.distanceMeters,
                                durationSeconds = daily?.walkingDurationSeconds,
                                calories = daily?.estimatedCaloriesKcal,
                                speedKmh = daily?.averageWalkingSpeedKmh,
                                reliability = when (daily?.stepsQuality) {
                                    DataQuality.RECOVERED, DataQuality.MIXED ->
                                        DataReliability.PARTLY_RECOVERED
                                    DataQuality.ESTIMATED -> DataReliability.PARTLY_ESTIMATED
                                    DataQuality.MEASURED -> DataReliability.COMPLETE
                                    else -> DataReliability.NO_DATA
                                },
                            ),
                        ),
                        motionLevel = motionRepository.read(),
                        sessionState = when {
                            manual != null -> SessionState.MANUAL_WALK
                            tracking.trackingRequested -> SessionState.TRACKING
                            else -> SessionState.TRACKING_STOPPED
                        },
                        manualSession = manual?.let {
                            ManualSessionUi(
                                id = it.id,
                                startedAtEpochMillis = it.startedAtEpochMillis,
                                steps = it.steps,
                                distanceMeters = it.distanceMeters ?: 0.0,
                                elapsedSeconds = it.elapsedDurationSeconds,
                            )
                        },
                        trackingUiStatus = tracking.trackingStatus,
                        sensorSupported = tracking.trackingStatus != PersistentTrackingStatus.SENSOR_UNSUPPORTED,
                    )
                }
            }
        }
    }

    fun startTracking() {
        viewModelScope.launch {
            trackingRepository.update {
                it.copy(
                    trackingRequested = true,
                    trackingStatus = PersistentTrackingStatus.STARTING,
                    sensorBaseline = null,
                    lastSensorValue = null,
                    sessionId = null,
                    lastStopReason = null,
                )
            }
            ContextCompat.startForegroundService(
                getApplication(),
                Intent(getApplication(), StepTrackingService::class.java)
                    .setAction(StepTrackingService.ACTION_START),
            )
        }
    }

    fun stopTracking() {
        getApplication<Application>().startService(
            Intent(getApplication(), StepTrackingService::class.java)
                .setAction(StepTrackingService.ACTION_STOP),
        )
    }

    fun startManualWalk() {
        val tracking = _uiState.value.sessionState != SessionState.TRACKING_STOPPED
        viewModelScope.launch {
            if (!tracking) {
                trackingRepository.update {
                    it.copy(
                        trackingRequested = true,
                        trackingStatus = PersistentTrackingStatus.STARTING,
                        sensorBaseline = null,
                        lastSensorValue = null,
                        sessionId = null,
                        lastStopReason = null,
                    )
                }
            }
            ContextCompat.startForegroundService(
                getApplication(),
                Intent(getApplication(), StepTrackingService::class.java)
                    .setAction(StepTrackingService.ACTION_START_MANUAL_WALK),
            )
        }
    }

    fun endManualWalk(sessionId: String) {
        getApplication<Application>().startService(
            Intent(getApplication(), StepTrackingService::class.java)
                .setAction(StepTrackingService.ACTION_END_MANUAL_WALK)
                .putExtra(StepTrackingService.EXTRA_MANUAL_SESSION_ID, sessionId),
        )
    }

    private fun load() = observeTracking()

    private fun realSnapshot(
        steps: Long,
        status: TrackingStatus,
        lastHealthyAt: Instant?,
        distanceMeters: Double?,
        durationSeconds: Long?,
        calories: Double?,
        speedKmh: Double?,
        reliability: DataReliability,
    ) = HomeSnapshot(
        rank = RankStatus(RankTier.BRONZE, 1, 0, 0),
        metrics = ActivityMetrics(
            steps.coerceAtMost(Int.MAX_VALUE.toLong()).toInt(),
            10_000,
            distanceMeters ?: 0.0,
            durationSeconds ?: 0,
            calories ?: 0.0,
            (speedKmh ?: 0.0) / 3.6,
        ),
        trackingStatus = status,
        lastHealthyAt = lastHealthyAt,
        match = DailyMatch("準備中", 0f, 0f, 0, MatchOutcome.IN_PROGRESS),
        winStreak = 0,
        league = LeagueStatus(0, 0, 0),
        reliability = reliability,
        isOffline = false,
        metricsAvailable = reliability != DataReliability.NO_DATA,
    )
}
