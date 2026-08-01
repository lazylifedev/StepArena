package com.lazyapps.steparena.game

import com.lazyapps.steparena.core.database.entity.DailyActivityRecordEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CompetitiveAllocationTest {
    @Test fun `two dimensional allocation preserves both margins`() {
        val result = allocateCompetitiveDays(listOf(1, 2), 1, 1, 1)
        assertEquals(listOf(1L, 2L), result.map { it.total })
        assertEquals(1L, result.sumOf { it.eligible })
        assertEquals(1L, result.sumOf { it.restricted })
        assertEquals(1L, result.sumOf { it.excluded })
        result.forEach { assertEquals(it.total, it.eligible + it.restricted + it.excluded) }
    }

    @Test fun `fifty fifty capacity preserves 80 20 classification`() {
        val result = allocateCompetitiveDays(listOf(50, 50), 80, 20, 0)
        assertEquals(listOf(40L, 40L), result.map { it.eligible })
        assertEquals(listOf(10L, 10L), result.map { it.restricted })
    }

    @Test fun `largest remainder handles long boundary without overflow`() {
        val result = largestRemainder(Long.MAX_VALUE, listOf(Long.MAX_VALUE, Long.MAX_VALUE))
        assertTrue(result.all { it >= 0 })
        assertEquals(Long.MAX_VALUE, result.sum())
    }

    @Test fun `summary assigns all zero classified weights to excluded`() {
        val daily = DailyActivityRecordEntity(
            id = "day", localDate = "2026-08-01", zoneId = "Asia/Tokyo", steps = 100,
            unclassifiedSteps = 0, unclassifiedStepsQuality = com.lazyapps.steparena.core.database.model.DataQuality.UNKNOWN,
            distanceMeters = null, walkingDurationSeconds = null, estimatedCaloriesKcal = null,
            averageWalkingSpeedKmh = null, stepsQuality = com.lazyapps.steparena.core.database.model.DataQuality.MEASURED,
            distanceQuality = com.lazyapps.steparena.core.database.model.DataQuality.UNKNOWN,
            durationQuality = com.lazyapps.steparena.core.database.model.DataQuality.UNKNOWN,
            caloriesQuality = com.lazyapps.steparena.core.database.model.DataQuality.UNKNOWN,
            speedQuality = com.lazyapps.steparena.core.database.model.DataQuality.UNKNOWN,
            activeHourCount = 0, walkingSessionCount = 0, finalized = false, finalizedAtEpochMillis = null,
            createdAtEpochMillis = 0, updatedAtEpochMillis = 0,
        )
        val segment = com.lazyapps.steparena.core.database.entity.CompetitiveIntegritySegmentEntity(
            id = "segment", localDate = daily.localDate, zoneId = daily.zoneId,
            startedAtEpochMillis = 0, endedAtEpochMillis = 1, totalSteps = 100,
            eligibleSteps = 0, restrictedSteps = 0, excludedSteps = 0,
            assessment = CompetitiveIntegrityAssessment.REVIEW, reasons = "", classifierVersion = 1,
            createdAtEpochMillis = 1,
        )
        val summary = competitiveSummary(daily, listOf(segment))
        assertEquals(100L, summary.totalSteps)
        assertEquals(100L, summary.excludedSteps)
        assertEquals(0L, summary.eligibleSteps)
        assertEquals(0L, summary.restrictedSteps)
    }
}
