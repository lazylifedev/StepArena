package com.lazyapps.steparena.feature.records

import androidx.annotation.StringRes
import com.lazyapps.steparena.R
import com.lazyapps.steparena.core.database.entity.HourlyActivityRecordEntity

data class ChartAccessibilitySummary(
    @param:StringRes val templateRes: Int,
    val arguments: List<Any>,
)

fun chartAccessibilitySummary(
    metric: RecordMetric,
    records: List<HourlyActivityRecordEntity>,
): ChartAccessibilitySummary {
    val available = records.mapNotNull { record ->
        record.value(metric)?.takeIf(Double::isFinite)?.let { record to it }
    }
    if (available.isEmpty()) {
        return ChartAccessibilitySummary(R.string.records_chart_empty_description, emptyList())
    }
    val peak = available.maxBy { it.second }
    val total = when (metric) {
        RecordMetric.STEPS,
        RecordMetric.DISTANCE,
        RecordMetric.DURATION,
        RecordMetric.CALORIES,
        -> available.sumOf { it.second }
        RecordMetric.SPEED -> null
    }
    return when (metric) {
        RecordMetric.STEPS -> ChartAccessibilitySummary(
            R.string.records_chart_steps_description,
            listOf(total!!.toLong(), peak.first.hourOfDay, peak.second.toLong()),
        )
        RecordMetric.DISTANCE -> ChartAccessibilitySummary(
            R.string.records_chart_distance_description,
            listOf(total!! / 1_000.0, peak.first.hourOfDay, peak.second / 1_000.0),
        )
        RecordMetric.DURATION -> ChartAccessibilitySummary(
            R.string.records_chart_duration_description,
            listOf((total!! / 60.0).toLong(), peak.first.hourOfDay, (peak.second / 60.0).toLong()),
        )
        RecordMetric.CALORIES -> ChartAccessibilitySummary(
            R.string.records_chart_calories_description,
            listOf(total!!, peak.first.hourOfDay, peak.second),
        )
        RecordMetric.SPEED -> ChartAccessibilitySummary(
            R.string.records_chart_speed_description,
            listOf(peak.first.hourOfDay, peak.second),
        )
    }
}
