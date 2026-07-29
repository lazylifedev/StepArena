package com.lazyapps.steparena.game

import com.lazyapps.steparena.app.DebugStepArenaApplication
import com.lazyapps.steparena.app.DebugDataMode
import com.lazyapps.steparena.core.database.entity.DailyActivityRecordEntity
import com.lazyapps.steparena.core.database.model.DataQuality
import java.time.LocalDate

/**
 * Debug-only mutations. Generated opponents use a debug prefix, which makes cleanup selective.
 */
class DebugGameController(private val application: DebugStepArenaApplication) {
    private val database = application.debugDatabase
    private val repository = application.gameRepository
    private val clock get() = application.debugClock
    private val zone get() = clock.zone
    private val today get() = LocalDate.now(clock).toString()

    suspend fun run(scenario: DebugGameScenario) {
        check(application.dataMode == DebugDataMode.ISOLATED_SCENARIO) {
            "Debug scenario mutations require ISOLATED_SCENARIO mode"
        }
        when (scenario) {
            DebugGameScenario.SET_MEASURED_STEPS -> setSteps(5_000, DataQuality.MEASURED)
            DebugGameScenario.COUNTER_100 -> addSteps(100, DataQuality.MEASURED)
            DebugGameScenario.COUNTER_1000 -> addSteps(1_000, DataQuality.MEASURED)
            DebugGameScenario.COUNTER_5000 -> addSteps(5_000, DataQuality.MEASURED)
            DebugGameScenario.ADD_RECOVERED -> addSteps(1_000, DataQuality.RECOVERED)
            DebugGameScenario.ADD_HEALTH_CONNECT -> addSteps(1_000, DataQuality.MIXED)
            DebugGameScenario.ADD_ESTIMATED -> addSteps(1_000, DataQuality.ESTIMATED)
            DebugGameScenario.ADD_UNKNOWN -> addSteps(1_000, DataQuality.UNKNOWN)
            DebugGameScenario.OVER_30000 -> setSteps(40_000, DataQuality.MEASURED)
            DebugGameScenario.ABNORMAL_STEPS -> setSteps(100_001, DataQuality.UNKNOWN)
            DebugGameScenario.CREATE_MATCH -> repository.ensureTodayMatch()
            DebugGameScenario.SET_NPC_LOW -> updateMatch { it.copy(opponentId = "debug-low", opponentTargetSteps = 1_000) }
            DebugGameScenario.SET_NPC_4000 -> updateMatch { it.copy(opponentId = "debug-4000", opponentTargetSteps = 4_000) }
            DebugGameScenario.SET_NPC_HIGH -> updateMatch { it.copy(opponentId = "debug-high", opponentTargetSteps = 30_000) }
            DebugGameScenario.WIN -> updateMatch { it.copy(opponentId = "debug-win", opponentTargetSteps = 1) }
            DebugGameScenario.LOSS -> updateMatch { it.copy(opponentId = "debug-loss", opponentTargetSteps = 30_000) }
            DebugGameScenario.DRAW -> updateMatch { it.copy(opponentId = "debug-draw", opponentTargetSteps = it.eligibleUserSteps) }
            DebugGameScenario.NO_CONTEST -> updateMatch { it.copy(opponentId = "debug-no-contest", opponentTargetSteps = 0) }
            DebugGameScenario.FINALIZE, DebugGameScenario.DOUBLE_FINALIZE -> {
                val match = currentMatch() ?: return
                repository.finalizeMatch(match.id)
                if (scenario == DebugGameScenario.DOUBLE_FINALIZE) repository.finalizeMatch(match.id)
            }
            DebugGameScenario.RESET_TODAY_MATCH -> {
                database.dailyMatches().deleteForZone(zone.id)
                repository.ensureTodayMatch()
            }
            DebugGameScenario.ADD_RATING -> updateProfile { it.copy(rating = it.rating + 25) }
            DebugGameScenario.REMOVE_RATING -> updateProfile { it.copy(rating = (it.rating - 20).coerceAtLeast(1_000)) }
            DebugGameScenario.PROMOTION_READY -> updateProfile { it.copy(rating = 1_195) }
            DebugGameScenario.DEMOTION_READY -> updateProfile { it.copy(rating = 1_205) }
            DebugGameScenario.PROMOTE -> updateProfile { it.copy(rating = 1_200, rankTier = RankTier.BRONZE, rankDivision = 2) }
            DebugGameScenario.DEMOTE -> updateProfile { it.copy(rating = 1_199, rankTier = RankTier.BRONZE, rankDivision = 3) }
            DebugGameScenario.THREE_WIN_STREAK -> updateProfile { it.copy(currentWinStreak = 3, bestWinStreak = maxOf(it.bestWinStreak, 3)) }
            DebugGameScenario.FIVE_WIN_STREAK -> updateProfile { it.copy(currentWinStreak = 5, bestWinStreak = maxOf(it.bestWinStreak, 5)) }
            DebugGameScenario.THREE_LOSS_STREAK -> updateProfile { it.copy(currentLossStreak = 3) }
            DebugGameScenario.END_BEGINNER_PERIOD -> updateProfile { it.copy(beginnerMatchesRemaining = 0) }
            DebugGameScenario.LEAGUE_CREATE -> repository.rebuildCurrentLeague()
            DebugGameScenario.LEAGUE_FIRST -> setLeagueRank(1)
            DebugGameScenario.LEAGUE_FIFTH -> setLeagueRank(5)
            DebugGameScenario.LEAGUE_TENTH -> setLeagueRank(10)
            DebugGameScenario.LEAGUE_FINALIZE -> repository.finalizeExpiredLeagues()
            DebugGameScenario.SEASON_FINALIZE -> repository.finalizeExpiredSeasons()
            DebugGameScenario.NEXT_DAY -> { clock.advanceDays(1); repository.runMaintenance() }
            DebugGameScenario.NEXT_WEEK -> { clock.advanceDays(7); repository.runMaintenance() }
            DebugGameScenario.NEXT_MONTH -> { clock.advanceMonths(1); repository.runMaintenance() }
            DebugGameScenario.MONTH_END -> { clock.advanceMonths(1); repository.runMaintenance() }
            DebugGameScenario.NEXT_SEASON -> { clock.advanceMonths(1); repository.runMaintenance() }
            DebugGameScenario.CHANGE_TIME_ZONE -> { clock.changeZone(); repository.runMaintenance() }
            DebugGameScenario.CLOCK_ROLLBACK -> { clock.rollbackHours(6); repository.runMaintenance() }
            DebugGameScenario.FIRST_STEP_ACHIEVEMENT -> unlockDebugAchievement("debug-first-1000", 1_000)
            DebugGameScenario.FIRST_WIN_ACHIEVEMENT -> unlockDebugAchievement("debug-first-win", 1)
            DebugGameScenario.EVALUATE_ACHIEVEMENTS -> repository.evaluateAchievements()
            DebugGameScenario.DUPLICATE_ACHIEVEMENT -> {
                unlockDebugAchievement("debug-duplicate-check", 1)
                unlockDebugAchievement("debug-duplicate-check", 2)
            }
            DebugGameScenario.RESET_ACHIEVEMENTS -> database.achievementUnlocks().deleteDebug()
            DebugGameScenario.RERUN_WORK_MANAGER,
            DebugGameScenario.SAME_DAY_REPROCESS -> repository.runMaintenance()
            DebugGameScenario.RESET_DEBUG_DATA -> {
                RoomDebugScenarioResetter(application).resetAllScenarioData()
            }
        }
    }

