package com.lazyapps.steparena.feature.game

import com.lazyapps.steparena.core.database.entity.DailyActivityRecordEntity
import com.lazyapps.steparena.core.database.model.DataQuality
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
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
