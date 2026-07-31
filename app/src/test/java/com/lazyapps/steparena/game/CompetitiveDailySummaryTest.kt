package com.lazyapps.steparena.game

import com.lazyapps.steparena.core.database.entity.DailyActivityRecordEntity
import com.lazyapps.steparena.core.database.model.DataQuality
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CompetitiveDailySummaryTest {
    @Test
    fun `health connect addition is not subtracted from measured steps`() {
        val summary = competitiveSummary(daily(measuredSteps = 3_619, healthConnectAddedSteps = 20))

        assertEquals(3_639, summary.totalSteps)
        assertEquals(3_619, summary.eligibleSteps)
        assertEquals(20, summary.restrictedSteps)
        assertEquals(0, summary.excludedSteps)
        assertEquals(summary.totalSteps, summary.eligibleSteps + summary.restrictedSteps + summary.excludedSteps)
        assertTrue(summary.reasons.contains(CompetitiveStepRestrictionReason.RECOVERED_LIMITED))
    }

    @Test
    fun `measured challenge value remains stable before and after date finalization`() {
        val beforeFinalization = competitiveSummary(
            daily(measuredSteps = 3_619, healthConnectAddedSteps = 20, finalized = false),
        )
        val afterFinalization = competitiveSummary(
            daily(measuredSteps = 3_619, healthConnectAddedSteps = 20, finalized = true),
        )

        assertEquals(3_619, beforeFinalization.eligibleSteps)
        assertEquals(beforeFinalization, afterFinalization)
    }

    private fun daily(
        measuredSteps: Long,
        healthConnectAddedSteps: Long,
        finalized: Boolean = false,
    ) = DailyActivityRecordEntity(
        id = "2026-07-31|Asia/Tokyo",
        localDate = "2026-07-31",
        zoneId = "Asia/Tokyo",
        steps = measuredSteps,
        unclassifiedSteps = healthConnectAddedSteps,
        unclassifiedStepsQuality = DataQuality.RECOVERED,
        distanceMeters = null,
        walkingDurationSeconds = null,
        estimatedCaloriesKcal = null,
        averageWalkingSpeedKmh = null,
        stepsQuality = DataQuality.MIXED,
        distanceQuality = DataQuality.UNKNOWN,
        durationQuality = DataQuality.UNKNOWN,
        caloriesQuality = DataQuality.UNKNOWN,
        speedQuality = DataQuality.UNKNOWN,
        activeHourCount = 0,
        walkingSessionCount = 0,
        finalized = finalized,
        finalizedAtEpochMillis = if (finalized) 1 else null,
        createdAtEpochMillis = 0,
        updatedAtEpochMillis = 0,
    )
}
