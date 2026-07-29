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

class HomeViewModel(
    application: Application,
    private val motionRepository: MotionSettingsRepository = InMemoryMotionSettingsRepository(),
) : AndroidViewModel(application) {
    private val trackingRepository = TrackingStateRepository(application)
    private val _uiState = MutableStateFlow(HomeUiState())
    val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()

    init {
        observeTracking()
    }

    fun onAction(action: HomeAction) {
        when (action) {
            HomeAction.StartSession -> startTracking()
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
            trackingRepository.state.collect { tracking ->
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
                        content = HomeContent.Ready(realSnapshot(tracking.accumulatedTodaySteps, status, tracking.lastSensorEventAt)),
                        motionLevel = motionRepository.read(),
                        sessionState = if (tracking.trackingRequested) SessionState.STARTED else SessionState.IDLE,
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
                it.copy(trackingRequested = true, trackingStatus = PersistentTrackingStatus.STARTING)
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

    private fun load() = observeTracking()

    private fun realSnapshot(steps: Long, status: TrackingStatus, lastHealthyAt: Instant?) = HomeSnapshot(
        rank = RankStatus(RankTier.BRONZE, 1, 0, 0),
        metrics = ActivityMetrics(steps.coerceAtMost(Int.MAX_VALUE.toLong()).toInt(), 10_000, 0.0, 0, 0.0, 0.0),
        trackingStatus = status,
        lastHealthyAt = lastHealthyAt,
        match = DailyMatch("準備中", 0f, 0f, 0, MatchOutcome.IN_PROGRESS),
        winStreak = 0,
        league = LeagueStatus(0, 0, 0),
        reliability = DataReliability.COMPLETE,
        isOffline = false,
    )
}
