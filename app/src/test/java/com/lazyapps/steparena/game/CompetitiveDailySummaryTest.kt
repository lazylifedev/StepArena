package com.lazyapps.steparena.game

import com.lazyapps.steparena.core.database.entity.DailyActivityRecordEntity
import com.lazyapps.steparena.core.database.entity.CompetitiveIntegritySegmentEntity
import com.lazyapps.steparena.game.CompetitiveIntegrityAssessment
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

    @Test
    fun `unclassified measured steps and integrity eligible steps share one summary`() {
        val daily = daily(measuredSteps = 5_100, healthConnectAddedSteps = 0)
            .copy(stepsQuality = DataQuality.MEASURED)
        val segment = CompetitiveIntegritySegmentEntity(
            id = "segment", localDate = daily.localDate, zoneId = daily.zoneId,
            startedAtEpochMillis = 0, endedAtEpochMillis = 1,
            totalSteps = 100, eligibleSteps = 80, restrictedSteps = 20, excludedSteps = 0,
            assessment = CompetitiveIntegrityAssessment.LIMITED,
            reasons = "", classifierVersion = 1, createdAtEpochMillis = 1,
        )

        val today = competitiveSummary(daily.copy(finalized = false), listOf(segment))
        val finalized = competitiveSummary(daily.copy(finalized = true), listOf(segment))

        assertEquals(5_080, today.eligibleSteps)
        assertEquals(today, finalized)
    }

    @Test
    fun `duplicate and legacy segments are bounded by daily steps`() {
        val daily = daily(measuredSteps = 100, healthConnectAddedSteps = 0)
            .copy(stepsQuality = DataQuality.MEASURED)
        val duplicate = CompetitiveIntegritySegmentEntity(
            id = "duplicate", localDate = daily.localDate, zoneId = daily.zoneId,
            startedAtEpochMillis = 0, endedAtEpochMillis = 1,
            totalSteps = 120, eligibleSteps = 120, restrictedSteps = 0, excludedSteps = 0,
            assessment = CompetitiveIntegrityAssessment.TRUSTED,
            reasons = "", classifierVersion = 1, createdAtEpochMillis = 1,
        )

        val summary = competitiveSummary(daily, listOf(duplicate, duplicate))

        assertTrue(summary.totalSteps <= 100)
        assertTrue(summary.eligibleSteps <= 100)
        assertTrue(summary.eligibleSteps >= 0)
        assertTrue(summary.eligibleSteps + summary.restrictedSteps + summary.excludedSteps <= 100)
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
