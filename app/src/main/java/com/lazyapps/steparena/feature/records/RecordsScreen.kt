package com.lazyapps.steparena.feature.records

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import com.lazyapps.steparena.R
import com.lazyapps.steparena.app.StepArenaApplication
import com.lazyapps.steparena.core.database.entity.HourlyActivityRecordEntity
import com.lazyapps.steparena.core.database.entity.WalkingSessionEntity
import com.lazyapps.steparena.core.designsystem.component.GlassSurface
import com.lazyapps.steparena.core.designsystem.theme.StepArenaColors
import com.lazyapps.steparena.core.designsystem.theme.StepArenaSpacing
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

enum class RecordPeriod { HOURLY, DAILY, WEEKLY, MONTHLY, SESSIONS }
enum class RecordMetric { STEPS, DISTANCE, DURATION, CALORIES, SPEED }

@Composable
fun RecordsScreen(modifier: Modifier = Modifier) {
    val app = LocalContext.current.applicationContext as StepArenaApplication
    val zoneId = remember { app.clock.zone }
    val hours by remember(zoneId) { app.activityRepository.observeHours(LocalDate.now(app.clock), zoneId) }
        .collectAsState(initial = emptyList())
    val sessions by remember { app.activityRepository.observeSessions() }
        .collectAsState(initial = emptyList())
    var period by remember { mutableStateOf(RecordPeriod.HOURLY) }
    var metric by remember { mutableStateOf(RecordMetric.STEPS) }
    var selectedHour by remember { mutableStateOf<HourlyActivityRecordEntity?>(null) }
    var selectedSession by remember { mutableStateOf<WalkingSessionEntity?>(null) }

    LazyColumn(
        modifier = modifier.fillMaxSize().padding(StepArenaSpacing.md).testTag("records_screen"),
        verticalArrangement = Arrangement.spacedBy(StepArenaSpacing.md),
    ) {
        item { Text(stringResource(R.string.records_title), style = MaterialTheme.typography.headlineMedium) }
        item {
            LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                items(listOf(RecordPeriod.HOURLY, RecordPeriod.DAILY, RecordPeriod.SESSIONS)) {
                    FilterChip(
                        selected = period == it,
                        onClick = { period = it },
                        label = { Text(stringResource(periodLabelRes(it))) },
                    )
                }
            }
        }
        if (period == RecordPeriod.SESSIONS) {
            if (sessions.isEmpty()) item { EmptyRecords("歩行セッションはまだありません") }
            items(sessions, key = { it.id }) { session ->
                SessionRow(session) { selectedSession = session }
            }
        } else {
            item {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    items(RecordMetric.entries) {
                        FilterChip(
                            selected = metric == it,
                            onClick = { metric = it },
                            label = { Text(metricLabel(it)) },
                        )
                    }
                }
            }
            if (hours.isEmpty()) item { EmptyRecords("今日の活動記録はまだありません") }
            else {
                if (period == RecordPeriod.DAILY) {
                    item {
                        GlassSurface(Modifier.fillMaxWidth().testTag("daily_summary")) {
                            Text("今日", style = MaterialTheme.typography.titleMedium)
                            Text("${hours.sumOf { it.steps }}歩", style = MaterialTheme.typography.headlineSmall)
                            Text("時間別の記録を合計して表示しています")
                        }
                    }
                } else {
                    item { HourChart(hours, metric) { selectedHour = it } }
                    selectedHour?.let { hour -> item { HourDetail(hour) } }
                }
            }
        }
        selectedSession?.let { session -> item { SessionDetail(session) } }
    }
}

@Composable
private fun HourChart(
    records: List<HourlyActivityRecordEntity>,
    metric: RecordMetric,
    onSelect: (HourlyActivityRecordEntity) -> Unit,
) {
    val ordered = records.sortedBy { it.periodStartEpochMillis }
    val maximum = ordered.maxOfOrNull { metricValue(it, metric) }?.coerceAtLeast(1.0) ?: 1.0
    val totalSteps = ordered.sumOf { it.steps }
    val maxRecord = ordered.maxByOrNull { metricValue(it, metric) }
    val chartDescription = "今日の${metricLabel(metric)}グラフ。合計${formatNumber(totalSteps)}歩。" +
        (maxRecord?.let { "最も多い時間帯は${it.hourOfDay}時台で${formatNumber(it.steps)}歩です。" } ?: "")
    GlassSurface(Modifier.fillMaxWidth().testTag("hourly_chart").semantics {
        contentDescription = chartDescription
    }) {
        Text("今日の${metricLabel(metric)}")
        Row(
            modifier = Modifier.fillMaxWidth().height(150.dp),
            horizontalArrangement = Arrangement.spacedBy(2.dp),
            verticalAlignment = Alignment.Bottom,
        ) {
            ordered.forEach { record ->
                Box(
                    Modifier.weight(1f)
                        .height((120 * metricValue(record, metric) / maximum).coerceAtLeast(2.0).dp)
                        .clickable { onSelect(record) },
                ) {
                    Box(Modifier.fillMaxSize().padding(horizontal = 1.dp), contentAlignment = Alignment.BottomCenter) {
                        androidx.compose.foundation.Canvas(Modifier.fillMaxSize()) {
                            drawRect(StepArenaColors.CyanSoft)
                        }
                    }
                }
            }
        }
        Box(Modifier.fillMaxWidth()) {
            Text("0時", modifier = Modifier.align(Alignment.CenterStart))
            Text("12時", modifier = Modifier.align(Alignment.Center))
            Text("23時", modifier = Modifier.align(Alignment.CenterEnd))
        }
    }
}

