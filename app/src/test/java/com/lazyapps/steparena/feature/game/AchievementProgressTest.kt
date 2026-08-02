package com.lazyapps.steparena.feature.game

import com.lazyapps.steparena.core.database.entity.DailyActivityRecordEntity
import com.lazyapps.steparena.core.database.model.DataQuality
import com.lazyapps.steparena.game.shouldUnlockDailyTenThousand
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AchievementProgressTest {
    @Test fun twoHundredPercentTextCollapsesGridToOneColumn() {
        assertEquals(2, achievementColumnCount(1f))
        assertEquals(1, achievementColumnCount(2f))
    }
    @Test fun lockedAchievementUsesActualProgressInsteadOfZero() {
        val progress = achievementProgress(
            profile = null,
            matches = emptyList(),
            daily = listOf(day("2026-07-30"), day("2026-07-31")),
            unlocks = emptyList(),
        ).first { it.id == "three_day_streak" }

        assertEquals(2, progress.current)
        assertEquals(3, progress.target)
        assertFalse(progress.unlocked)
    }

    @Test fun recoveryAchievementUsesRecoveryHistoryCount() {
        val progress = achievementProgress(
            null, emptyList(), listOf(day("2026-07-31", recovered = 20)), emptyList(),
        ).first { it.id == "gap_recovery_success" }
        assertEquals(1, progress.current)
    }

    @Test fun dailyTenThousandUsesFixedThresholdRegardlessOfUserGoal() {
        assertFalse(shouldUnlockDailyTenThousand(5_000))
        assertFalse(shouldUnlockDailyTenThousand(9_999))
        assertTrue(shouldUnlockDailyTenThousand(10_000))
    }

    @Test fun monthlyChallengeCountUsesOnlyCurrentSeason() {
        val matches = (1..12).map { match("past", it) } + (1..3).map { match("current", 20 + it) }
        val progress = achievementProgress(
            null, matches, emptyList(), emptyList(), currentSeasonId = "current",
        ).first { it.id == "season_10_matches" }
        assertEquals(3, progress.current)
    }

    @Test fun unlockedProgressNeverRegressesAfterMonthOrRankChanges() {
        val unlock = com.lazyapps.steparena.core.database.entity.AchievementUnlockEntity(
            "season_10_matches", 1, 12, "past", false,
        )
        val progress = achievementProgress(
            null, emptyList(), emptyList(), listOf(unlock), currentSeasonId = "current",
        ).first { it.id == "season_10_matches" }
        assertEquals(12, progress.current)
        assertTrue(progress.unlocked)
    }

    @Test fun todaysRealtimeEligibleStepsCanUnlockBeforeFinalization() {
        val progress = achievementProgress(
            null, emptyList(), emptyList(), emptyList(), currentEligibleSteps = 10_000,
        ).first { it.id == "daily_10000_steps" }
        assertEquals(10_000, progress.current)
    }

    private fun match(seasonId: String, index: Int) = com.lazyapps.steparena.core.database.entity.DailyMatchEntity(
        id = "$seasonId-$index", localDate = "2026-07-${index.coerceAtMost(28)}", zoneId = "Asia/Tokyo",
        seasonId = seasonId, matchType = com.lazyapps.steparena.game.MatchType.DAILY,
        status = com.lazyapps.steparena.game.MatchStatus.FINALIZED,
        outcome = null, opponentId = "o", opponentName = "Aoi", opponentAvatarKey = "aoi",
        opponentRankTier = com.lazyapps.steparena.game.RankTier.BRONZE, opponentRankDivision = 3,
        opponentPersonality = com.lazyapps.steparena.game.OpponentPersonality.STEADY,
        opponentTargetSteps = 10_000, totalUserSteps = 1_000, eligibleUserSteps = 1_000,
        restrictedUserSteps = 0, excludedUserSteps = 0, restrictionReasons = "",
        competitiveQuality = com.lazyapps.steparena.game.CompetitiveStepQuality.FULL,
        ratingBefore = 1_000, ratingDelta = 0, ratingAfter = 1_000, ratingBreakdown = null,
        finalizedAtEpochMillis = 1, createdAtEpochMillis = 1, updatedAtEpochMillis = 1,
    )

    private fun day(date: String, recovered: Long = 0) = DailyActivityRecordEntity(
        id = "$date|Asia/Tokyo", localDate = date, zoneId = "Asia/Tokyo", steps = 1_000,
        unclassifiedSteps = recovered, unclassifiedStepsQuality = DataQuality.RECOVERED,
        distanceMeters = null, walkingDurationSeconds = null, estimatedCaloriesKcal = null,
        averageWalkingSpeedKmh = null, stepsQuality = DataQuality.MEASURED,
        distanceQuality = DataQuality.UNKNOWN, durationQuality = DataQuality.UNKNOWN,
        caloriesQuality = DataQuality.UNKNOWN, speedQuality = DataQuality.UNKNOWN,
        activeHourCount = 1, walkingSessionCount = 1, finalized = false,
        finalizedAtEpochMillis = null, createdAtEpochMillis = 0, updatedAtEpochMillis = 0,
    )
}
