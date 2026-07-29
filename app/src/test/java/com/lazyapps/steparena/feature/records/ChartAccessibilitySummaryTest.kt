package com.lazyapps.steparena.feature.records

import com.lazyapps.steparena.R
import com.lazyapps.steparena.core.database.entity.HourlyActivityRecordEntity
import com.lazyapps.steparena.core.database.model.DataQuality
import org.junit.Assert.assertEquals
import org.junit.Test

class ChartAccessibilitySummaryTest {
    private val records = listOf(
        hour(14, steps = 940, distance = 645.0, duration = 570, calories = 34.5, speed = 4.7),
        hour(15, steps = 940, distance = 645.0, duration = 570, calories = 34.5, speed = 4.8),
        hour(16, steps = 940, distance = 645.0, duration = 570, calories = 34.5, speed = 4.9),
        hour(17, steps = 940, distance = 645.0, duration = 570, calories = 34.5, speed = 5.0),
        hour(18, steps = 1_240, distance = 840.0, duration = 840, calories = 42.0, speed = 5.2),
    )

    @Test fun `steps summary contains total and peak hour`() {
        val summary = chartAccessibilitySummary(RecordMetric.STEPS, records)
        assertEquals(R.string.records_chart_steps_description, summary.templateRes)
        assertNumericArguments(listOf(5_000.0, 18.0, 1_240.0), summary)
    }

    @Test fun `distance duration and calories summaries contain totals and peaks`() {
        assertNumericArguments(
            listOf(3.42, 18.0, .84),
            chartAccessibilitySummary(RecordMetric.DISTANCE, records),
        )
        assertNumericArguments(
            listOf(52.0, 18.0, 14.0),
            chartAccessibilitySummary(RecordMetric.DURATION, records),
        )
        assertNumericArguments(
            listOf(180.0, 18.0, 42.0),
            chartAccessibilitySummary(RecordMetric.CALORIES, records),
        )
    }

    @Test fun `speed summary uses peak and never totals speed`() {
        val summary = chartAccessibilitySummary(RecordMetric.SPEED, records)
        assertEquals(R.string.records_chart_speed_description, summary.templateRes)
        assertNumericArguments(listOf(18.0, 5.2), summary)
    }

    @Test fun `empty and null-only metrics use empty summary`() {
        val expected = ChartAccessibilitySummary(R.string.records_chart_empty_description, emptyList())
        assertEquals(expected, chartAccessibilitySummary(RecordMetric.STEPS, emptyList()))
        assertEquals(expected, chartAccessibilitySummary(RecordMetric.DISTANCE, listOf(hour(1))))
    }

    @Test fun `null values are excluded from peak selection`() {
        val summary = chartAccessibilitySummary(
            RecordMetric.DISTANCE,
            listOf(hour(1, distance = null), hour(2, distance = 800.0)),
        )
        assertNumericArguments(listOf(.8, 2.0, .8), summary)
    }

    @Test fun `duplicate DST hour keeps the peak record value`() {
        val summary = chartAccessibilitySummary(
            RecordMetric.STEPS,
            listOf(hour(1, offset = -14_400, steps = 100), hour(1, offset = -18_000, steps = 200)),
        )
        assertNumericArguments(listOf(300.0, 1.0, 200.0), summary)
    }

    private fun assertNumericArguments(expected: List<Double>, summary: ChartAccessibilitySummary) {
        assertEquals(expected, summary.arguments.map { (it as Number).toDouble() })
    }

    private fun hour(
        hour: Int,
        offset: Int = 32_400,
        steps: Long = 0,
        distance: Double? = null,
        duration: Long? = null,
        calories: Double? = null,
        speed: Double? = null,
    ) = HourlyActivityRecordEntity(
        id = "$hour-$offset", localDate = "2026-07-30", hourOfDay = hour,
        zoneId = "Asia/Tokyo", utcOffsetSeconds = offset,
        periodStartEpochMillis = hour * 3_600_000L - offset * 1_000L,
        periodEndEpochMillis = (hour + 1) * 3_600_000L - offset * 1_000L,
        steps = steps, distanceMeters = distance, walkingDurationSeconds = duration,
        estimatedCaloriesKcal = calories, averageWalkingSpeedKmh = speed,
        stepsQuality = DataQuality.MEASURED,
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
