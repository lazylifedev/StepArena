package com.lazyapps.steparena.feature.records

import com.lazyapps.steparena.core.database.entity.DailyActivityRecordEntity
import com.lazyapps.steparena.core.database.entity.HourlyActivityRecordEntity
import com.lazyapps.steparena.core.database.model.DataQuality
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class RecordSummaryTest {
    @Test fun `daily entity is authoritative for every metric`() {
        val daily = daily()
        val hours = listOf(hour(start = 2, steps = 999, distance = 999.0, duration = 999))
        assertEquals(5000.0, summarizeToday(RecordMetric.STEPS, daily, hours).value!!, 0.0)
        assertEquals(3420.0, summarizeToday(RecordMetric.DISTANCE, daily, hours).value!!, 0.0)
        assertEquals(3120.0, summarizeToday(RecordMetric.DURATION, daily, hours).value!!, 0.0)
        assertEquals(180.0, summarizeToday(RecordMetric.CALORIES, daily, hours).value!!, 0.0)
        assertEquals(4.8, summarizeToday(RecordMetric.SPEED, daily, hours).value!!, 0.0)
        assertEquals(
            RecordSummarySource.DAILY,
            summarizeToday(RecordMetric.STEPS, daily, hours).source,
        )
    }

    @Test fun `hourly fallback sums additive metrics`() {
        val hours = listOf(
            hour(start = 2, steps = 1000, distance = 800.0, duration = 600, calories = 40.0),
            hour(start = 3, steps = 1500, distance = 1200.0, duration = 900, calories = 60.0),
        )
        assertEquals(2500.0, summarizeToday(RecordMetric.STEPS, null, hours).value!!, 0.0)
        assertEquals(2000.0, summarizeToday(RecordMetric.DISTANCE, null, hours).value!!, 0.0)
        assertEquals(1500.0, summarizeToday(RecordMetric.DURATION, null, hours).value!!, 0.0)
        assertEquals(100.0, summarizeToday(RecordMetric.CALORIES, null, hours).value!!, 0.0)
    }

    @Test fun `speed fallback is weighted by distance and duration and never summed`() {
        val hours = listOf(
            hour(start = 2, distance = 1000.0, duration = 900, speed = 4.0),
            hour(start = 3, distance = 3000.0, duration = 1800, speed = 6.0),
        )
        assertEquals(5.333, summarizeToday(RecordMetric.SPEED, null, hours).value!!, 0.001)
    }

    @Test fun `speed uses simple average only when weighting inputs are absent`() {
        val hours = listOf(
            hour(start = 2, distance = null, duration = null, speed = 4.0),
            hour(start = 3, distance = null, duration = null, speed = 6.0),
        )
        assertEquals(5.0, summarizeToday(RecordMetric.SPEED, null, hours).value!!, 0.0)
    }

    @Test fun `null records remain unavailable and unknown`() {
        val summary = summarizeToday(RecordMetric.DISTANCE, null, listOf(hour(start = 2)))
        assertNull(summary.value)
        assertEquals(DataQuality.UNKNOWN, summary.quality)
    }

    @Test fun `quality distinguishes measured recovered estimated mixed and unknown`() {
        assertEquals(
            DataQuality.MEASURED,
            summarizeToday(RecordMetric.STEPS, null, listOf(hour(start = 1))).quality,
        )
        assertEquals(
            DataQuality.RECOVERED,
            summarizeToday(
                RecordMetric.STEPS,
                null,
                listOf(hour(start = 1, stepsQuality = DataQuality.RECOVERED)),
            ).quality,
        )
        assertEquals(
            DataQuality.MIXED,
            summarizeToday(
                RecordMetric.STEPS,
                null,
                listOf(
                    hour(start = 1),
                    hour(start = 2, stepsQuality = DataQuality.ESTIMATED),
                ),
            ).quality,
        )
        assertEquals(
            RecordQualitySummary.HAS_UNKNOWN,
            summarizeToday(
                RecordMetric.STEPS,
                null,
                listOf(hour(start = 1), hour(start = 2, stepsQuality = DataQuality.UNKNOWN)),
            ).qualitySummary,
        )
    }

    @Test fun `DST duplicate hours are ordered by instant`() {
        val later = hour(start = 2_000, localHour = 1, offset = -18_000)
        val earlier = hour(start = 1_000, localHour = 1, offset = -14_400)
        assertEquals(listOf(earlier, later), orderedRecordedHours(listOf(later, earlier)))
    }

    private fun daily() = DailyActivityRecordEntity(
        id = "day", localDate = "2026-07-30", zoneId = "Asia/Tokyo",
        steps = 5000, unclassifiedSteps = 0, unclassifiedStepsQuality = DataQuality.UNKNOWN,
        distanceMeters = 3420.0, walkingDurationSeconds = 3120,
        estimatedCaloriesKcal = 180.0, averageWalkingSpeedKmh = 4.8,
        stepsQuality = DataQuality.MEASURED, distanceQuality = DataQuality.ESTIMATED,
        durationQuality = DataQuality.MEASURED, caloriesQuality = DataQuality.ESTIMATED,
        speedQuality = DataQuality.ESTIMATED, activeHourCount = 2, walkingSessionCount = 1,
        finalized = false, finalizedAtEpochMillis = null,
        createdAtEpochMillis = 0, updatedAtEpochMillis = 0,
    )

    private fun hour(
        start: Long,
        localHour: Int = 1,
        offset: Int = 32_400,
        steps: Long = 1,
        distance: Double? = null,
        duration: Long? = null,
        calories: Double? = null,
        speed: Double? = null,
        stepsQuality: DataQuality = DataQuality.MEASURED,
    ) = HourlyActivityRecordEntity(
        id = start.toString(), localDate = "2026-07-30", hourOfDay = localHour,
        zoneId = "Asia/Tokyo", utcOffsetSeconds = offset,
        periodStartEpochMillis = start, periodEndEpochMillis = start + 3_600_000,
        steps = steps, distanceMeters = distance, walkingDurationSeconds = duration,
        estimatedCaloriesKcal = calories, averageWalkingSpeedKmh = speed,
        stepsQuality = stepsQuality,
        distanceQuality = if (distance == null) DataQuality.UNKNOWN else DataQuality.ESTIMATED,
        durationQuality = if (duration == null) DataQuality.UNKNOWN else DataQuality.MEASURED,
        caloriesQuality = if (calories == null) DataQuality.UNKNOWN else DataQuality.ESTIMATED,
        speedQuality = if (speed == null) DataQuality.UNKNOWN else DataQuality.ESTIMATED,
        firstActivityAtEpochMillis = null, lastActivityAtEpochMillis = null,
        sensorEventCount = 1, recoveredSteps = 0, estimatedSteps = 0,
        appliedStepLengthMeters = .7, appliedWeightKg = 60.0, calorieFormulaVersion = 1,
        createdAtEpochMillis = 0, updatedAtEpochMillis = 0,
    )
}
