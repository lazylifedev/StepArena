package com.lazyapps.steparena.feature.game

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.lazyapps.steparena.app.StepArenaApplication
import com.lazyapps.steparena.core.database.entity.*
import com.lazyapps.steparena.tracking.TrackingStateRepository
import com.lazyapps.steparena.game.MatchStatus
import com.lazyapps.steparena.game.competitiveSummary
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

data class GameUiState(
    val profile: GamePlayerProfileEntity? = null,
    val todayMatch: DailyMatchEntity? = null,
    val recentMatches: List<DailyMatchEntity> = emptyList(),
    val league: WeeklyLeagueEntity? = null,
    val leagueParticipants: List<WeeklyLeagueParticipantEntity> = emptyList(),
    val season: GameSeasonEntity? = null,
    val achievements: List<AchievementUnlockEntity> = emptyList(),
    val notificationEvents: List<GameNotificationEventEntity> = emptyList(),
    val currentMeasuredSteps: Long = 0,
    val currentEligibleSteps: Long = currentMeasuredSteps.coerceAtMost(30_000),
    val currentHealthConnectAddedSteps: Long = 0,
    val challengeCelebration: ChallengeCelebration? = null,
    val recentDailyActivity: List<DailyActivityRecordEntity> = emptyList(),
)

internal fun GameUiState.challengeUiState() = ChallengeUiState(
    todayMatch = todayMatch,
    recentMatches = recentMatches,
    currentMeasuredSteps = currentMeasuredSteps,
    currentEligibleSteps = currentEligibleSteps,
    currentHealthConnectAddedSteps = currentHealthConnectAddedSteps,
    challengeCelebration = challengeCelebration,
    displayName = profile?.displayName,
)

internal fun GameUiState.rankUiState() = RankUiState(profile, profile?.displayName)

internal fun GameUiState.weeklyGroupUiState() = WeeklyGroupUiState(league, leagueParticipants)

internal fun GameUiState.monthlyRecordUiState() = MonthlyRecordUiState(
    season = season,
    currentRating = profile?.rating ?: 0,
    daily = recentDailyActivity,
)

