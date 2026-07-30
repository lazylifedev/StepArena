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
    private val gameRepository = (application as StepArenaApplication).gameRepository
    private val recoverySettingsRepository =
        (application as StepArenaApplication).recoverySettingsRepository
    private val appClock = (application as StepArenaApplication).clock
    private val isolatedScenario = (application as StepArenaApplication).isolatedScenario
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
            val trackingAndRecovery = combine(
                trackingRepository.state,
                recoverySettingsRepository.settings,
            ) { tracking, recovery -> tracking to recovery }
            combine(
                trackingAndRecovery,
                activityRepository.observeActiveManualSession(),
                gameRepository.observePlayerProfile(),
                gameRepository.observeTodayMatch(),
                gameRepository.observeCurrentLeague(),
            ) { trackingRecovery, manual, profile, match, league ->
                HomeSources(
                    trackingRecovery.first,
                    manual,
                    profile,
                    match,
                    league,
                    trackingRecovery.second.healthConnectEnabled,
                )
            }.flatMapLatest { sources ->
                activityRepository.observeToday(
                    java.time.LocalDate.now(appClock),
                    appClock.zone,
                ).map { daily ->
                    sources to daily
                }
            }.collect { (sources, daily) ->
                val tracking = sources.tracking
                val manual = sources.manual
                val status = when (tracking.trackingStatus) {
                    PersistentTrackingStatus.TRACKING, PersistentTrackingStatus.RESTARTED ->
                        TrackingStatus.ACTIVE
                    PersistentTrackingStatus.PERMISSION_REQUIRED ->
                        TrackingStatus.PERMISSION_REQUIRED
                    PersistentTrackingStatus.BATTERY_RESTRICTED ->
                        TrackingStatus.BATTERY_SETTING_REQUIRED
                    PersistentTrackingStatus.SERVICE_HEARTBEAT_STALE,
                    PersistentTrackingStatus.SENSOR_DATA_STALE,
                    PersistentTrackingStatus.ERROR -> TrackingStatus.MAY_BE_STOPPED
                    else -> TrackingStatus.NOT_STARTED
                }
                _uiState.update {
                    it.copy(
                        content = HomeContent.Ready(
                            realSnapshot(
                                steps = tracking.accumulatedTodaySteps,
                                status = status,
                                lastHealthyAt = tracking.lastSensorEventAt,
                                distanceMeters = daily?.distanceMeters,
                                durationSeconds = daily?.walkingDurationSeconds,
                                calories = daily?.estimatedCaloriesKcal,
                                speedKmh = daily?.averageWalkingSpeedKmh,
                                reliability = when {
                                    !sources.recoveryEnabled &&
                                        tracking.accumulatedTodaySteps > 0 -> DataReliability.COMPLETE
                                    else -> when (daily?.stepsQuality) {
                                    DataQuality.RECOVERED, DataQuality.MIXED ->
                                        DataReliability.PARTLY_RECOVERED
                                    DataQuality.ESTIMATED -> DataReliability.PARTLY_ESTIMATED
                                    DataQuality.MEASURED -> DataReliability.COMPLETE
                                    else -> DataReliability.NO_DATA
                                    }
                                },
                                profile = sources.profile,
                                gameMatch = sources.match,
                                gameLeague = sources.league,
                                recoveredSteps = if (sources.recoveryEnabled) {
                                    daily?.unclassifiedSteps ?: 0
                                } else 0,
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
        if (isolatedScenario) return
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
        if (isolatedScenario) return
        getApplication<Application>().startService(
            Intent(getApplication(), StepTrackingService::class.java)
                .setAction(StepTrackingService.ACTION_STOP),
        )
    }

    fun startManualWalk() {
        if (isolatedScenario) return
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
        if (isolatedScenario) return
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
        profile: com.lazyapps.steparena.core.database.entity.GamePlayerProfileEntity,
        gameMatch: com.lazyapps.steparena.core.database.entity.DailyMatchEntity?,
        gameLeague: com.lazyapps.steparena.core.database.entity.WeeklyLeagueEntity?,
        recoveredSteps: Long,
    ) = HomeSnapshot(
        rank = RankStatus(
            when (profile.rankTier) {
                com.lazyapps.steparena.game.RankTier.BRONZE -> RankTier.BRONZE
                com.lazyapps.steparena.game.RankTier.SILVER -> RankTier.SILVER
                com.lazyapps.steparena.game.RankTier.GOLD -> RankTier.GOLD
                com.lazyapps.steparena.game.RankTier.PLATINUM -> RankTier.PLATINUM
                else -> RankTier.DIAMOND
            },
            profile.rankDivision ?: 1,
            profile.rating,
            com.lazyapps.steparena.game.RankSystem.definitions
                .getOrNull(com.lazyapps.steparena.game.RankSystem.definitions.indexOf(
                    com.lazyapps.steparena.game.RankSystem.definition(profile.rating),
                ) + 1)?.minimumRating?.minus(profile.rating)?.coerceAtLeast(0) ?: 0,
        ),
        metrics = ActivityMetrics(
            (steps + recoveredSteps).coerceAtMost(Int.MAX_VALUE.toLong()).toInt(),
            10_000,
            distanceMeters ?: 0.0,
            durationSeconds ?: 0,
            calories ?: 0.0,
            (speedKmh ?: 0.0) / 3.6,
        ),
        trackingStatus = status,
        lastHealthyAt = lastHealthyAt,
        match = DailyMatch(
            gameMatch?.opponentName.orEmpty(),
            gameMatch?.let { steps.toFloat().div(it.opponentTargetSteps.coerceAtLeast(1)).coerceIn(0f, 1f) } ?: 0f,
            gameMatch?.let {
                val now = java.time.ZonedDateTime.now()
                com.lazyapps.steparena.game.LocalOpponentGenerator().progress(
                    com.lazyapps.steparena.game.LocalOpponent(
                        it.opponentId, it.opponentName, it.opponentAvatarKey, it.opponentRankTier,
                        it.opponentRankDivision, it.opponentTargetSteps, it.opponentPersonality,
                    ), now.hour * 60 + now.minute,
                ).toFloat().div(it.opponentTargetSteps.coerceAtLeast(1))
            } ?: 0f,
            gameMatch?.let { (it.opponentTargetSteps - steps + 1).coerceAtLeast(0).coerceAtMost(Int.MAX_VALUE.toLong()).toInt() } ?: 0,
            MatchOutcome.IN_PROGRESS,
        ),
        winStreak = profile.currentWinStreak,
        league = LeagueStatus(gameLeague?.userRank ?: 0, 10, 0),
        reliability = reliability,
        isOffline = false,
        metricsAvailable = reliability != DataReliability.NO_DATA,
        recoveredSteps = recoveredSteps,
        measuredSteps = steps,
    )

    private data class HomeSources(
        val tracking: com.lazyapps.steparena.tracking.StepTrackingState,
        val manual: com.lazyapps.steparena.core.database.entity.WalkingSessionEntity?,
        val profile: com.lazyapps.steparena.core.database.entity.GamePlayerProfileEntity,
        val match: com.lazyapps.steparena.core.database.entity.DailyMatchEntity?,
        val league: com.lazyapps.steparena.core.database.entity.WeeklyLeagueEntity?,
        val recoveryEnabled: Boolean,
    )
}
