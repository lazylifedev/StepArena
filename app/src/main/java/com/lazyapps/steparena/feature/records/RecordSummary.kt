package com.lazyapps.steparena.feature.records

import com.lazyapps.steparena.core.database.entity.DailyActivityRecordEntity
import com.lazyapps.steparena.core.database.entity.HourlyActivityRecordEntity
import com.lazyapps.steparena.core.database.model.DataQuality
import com.lazyapps.steparena.core.database.model.mergeQuality

data class RecordSummary(
    val value: Double?,
    val quality: DataQuality,
    val qualitySummary: RecordQualitySummary,
    val source: RecordSummarySource,
)

enum class RecordSummarySource { DAILY, HOURLY }
enum class RecordQualitySummary { ALL_MEASURED, HAS_RECOVERED, HAS_ESTIMATED, HAS_UNKNOWN, MIXED }

fun summarizeToday(
    metric: RecordMetric,
    daily: DailyActivityRecordEntity?,
    hours: List<HourlyActivityRecordEntity>,
): RecordSummary {
    val dailyValue = daily?.value(metric)
    if (daily != null && dailyValue != null) {
        return RecordSummary(
            dailyValue,
            daily.quality(metric),
            qualitySummary(hours.map { it.quality(metric) }, daily.quality(metric)),
            RecordSummarySource.DAILY,
        )
    }
    val validHours = hours.mapNotNull { hour ->
        hour.value(metric)?.takeIf(Double::isFinite)?.let { hour to it }
    }
    val value = when (metric) {
        RecordMetric.STEPS,
        RecordMetric.DISTANCE,
        RecordMetric.DURATION,
        RecordMetric.CALORIES,
        -> validHours.takeIf { it.isNotEmpty() }?.sumOf { it.second }

        RecordMetric.SPEED -> {
            val distance = hours.mapNotNull { it.distanceMeters }.sum()
            val duration = hours.mapNotNull { it.walkingDurationSeconds }.sum()
            if (distance > 0.0 && duration > 0L) distance / duration * 3.6
            else validHours.takeIf { it.isNotEmpty() }?.map { it.second }?.average()
        }
    }
    return RecordSummary(
        value = value,
        quality = mergeQuality(hours.map { it.quality(metric) }),
        qualitySummary = qualitySummary(hours.map { it.quality(metric) }),
        source = RecordSummarySource.HOURLY,
    )
}

private fun qualitySummary(
    hourly: List<DataQuality>,
    fallback: DataQuality? = null,
): RecordQualitySummary {
    val values = hourly.ifEmpty { listOfNotNull(fallback) }.toSet()
    return when {
        values.isEmpty() || DataQuality.UNKNOWN in values -> RecordQualitySummary.HAS_UNKNOWN
        values == setOf(DataQuality.MEASURED) -> RecordQualitySummary.ALL_MEASURED
        DataQuality.MIXED in values -> RecordQualitySummary.MIXED
        DataQuality.ESTIMATED in values -> RecordQualitySummary.HAS_ESTIMATED
        DataQuality.RECOVERED in values -> RecordQualitySummary.HAS_RECOVERED
        else -> RecordQualitySummary.MIXED
    }
}

fun orderedRecordedHours(hours: List<HourlyActivityRecordEntity>): List<HourlyActivityRecordEntity> =
    hours.sortedBy { it.periodStartEpochMillis }

private fun DailyActivityRecordEntity.value(metric: RecordMetric): Double? = when (metric) {
    RecordMetric.STEPS -> steps.toDouble()
    RecordMetric.DISTANCE -> distanceMeters
    RecordMetric.DURATION -> walkingDurationSeconds?.toDouble()
    RecordMetric.CALORIES -> estimatedCaloriesKcal
    RecordMetric.SPEED -> averageWalkingSpeedKmh
}

private fun DailyActivityRecordEntity.quality(metric: RecordMetric): DataQuality = when (metric) {
    RecordMetric.STEPS -> stepsQuality
    RecordMetric.DISTANCE -> distanceQuality
    RecordMetric.DURATION -> durationQuality
    RecordMetric.CALORIES -> caloriesQuality
    RecordMetric.SPEED -> speedQuality
}

internal fun HourlyActivityRecordEntity.value(metric: RecordMetric): Double? = when (metric) {
    RecordMetric.STEPS -> steps.toDouble()
    RecordMetric.DISTANCE -> distanceMeters
    RecordMetric.DURATION -> walkingDurationSeconds?.toDouble()
    RecordMetric.CALORIES -> estimatedCaloriesKcal
    RecordMetric.SPEED -> averageWalkingSpeedKmh
}

internal fun HourlyActivityRecordEntity.quality(metric: RecordMetric): DataQuality = when (metric) {
    RecordMetric.STEPS -> stepsQuality
    RecordMetric.DISTANCE -> distanceQuality
    RecordMetric.DURATION -> durationQuality
    RecordMetric.CALORIES -> caloriesQuality
    RecordMetric.SPEED -> speedQuality
}