    private suspend fun setSteps(steps: Long, quality: DataQuality) {
        val now = clock.millis()
        val existing = database.daily().get(today, zone.id)
        database.daily().upsert(
            (existing ?: emptyDaily(now)).copy(
                steps = steps,
                unclassifiedSteps = if (quality == DataQuality.MEASURED) 0 else steps,
                unclassifiedStepsQuality = quality,
                stepsQuality = quality,
                updatedAtEpochMillis = now,
            ),
        )
        repository.ensureTodayMatch()
    }

    private suspend fun addSteps(steps: Long, quality: DataQuality) {
        val existing = database.daily().get(today, zone.id)
        val combinedQuality = when {
            existing == null || existing.steps == 0L -> quality
            existing.stepsQuality == quality -> quality
            else -> DataQuality.MIXED
        }
        setSteps((existing?.steps ?: 0) + steps, combinedQuality)
    }

    private fun emptyDaily(now: Long) = DailyActivityRecordEntity(
        id = "debug-daily-$today-${zone.id.hashCode()}",
        localDate = today,
        zoneId = zone.id,
        steps = 0,
        unclassifiedSteps = 0,
        unclassifiedStepsQuality = DataQuality.UNKNOWN,
        distanceMeters = null,
        walkingDurationSeconds = null,
        estimatedCaloriesKcal = null,
        averageWalkingSpeedKmh = null,
        stepsQuality = DataQuality.UNKNOWN,
        distanceQuality = DataQuality.UNKNOWN,
        durationQuality = DataQuality.UNKNOWN,
        caloriesQuality = DataQuality.UNKNOWN,
        speedQuality = DataQuality.UNKNOWN,
        activeHourCount = 0,
        walkingSessionCount = 0,
        finalized = false,
        finalizedAtEpochMillis = null,
        createdAtEpochMillis = now,
        updatedAtEpochMillis = now,
    )

    private suspend fun setLeagueRank(rank: Int) {
        repository.rebuildCurrentLeague()
        val start = LocalDate.now().with(
            java.time.temporal.TemporalAdjusters.previousOrSame(java.time.DayOfWeek.MONDAY),
        )
        val id = "league-$start-${zone.id.hashCode()}"
        database.weeklyLeagues().get(id)?.let {
            database.weeklyLeagues().upsert(
                it.copy(userRank = rank, updatedAtEpochMillis = System.currentTimeMillis()),
            )
        }
    }

    private suspend fun unlockDebugAchievement(id: String, progress: Long) {
        database.achievementUnlocks().insert(
            com.lazyapps.steparena.core.database.entity.AchievementUnlockEntity(
                id, System.currentTimeMillis(), progress, null, false,
            ),
        )
    }

    private suspend fun currentMatch() =
        database.dailyMatches().getForDate(today, zone.id)

    private suspend fun updateMatch(transform: (com.lazyapps.steparena.core.database.entity.DailyMatchEntity) ->
        com.lazyapps.steparena.core.database.entity.DailyMatchEntity) {
        repository.ensureTodayMatch()
        currentMatch()?.let { database.dailyMatches().update(transform(it)) }
    }

    private suspend fun updateProfile(
        transform: (com.lazyapps.steparena.core.database.entity.GamePlayerProfileEntity) ->
            com.lazyapps.steparena.core.database.entity.GamePlayerProfileEntity,
    ) {
        val profile = database.gamePlayerProfile().get() ?: return
        val changed = transform(profile)
        val rank = RankSystem.definition(changed.rating)
        database.gamePlayerProfile().upsert(
            changed.copy(rankTier = rank.tier, rankDivision = rank.division, updatedAtEpochMillis = System.currentTimeMillis()),
        )
    }
}
