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
    val hours by remember { app.activityRepository.observeHours(LocalDate.now()) }
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
        item { Text("活動記録", style = MaterialTheme.typography.headlineMedium) }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                RecordPeriod.entries.forEach {
                    FilterChip(
                        selected = period == it,
                        onClick = { period = it },
                        label = { Text(periodLabel(it)) },
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
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    RecordMetric.entries.forEach {
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
                item { HourChart(hours, metric) { selectedHour = it } }
                selectedHour?.let { hour -> item { HourDetail(hour) } }
                if (period != RecordPeriod.HOURLY) {
                    item { Text("日・週・月表示は蓄積済みの日次データを使用します") }
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
    val byHour = records.associateBy { it.hourOfDay }
    val maximum = (0..23).maxOf { metricValue(byHour[it], metric) }.coerceAtLeast(1.0)
    GlassSurface(Modifier.fillMaxWidth().testTag("hourly_chart")) {
        Text("24時間")
        Row(
            modifier = Modifier.fillMaxWidth().height(150.dp),
            horizontalArrangement = Arrangement.spacedBy(2.dp),
            verticalAlignment = Alignment.Bottom,
        ) {
            (0..23).forEach { hour ->
                val record = byHour[hour]
                Box(
                    Modifier.weight(1f)
                        .height((120 * metricValue(record, metric) / maximum).coerceAtLeast(2.0).dp)
                        .clickable(enabled = record != null) { record?.let(onSelect) },
                ) {
                    Box(Modifier.fillMaxSize().padding(horizontal = 1.dp), contentAlignment = Alignment.BottomCenter) {
                        androidx.compose.foundation.Canvas(Modifier.fillMaxSize()) {
                            drawRect(if (record == null) Color.DarkGray else StepArenaColors.CyanSoft)
                        }
                    }
                }
            }
        }
        Text("0時　　　　　　　　　12時　　　　　　　　　23時")
    }
}

@Composable
private fun HourDetail(record: HourlyActivityRecordEntity) {
    GlassSurface(Modifier.fillMaxWidth().testTag("hour_detail")) {
        Text("${record.hourOfDay}:00～${record.hourOfDay}:59", style = MaterialTheme.typography.titleMedium)
        Text("歩数　${record.steps}歩")
        Text("歩行距離　${format(record.distanceMeters, "%.2f km", 1_000.0)}")
        Text("歩行時間　${record.walkingDurationSeconds?.let { "${it / 60}分${it % 60}秒" } ?: "―"}")
        Text("消費カロリー　${format(record.estimatedCaloriesKcal, "%.0f kcal")}")
        Text("平均歩行時速　${format(record.averageWalkingSpeedKmh, "%.2f km/h")}")
        Text("品質　${record.stepsQuality.name} / 距離・カロリーは推定")
    }
}

@Composable
private fun SessionRow(session: WalkingSessionEntity, onClick: () -> Unit) {
    GlassSurface(Modifier.fillMaxWidth().clickable(onClick = onClick).testTag("session_row")) {
        Text(formatTime(session.startedAtEpochMillis))
        Text("${session.sessionType.name}　${session.steps}歩")
        Text("${format(session.distanceMeters, "%.2f km", 1_000.0)}　${session.activeDurationSeconds / 60}分")
        if (session.stepsQuality.name != "MEASURED") Text("品質: ${session.stepsQuality.name}", color = StepArenaColors.Amber)
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
        Text("データソース Step Counter / 品質 ${session.stepsQuality.name}")
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

private fun periodLabel(period: RecordPeriod) = when (period) {
    RecordPeriod.HOURLY -> "時間"
    RecordPeriod.DAILY -> "日"
    RecordPeriod.WEEKLY -> "週"
    RecordPeriod.MONTHLY -> "月"
    RecordPeriod.SESSIONS -> "セッション"
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
