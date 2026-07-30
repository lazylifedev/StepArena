package com.lazyapps.steparena.feature.game

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.lazyapps.steparena.app.StepArenaApplication
import com.lazyapps.steparena.core.database.entity.*
import com.lazyapps.steparena.tracking.TrackingStateRepository
import com.lazyapps.steparena.game.MatchStatus
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

data class GameUiState(
    val profile: GamePlayerProfileEntity? = null,
    val todayMatch: DailyMatchEntity? = null,
    val recentMatches: List<DailyMatchEntity> = emptyList(),
    val league: WeeklyLeagueEntity? = null,
    val season: GameSeasonEntity? = null,
    val achievements: List<AchievementUnlockEntity> = emptyList(),
    val notificationEvents: List<GameNotificationEventEntity> = emptyList(),
    val currentMeasuredSteps: Long = 0,
    val currentHealthConnectAddedSteps: Long = 0,
)

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class GameViewModel(application: Application) : AndroidViewModel(application) {
    private val app = application as StepArenaApplication
    private val repository = app.gameRepository
    private val trackingRepository = TrackingStateRepository(application)
    private val activityRepository = app.activityRepository
    private val recoverySettingsRepository = app.recoverySettingsRepository
    private val currentLocalDay = app.currentLocalDayProvider.current
    private val healthConnectAddedSteps = combine(
        recoverySettingsRepository.settings,
        currentLocalDay.flatMapLatest {
            activityRepository.observeToday(it.date, it.zoneId)
        },
    ) { settings, daily ->
        if (settings.healthConnectEnabled) daily?.unclassifiedSteps ?: 0 else 0
    }
    val state: StateFlow<GameUiState> = combine(
        repository.observePlayerProfile(),
        currentLocalDay.flatMapLatest { repository.observeMatch(it.date, it.zoneId) },
        repository.observeRecentMatches(30),
        repository.observeCurrentLeague(),
        repository.observeCurrentSeason(),
        repository.observeAchievements(),
        repository.observeNotificationEvents(),
        trackingRepository.state,
        healthConnectAddedSteps,
        currentLocalDay,
    ) { values ->
        @Suppress("UNCHECKED_CAST")
        GameUiState(
            values[0] as GamePlayerProfileEntity,
            values[1] as DailyMatchEntity?,
            values[2] as List<DailyMatchEntity>,
            values[3] as WeeklyLeagueEntity?,
            values[4] as GameSeasonEntity?,
            values[5] as List<AchievementUnlockEntity>,
            values[6] as List<GameNotificationEventEntity>,
            (values[7] as com.lazyapps.steparena.tracking.StepTrackingState).let { tracking ->
                (values[9] as com.lazyapps.steparena.core.time.LocalDay).let { day ->
                    tracking.accumulatedTodaySteps.takeIf {
                        tracking.currentLocalDate == day.date &&
                            tracking.currentZoneId == day.zoneId.id
                    } ?: 0
                }
            },
            values[8] as Long,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), GameUiState())

    init {
        viewModelScope.launch {
            repository.finalizePendingMatches()
            repository.evaluateAchievements()
        }
        viewModelScope.launch {
            currentLocalDay.collect { day ->
                repository.ensureMatch(day.date, day.zoneId)
            }
        }
    }

    fun acknowledgeEvent(id: String) {
        viewModelScope.launch { repository.acknowledgeNotificationEvent(id) }
    }
}

data class CurrentChallengeSteps(
    val displayedUserSteps: Long,
    val eligibleSteps: Long,
    val isFinalized: Boolean,
)

fun currentChallengeSteps(match: DailyMatchEntity, measuredSteps: Long): CurrentChallengeSteps =
    if (match.status == MatchStatus.FINALIZED) {
        CurrentChallengeSteps(match.totalUserSteps, match.eligibleUserSteps, true)
    } else {
        val current = measuredSteps.coerceAtLeast(0)
        CurrentChallengeSteps(current, current.coerceAtMost(30_000), false)
    }