@Composable
private fun HourDetail(record: HourlyActivityRecordEntity) {
    GlassSurface(Modifier.fillMaxWidth().testTag("hour_detail")) {
        Text("${record.hourOfDay}:00（UTC${offsetText(record.utcOffsetSeconds)}）", style = MaterialTheme.typography.titleMedium)
        Text("歩数　${record.steps}歩")
        Text("歩行距離　${format(record.distanceMeters, "%.2f km", 1_000.0)}")
        Text("歩行時間　${record.walkingDurationSeconds?.let { "${it / 60}分${it % 60}秒" } ?: "―"}")
        Text("消費カロリー　${format(record.estimatedCaloriesKcal, "%.0f kcal")}")
        Text("平均歩行時速　${format(record.averageWalkingSpeedKmh, "%.2f km/h")}")
        Text("品質　${stringResource(record.stepsQuality.labelRes())} / 距離・カロリーは推定")
        if (record.appliedWeightKg == 60.0) Text(stringResource(R.string.calorie_default_weight))
    }
}

@Composable
private fun SessionRow(session: WalkingSessionEntity, onClick: () -> Unit) {
    GlassSurface(Modifier.fillMaxWidth().clickable(onClick = onClick).testTag("session_row")) {
        Text(formatTime(session.startedAtEpochMillis))
        Text("${stringResource(session.sessionType.labelRes())}　${session.steps}歩")
        Text("${format(session.distanceMeters, "%.2f km", 1_000.0)}　${session.activeDurationSeconds / 60}分")
        if (session.stepsQuality != com.lazyapps.steparena.core.database.model.DataQuality.MEASURED) {
            Text("品質: ${stringResource(session.stepsQuality.labelRes())}", color = StepArenaColors.Amber)
        }
    }
}

@Composable
private fun SessionDetail(session: WalkingSessionEntity) {
    GlassSurface(Modifier.fillMaxWidth().testTag("session_detail")) {
        Text("セッション詳細", style = MaterialTheme.typography.titleMedium)
        Text("開始 ${formatTime(session.startedAtEpochMillis)}")
        Text("終了 ${session.endedAtEpochMillis?.let(::formatTime) ?: "計測中"}")
        Text("歩数 ${session.steps}歩 / 距離 ${format(session.distanceMeters, "%.2f km", 1_000.0)}")
        Text("歩行 ${session.activeDurationSeconds}秒 / 経過 ${session.elapsedDurationSeconds}秒 / 停止 ${session.pausedDurationSeconds}秒")
        Text("推定カロリー ${format(session.estimatedCaloriesKcal, "%.0f kcal")}")
        Text("移動速度 ${format(session.averageMovingSpeedKmh, "%.2f km/h")} / 経過速度 ${format(session.averageElapsedSpeedKmh, "%.2f km/h")}")
        Text("データソース Step Counter / 品質 ${stringResource(session.stepsQuality.labelRes())}")
    }
}

@Composable private fun EmptyRecords(message: String) =
    GlassSurface(Modifier.fillMaxWidth()) { Text(message); Text("計測を開始するとここに記録されます") }

private fun metricValue(record: HourlyActivityRecordEntity?, metric: RecordMetric): Double = when (metric) {
    RecordMetric.STEPS -> record?.steps?.toDouble()
    RecordMetric.DISTANCE -> record?.distanceMeters
    RecordMetric.DURATION -> record?.walkingDurationSeconds?.toDouble()
    RecordMetric.CALORIES -> record?.estimatedCaloriesKcal
    RecordMetric.SPEED -> record?.averageWalkingSpeedKmh
} ?: 0.0

private fun periodLabelRes(period: RecordPeriod) = when (period) {
    RecordPeriod.HOURLY -> R.string.record_period_hourly
    RecordPeriod.DAILY -> R.string.record_period_daily
    RecordPeriod.WEEKLY -> R.string.record_period_weekly
    RecordPeriod.MONTHLY -> R.string.record_period_monthly
    RecordPeriod.SESSIONS -> R.string.record_period_sessions
}
private fun offsetText(seconds: Int): String {
    val totalMinutes = seconds / 60
    val sign = if (totalMinutes >= 0) "+" else "-"
    val absolute = kotlin.math.abs(totalMinutes)
    return "$sign${absolute / 60}:${(absolute % 60).toString().padStart(2, '0')}"
}
private fun metricLabel(metric: RecordMetric) = when (metric) {
    RecordMetric.STEPS -> "歩数"
    RecordMetric.DISTANCE -> "距離"
    RecordMetric.DURATION -> "時間"
    RecordMetric.CALORIES -> "カロリー"
    RecordMetric.SPEED -> "時速"
}
private fun format(value: Double?, pattern: String, divisor: Double = 1.0): String =
    value?.takeIf { it.isFinite() }?.let { String.format(Locale.getDefault(), pattern, it / divisor) } ?: "―"
private fun formatTime(epoch: Long): String = DateTimeFormatter.ofPattern("M/d HH:mm")
    .format(Instant.ofEpochMilli(epoch).atZone(ZoneId.systemDefault()))
private fun formatNumber(value: Number): String =
    java.text.NumberFormat.getNumberInstance(Locale.JAPAN).format(value)
