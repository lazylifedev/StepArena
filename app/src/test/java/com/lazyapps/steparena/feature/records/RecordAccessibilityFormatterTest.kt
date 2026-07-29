package com.lazyapps.steparena.feature.records

import com.lazyapps.steparena.core.database.entity.HourlyActivityRecordEntity
import com.lazyapps.steparena.core.database.model.DataQuality
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Locale

class RecordAccessibilityFormatterTest {
    @Test fun summariesUseMetricSpecificUnitsAndSpeedHasNoTotal() {
        val records = listOf(record(hour = 18, steps = 1_240, distance = 840.0, duration = 840, calories = 42.0, speed = 5.2))
        assertTrue(summary(records, RecordMetric.STEPS).contains("合計1,240歩"))
        assertTrue(summary(records, RecordMetric.DISTANCE).contains("0.84キロメートル"))
        assertTrue(summary(records, RecordMetric.DURATION).contains("14分"))
        assertTrue(summary(records, RecordMetric.CALORIES).contains("42キロカロリー"))
        assertEquals(
            "今日の平均歩行速度グラフ。最も速い時間帯は18時台で時速5.2キロメートルです。",
            summary(records, RecordMetric.SPEED),
        )
        assertFalse(summary(records, RecordMetric.SPEED).contains("合計"))
    }

    private fun summary(records: List<HourlyActivityRecordEntity>, metric: RecordMetric) =
        RecordAccessibilityFormatter.chartSummary(records, metric, Locale.US)

    private fun record(
        hour: Int,
        steps: Long,
        distance: Double,
        duration: Long,
        calories: Double,
        speed: Double,
    ) = HourlyActivityRecordEntity(
        id = "$hour", localDate = "2026-07-30", hourOfDay = hour, zoneId = "Asia/Tokyo",
        utcOffsetSeconds = 32_400, periodStartEpochMillis = hour * 3_600_000L,
        periodEndEpochMillis = (hour + 1) * 3_600_000L, steps = steps,
        distanceMeters = distance, walkingDurationSeconds = duration,
        estimatedCaloriesKcal = calories, averageWalkingSpeedKmh = speed,
        stepsQuality = DataQuality.MEASURED, distanceQuality = DataQuality.ESTIMATED,
        durationQuality = DataQuality.MEASURED, caloriesQuality = DataQuality.ESTIMATED,
        speedQuality = DataQuality.ESTIMATED, firstActivityAtEpochMillis = null,
        lastActivityAtEpochMillis = null, sensorEventCount = 1, recoveredSteps = 0,
        estimatedSteps = 0, appliedStepLengthMeters = 0.7, appliedWeightKg = 60.0,
        calorieFormulaVersion = 1, createdAtEpochMillis = 0, updatedAtEpochMillis = 0,
    )
}