internal fun GameUiState.achievementUiState() = AchievementUiState(
    achievementProgress(
        profile, recentMatches, recentDailyActivity, achievements,
        currentSeasonId = season?.id,
        currentEligibleSteps = currentEligibleSteps,
    ),
)

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class GameViewModel(application: Application) : AndroidViewModel(application) {
    private val app = application as StepArenaApplication
    private val repository = app.gameRepository
    private val trackingRepository = TrackingStateRepository(application)
    private val activityRepository = app.activityRepository
    private val recoverySettingsRepository = app.recoverySettingsRepository
    private val challengeCelebrationRepository = ChallengeCelebrationRepository(application)
    private val _challengeCelebration = MutableStateFlow<ChallengeCelebration?>(null)
    private val celebrationClaimsInFlight = mutableSetOf<String>()
    private val celebrationClaimsCompleted = mutableSetOf<String>()
    private val currentLocalDay = app.currentLocalDayProvider.current
    private val healthConnectAddedSteps = combine(
        recoverySettingsRepository.settings,
        currentLocalDay.flatMapLatest {
            activityRepository.observeToday(it.date, it.zoneId)
        },
    ) { settings, daily ->
        TodayActivity(
            daily = daily,
            measuredSteps = daily?.steps ?: 0,
            externalSteps = if (settings.healthConnectEnabled) daily?.externalRecoveredSteps ?: 0 else 0,
        )
    }
    private val todayIntegrity = currentLocalDay.flatMapLatest {
        app.database.competitiveIntegritySegments().observeDate(it.date.toString(), it.zoneId.id)
    }
    private val baseState = combine(
        repository.observePlayerProfile(),
        currentLocalDay.flatMapLatest { repository.observeMatch(it.date, it.zoneId) },
        repository.observeRecentMatches(30),
        repository.observeCurrentLeague(),
        repository.observeCurrentLeagueParticipants(),
        repository.observeCurrentSeason(),
        repository.observeAchievements(),
        repository.observeNotificationEvents(),
        trackingRepository.state,
        healthConnectAddedSteps,
        todayIntegrity,
        currentLocalDay,
        app.database.daily().recent(40),
    ) { values ->
        @Suppress("UNCHECKED_CAST")
        GameUiState(
            profile = values[0] as GamePlayerProfileEntity,
            todayMatch = values[1] as DailyMatchEntity?,
            recentMatches = values[2] as List<DailyMatchEntity>,
            league = values[3] as WeeklyLeagueEntity?,
            leagueParticipants = values[4] as List<WeeklyLeagueParticipantEntity>,
            season = values[5] as GameSeasonEntity?,
            achievements = values[6] as List<AchievementUnlockEntity>,
            notificationEvents = values[7] as List<GameNotificationEventEntity>,
            currentMeasuredSteps = (values[9] as TodayActivity).measuredSteps,
            currentEligibleSteps = competitiveSummary(
                (values[9] as TodayActivity).daily,
                values[10] as List<CompetitiveIntegritySegmentEntity>,
            ).eligibleSteps,
            currentHealthConnectAddedSteps = (values[9] as TodayActivity).externalSteps,
            recentDailyActivity = values[12] as List<DailyActivityRecordEntity>,
        )
    }
    val state: StateFlow<GameUiState> = combine(
        baseState,
        _challengeCelebration,
    ) { state, celebration ->
        state.copy(challengeCelebration = celebration)
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
        viewModelScope.launch {
            healthConnectAddedSteps.map { it.measuredSteps }.distinctUntilChanged().collect {
                repository.evaluateAchievements()
            }
        }
    }

    fun acknowledgeEvent(id: String) {
        viewModelScope.launch { repository.acknowledgeNotificationEvent(id) }
    }

    fun acknowledgeAchievement(id: String) {
        viewModelScope.launch { app.database.achievementUnlocks().acknowledge(id) }
    }

    fun observeChallengeMilestone(
        matchId: String,
        eligibleSteps: Long,
        partnerTargetSteps: Long,
    ) {
        if (
            eligibleSteps < partnerTargetSteps ||
            matchId in celebrationClaimsCompleted ||
            !celebrationClaimsInFlight.add(matchId)
        ) return
        viewModelScope.launch {
            try {
                if (challengeCelebrationRepository.claim(
                        matchId,
                        eligibleSteps,
                        partnerTargetSteps,
                    )
                ) {
                    _challengeCelebration.value = ChallengeCelebration(matchId)
                }
            } finally {
                celebrationClaimsInFlight.remove(matchId)
                celebrationClaimsCompleted.add(matchId)
            }
        }
    }

    fun acknowledgeChallengeCelebration(matchId: String) {
        if (_challengeCelebration.value?.matchId == matchId) {
            _challengeCelebration.value = null
        }
    }
}

data class CurrentChallengeSteps(
    val displayedUserSteps: Long,
    val eligibleSteps: Long,
    val isFinalized: Boolean,
)

fun currentChallengeSteps(
    match: DailyMatchEntity,
    measuredSteps: Long,
    eligibleSteps: Long = measuredSteps.coerceAtMost(30_000),
): CurrentChallengeSteps =
    if (match.status == MatchStatus.FINALIZED) {
        CurrentChallengeSteps(match.totalUserSteps, match.eligibleUserSteps, true)
    } else {
        val current = measuredSteps.coerceAtLeast(0)
        CurrentChallengeSteps(current, eligibleSteps.coerceIn(0, minOf(current, 30_000)), false)
    }

private data class TodayActivity(
    val daily: DailyActivityRecordEntity?,
    val measuredSteps: Long,
    val externalSteps: Long,
)
