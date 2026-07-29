package com.lazyapps.steparena.feature.records

import com.lazyapps.steparena.core.database.entity.HourlyActivityRecordEntity
import java.text.NumberFormat
import java.util.Locale

object RecordAccessibilityFormatter {
    fun chartSummary(
        records: List<HourlyActivityRecordEntity>,
        metric: RecordMetric,
        locale: Locale = Locale.getDefault(),
    ): String {
        val values = records.mapNotNull { record ->
            metricValueOrNull(record, metric)?.takeIf(Double::isFinite)?.let { record to it }
        }
        val peak = values.maxByOrNull { it.second }
        val title = when (metric) {
            RecordMetric.STEPS -> "今日の歩数グラフ。"
            RecordMetric.DISTANCE -> "今日の距離グラフ。"
            RecordMetric.DURATION -> "今日の歩行時間グラフ。"
            RecordMetric.CALORIES -> "今日の消費カロリーグラフ。"
            RecordMetric.SPEED -> "今日の平均歩行速度グラフ。"
        }
        val total = when (metric) {
            RecordMetric.SPEED -> ""
            else -> "合計${formatValue(values.sumOf { it.second }, metric, locale)}。"
        }
        val peakText = peak?.let { (record, value) ->
            val adjective = when (metric) {
                RecordMetric.DISTANCE, RecordMetric.DURATION -> "最も長い"
                RecordMetric.SPEED -> "最も速い"
                else -> "最も多い"
            }
            "${adjective}時間帯は${record.hourOfDay}時台で${formatValue(value, metric, locale)}です。"
        }.orEmpty()
        return title + total + peakText
    }

    fun barDescription(
        record: HourlyActivityRecordEntity,
        metric: RecordMetric,
        quality: String,
        locale: Locale = Locale.getDefault(),
    ): String = "${record.hourOfDay}時台、${formatValue(metricValue(record, metric), metric, locale)}、$quality"

    fun formatValue(value: Double, metric: RecordMetric, locale: Locale = Locale.getDefault()): String {
        val number = NumberFormat.getNumberInstance(locale)
        return when (metric) {
            RecordMetric.STEPS -> "${number.format(value.toLong())}歩"
            RecordMetric.DISTANCE -> "${String.format(locale, "%.2f", value / 1_000.0)}キロメートル"
            RecordMetric.DURATION -> "${number.format((value / 60.0).toLong())}分"
            RecordMetric.CALORIES -> "${number.format(value.toLong())}キロカロリー"
            RecordMetric.SPEED -> "時速${String.format(locale, "%.1f", value)}キロメートル"
        }
    }
}

internal fun metricValueOrNull(record: HourlyActivityRecordEntity?, metric: RecordMetric): Double? = when (metric) {
    RecordMetric.STEPS -> record?.steps?.toDouble()
    RecordMetric.DISTANCE -> record?.distanceMeters
    RecordMetric.DURATION -> record?.walkingDurationSeconds?.toDouble()
    RecordMetric.CALORIES -> record?.estimatedCaloriesKcal
    RecordMetric.SPEED -> record?.averageWalkingSpeedKmh
}

internal fun metricValue(record: HourlyActivityRecordEntity?, metric: RecordMetric): Double =
    metricValueOrNull(record, metric) ?: 0.0
