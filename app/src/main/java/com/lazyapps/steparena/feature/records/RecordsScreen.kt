package com.lazyapps.steparena.feature.records

import androidx.annotation.StringRes
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.lazyapps.steparena.R
import com.lazyapps.steparena.app.StepArenaApplication
import com.lazyapps.steparena.core.database.entity.DailyActivityRecordEntity
import com.lazyapps.steparena.core.database.entity.HourlyActivityRecordEntity
import com.lazyapps.steparena.core.database.entity.WalkingSessionEntity
import com.lazyapps.steparena.core.database.model.DataQuality
import com.lazyapps.steparena.core.designsystem.component.GlassSurface
import com.lazyapps.steparena.core.designsystem.theme.StepArenaColors
import com.lazyapps.steparena.core.designsystem.theme.StepArenaSpacing
import com.lazyapps.steparena.core.units.ActivityFormatter
import com.lazyapps.steparena.core.units.DistanceUnit
import com.lazyapps.steparena.core.units.SpeedUnit
import java.text.NumberFormat
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

enum class RecordPeriod { HOURLY, DAILY, SESSIONS }
enum class RecordMetric { STEPS, DISTANCE, DURATION, CALORIES, SPEED }

@Composable
fun RecordsScreen(modifier: Modifier = Modifier) {
    val app = LocalContext.current.applicationContext as StepArenaApplication
    val day by app.currentLocalDayProvider.current.collectAsState()
    val daily by remember(day) { app.activityRepository.observeToday(day.date, day.zoneId) }
        .collectAsState(initial = null)
    val hours by remember(day) { app.activityRepository.observeHours(day.date, day.zoneId) }
        .collectAsState(initial = emptyList())
    val sessions by remember { app.activityRepository.observeSessions() }
        .collectAsState(initial = emptyList())
    RecordsContent(daily, hours, sessions, modifier)
}

@Composable
internal fun RecordsContent(
    daily: DailyActivityRecordEntity?,
    hours: List<HourlyActivityRecordEntity>,
    sessions: List<WalkingSessionEntity>,
    modifier: Modifier = Modifier,
) {
    val orderedHours = remember(hours) { orderedRecordedHours(hours) }
    var period by remember { mutableStateOf(RecordPeriod.HOURLY) }
    var metric by remember { mutableStateOf(RecordMetric.STEPS) }
    var selectedHourKey by remember(hours) {
        mutableStateOf(orderedHours.firstOrNull()?.periodStartEpochMillis)
    }
    var selectedSession by remember { mutableStateOf<WalkingSessionEntity?>(null) }
    LaunchedEffect(orderedHours) {
        if (selectedHourKey !in orderedHours.map { it.periodStartEpochMillis }) {
            selectedHourKey = orderedHours.firstOrNull()?.periodStartEpochMillis
        }
    }
    val selectedHour = orderedHours.firstOrNull { it.periodStartEpochMillis == selectedHourKey }

    LazyColumn(
        modifier = modifier.fillMaxSize().padding(StepArenaSpacing.md).testTag("records_screen"),
        verticalArrangement = Arrangement.spacedBy(StepArenaSpacing.md),
    ) {
        item { Text(stringResource(R.string.records_title), style = MaterialTheme.typography.headlineMedium) }
        item {
            LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                items(RecordPeriod.entries) { item ->
                    FilterChip(
                        selected = period == item,
                        onClick = { period = item },
                        modifier = Modifier.testTag("record_period_${item.name.lowercase()}"),
                        label = { Text(stringResource(item.labelRes())) },
                    )
                }
            }
        }
        if (period == RecordPeriod.SESSIONS) {
            if (sessions.isEmpty()) item { EmptyRecords(R.string.records_empty_sessions) }
            items(sessions, key = { it.id }) { session ->
                SessionRow(session) { selectedSession = session }
            }
        } else {
            item {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    items(RecordMetric.entries) { item ->
                        FilterChip(
                            selected = metric == item,
                            onClick = { metric = item },
                            modifier = Modifier.testTag("record_metric_${item.name.lowercase()}"),
                            label = { Text(stringResource(item.labelRes())) },
                        )
                    }
                }
            }
            if (hours.isEmpty() && daily == null) {
                item { EmptyRecords(R.string.records_empty_today) }
            } else if (period == RecordPeriod.DAILY) {
                item { DailySummary(summarizeToday(metric, daily, hours), metric) }
            } else {
                item {
                    HourChart(orderedHours, metric, selectedHourKey) {
                        selectedHourKey = it.periodStartEpochMillis
                    }
                }
                selectedHour?.let { hour ->
                    item {
                        HourSelection(
                            record = hour,
                            ordered = orderedHours,
                            onSelect = { selectedHourKey = it.periodStartEpochMillis },
                        )
                    }
                }
            }
        }
        selectedSession?.let { session -> item { SessionDetail(session) } }
    }
}

@Composable
private fun DailySummary(summary: RecordSummary, metric: RecordMetric) {
    GlassSurface(Modifier.fillMaxWidth().testTag("daily_summary")) {
        Text(stringResource(metric.summaryTitleRes()), style = MaterialTheme.typography.titleMedium)
        Text(summaryValue(summary.value, metric), style = MaterialTheme.typography.headlineSmall)
        Text(stringResource(R.string.records_quality_heading))
        Text(stringResource(summary.qualitySummary.summaryRes()))
        Text(
            stringResource(
                if (summary.source == RecordSummarySource.DAILY) {
                    R.string.records_daily_source
                } else {
                    R.string.records_hourly_source
                },
            ),
        )
    }
}

@Composable
private fun HourChart(
    records: List<HourlyActivityRecordEntity>,
    metric: RecordMetric,
    selectedKey: Long?,
    onSelect: (HourlyActivityRecordEntity) -> Unit,
) {
    val recordsByHour = records.groupBy { it.hourOfDay }
    val maximum = records.maxOfOrNull { it.value(metric) ?: 0.0 }?.coerceAtLeast(1.0) ?: 1.0
    val summary = remember(records, metric) { chartAccessibilitySummary(metric, records) }
    val chartDescription = stringResource(summary.templateRes, *summary.arguments.toTypedArray())
    val actionLabel = stringResource(R.string.records_show_hour_detail)
    GlassSurface(
        Modifier.fillMaxWidth().testTag("hourly_chart").semantics {
            contentDescription = chartDescription
        },
    ) {
        Text(stringResource(R.string.records_chart_title, stringResource(metric.labelRes())))
        Row(
            modifier = Modifier.fillMaxWidth().height(150.dp),
            horizontalArrangement = Arrangement.spacedBy(2.dp),
            verticalAlignment = Alignment.Bottom,
        ) {
            (0..23).forEach { hour ->
                val hourRecords = recordsByHour[hour].orEmpty()
                Row(
                    modifier = Modifier.weight(1f).fillMaxHeight(),
                    horizontalArrangement = Arrangement.spacedBy(1.dp),
                ) {
                    if (hourRecords.isEmpty()) {
                        Box(Modifier.weight(1f).fillMaxHeight())
                    } else {
                        hourRecords.forEach { record ->
                            val selected = selectedKey == record.periodStartEpochMillis
                            val description = hourDescription(record, metric)
                            val fill = StepArenaColors.CyanSoft
                            val border = MaterialTheme.colorScheme.onSurface
                            Canvas(
                                Modifier.weight(1f).fillMaxHeight()
                                    .semantics {
                                        contentDescription = description
                                        this.selected = selected
                                        onClick(actionLabel) {
                                            onSelect(record)
                                            true
                                        }
                                    }
                                    .clickable { onSelect(record) },
                            ) {
                                val barHeight = (size.height * ((record.value(metric) ?: 0.0) / maximum))
                                    .toFloat().coerceAtLeast(2.dp.toPx())
                                val top = size.height - barHeight
                                drawRect(fill, topLeft = Offset(0f, top))
                                if (selected) {
                                    drawRect(
                                        color = border,
                                        topLeft = Offset(0f, top),
                                        style = Stroke(width = 2.dp.toPx()),
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
        Box(Modifier.fillMaxWidth()) {
            Text(stringResource(R.string.records_hour_zero), modifier = Modifier.align(Alignment.CenterStart))
            Text(stringResource(R.string.records_hour_twelve), modifier = Modifier.align(Alignment.Center))
            Text(stringResource(R.string.records_hour_twenty_three), modifier = Modifier.align(Alignment.CenterEnd))
        }
    }
}

@Composable
private fun HourSelection(
    record: HourlyActivityRecordEntity,
    ordered: List<HourlyActivityRecordEntity>,
    onSelect: (HourlyActivityRecordEntity) -> Unit,
) {
    val index = ordered.indexOfFirst { it.periodStartEpochMillis == record.periodStartEpochMillis }
    val previous = ordered.getOrNull(index - 1)
    val next = ordered.getOrNull(index + 1)
    GlassSurface(
        Modifier.fillMaxWidth().testTag("hour_detail"),
    ) {
        Text(
            stringResource(
                R.string.records_selected_hour,
                record.hourOfDay,
                offsetText(record.utcOffsetSeconds),
            ),
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.testTag("selected_hour_title"),
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(StepArenaSpacing.sm),
        ) {
            OutlinedButton(
                onClick = { previous?.let(onSelect) },
                enabled = previous != null,
                modifier = Modifier.weight(1f).heightIn(min = 48.dp).testTag("previous_hour"),
            ) { Text(stringResource(R.string.records_previous_hour)) }
            OutlinedButton(
                onClick = { next?.let(onSelect) },
                enabled = next != null,
                modifier = Modifier.weight(1f).heightIn(min = 48.dp).testTag("next_hour"),
            ) { Text(stringResource(R.string.records_next_hour)) }
        }
        HourDetail(record)
    }
}

@Composable
private fun HourDetail(record: HourlyActivityRecordEntity) {
    val locale = LocalConfiguration.current.locales[0]
    Text(stringResource(R.string.records_hour_steps, formatNumber(record.steps)))
    Text(stringResource(R.string.records_hour_distance, formatDistance(record.distanceMeters, locale)))
    Text(
        stringResource(
            R.string.records_hour_duration,
            record.walkingDurationSeconds?.let {
                stringResource(R.string.records_minutes_seconds, it / 60, it % 60)
            } ?: stringResource(R.string.records_no_value),
        ),
    )
    Text(stringResource(R.string.records_hour_calories, formatCalories(record.estimatedCaloriesKcal, locale)))
    Text(stringResource(R.string.records_hour_speed, formatSpeed(record.averageWalkingSpeedKmh, locale)))
    Text(stringResource(R.string.records_hour_quality, stringResource(record.stepsQuality.labelRes())))
    if (record.appliedWeightKg == 60.0) Text(stringResource(R.string.calorie_default_weight))
}

@Composable
private fun SessionRow(session: WalkingSessionEntity, onClick: () -> Unit) {
    val locale = LocalConfiguration.current.locales[0]
    GlassSurface(Modifier.fillMaxWidth().clickable(onClick = onClick).testTag("session_row")) {
        Text(formatTime(session.startedAtEpochMillis))
        Text(
            stringResource(
                R.string.records_session_summary,
                stringResource(session.sessionType.labelRes()),
                formatNumber(session.steps),
            ),
        )
        Text(
            stringResource(
                R.string.records_session_metrics,
                formatDistance(session.distanceMeters, locale),
                session.activeDurationSeconds / 60,
            ),
        )
        if (session.stepsQuality != DataQuality.MEASURED) {
            Text(
                stringResource(R.string.records_quality_value, stringResource(session.stepsQuality.labelRes())),
                color = StepArenaColors.Amber,
            )
        }
    }
}

@Composable
private fun SessionDetail(session: WalkingSessionEntity) {
    val locale = LocalConfiguration.current.locales[0]
    GlassSurface(Modifier.fillMaxWidth().testTag("session_detail")) {
        Text(stringResource(R.string.records_session_detail), style = MaterialTheme.typography.titleMedium)
        Text(stringResource(R.string.records_session_start, formatTime(session.startedAtEpochMillis)))
        Text(
            stringResource(
                R.string.records_session_end,
                session.endedAtEpochMillis?.let(::formatTime)
                    ?: stringResource(R.string.records_session_measuring),
            ),
        )
        Text(
            stringResource(
                R.string.records_session_steps_distance,
                formatNumber(session.steps),
                formatDistance(session.distanceMeters, locale),
            ),
        )
        Text(
            stringResource(
                R.string.records_session_durations,
                session.activeDurationSeconds,
                session.elapsedDurationSeconds,
                session.pausedDurationSeconds,
            ),
        )
        Text(stringResource(R.string.records_session_calories, formatCalories(session.estimatedCaloriesKcal, locale)))
        Text(
            stringResource(
                R.string.records_session_speeds,
                formatSpeed(session.averageMovingSpeedKmh, locale),
                formatSpeed(session.averageElapsedSpeedKmh, locale),
            ),
        )
        Text(stringResource(R.string.records_session_source, stringResource(session.stepsQuality.labelRes())))
    }
}

@Composable
private fun EmptyRecords(@StringRes messageRes: Int) =
    GlassSurface(Modifier.fillMaxWidth()) {
        Text(stringResource(messageRes))
        Text(stringResource(R.string.records_empty_hint))
    }

@StringRes
fun RecordPeriod.labelRes(): Int = when (this) {
    RecordPeriod.HOURLY -> R.string.record_period_hourly
    RecordPeriod.DAILY -> R.string.record_period_daily
    RecordPeriod.SESSIONS -> R.string.record_period_sessions
}

@StringRes
fun RecordMetric.labelRes(): Int = when (this) {
    RecordMetric.STEPS -> R.string.record_metric_steps
    RecordMetric.DISTANCE -> R.string.record_metric_distance
    RecordMetric.DURATION -> R.string.record_metric_duration
    RecordMetric.CALORIES -> R.string.record_metric_calories
    RecordMetric.SPEED -> R.string.record_metric_speed
}

@StringRes
private fun RecordMetric.summaryTitleRes(): Int = when (this) {
    RecordMetric.STEPS -> R.string.records_today_steps
    RecordMetric.DISTANCE -> R.string.records_today_distance
    RecordMetric.DURATION -> R.string.records_today_duration
    RecordMetric.CALORIES -> R.string.records_today_calories
    RecordMetric.SPEED -> R.string.records_today_speed
}

@StringRes
private fun RecordQualitySummary.summaryRes(): Int = when (this) {
    RecordQualitySummary.ALL_MEASURED -> R.string.records_quality_all_measured
    RecordQualitySummary.HAS_RECOVERED -> R.string.records_quality_has_recovered
    RecordQualitySummary.HAS_ESTIMATED -> R.string.records_quality_has_estimated
    RecordQualitySummary.MIXED -> R.string.records_quality_mixed_summary
    RecordQualitySummary.HAS_UNKNOWN -> R.string.records_quality_has_unknown
}

@Composable
private fun summaryValue(value: Double?, metric: RecordMetric): String {
    if (value == null || !value.isFinite()) return stringResource(R.string.records_no_value)
    return when (metric) {
        RecordMetric.STEPS -> stringResource(R.string.records_value_steps, formatNumber(value.toLong()))
        RecordMetric.DISTANCE -> stringResource(R.string.records_value_distance, value / 1_000.0)
        RecordMetric.DURATION -> stringResource(R.string.records_value_duration, (value / 60.0).toLong())
        RecordMetric.CALORIES -> stringResource(R.string.records_value_calories, value)
        RecordMetric.SPEED -> stringResource(R.string.records_value_speed, value)
    }
}

@Composable
private fun hourDescription(record: HourlyActivityRecordEntity, metric: RecordMetric): String =
    stringResource(
        R.string.records_hour_description,
        record.hourOfDay,
        offsetText(record.utcOffsetSeconds),
        summaryValue(record.value(metric), metric),
        stringResource(record.quality(metric).labelRes()),
    )

private fun offsetText(seconds: Int): String {
    val totalMinutes = seconds / 60
    val sign = if (totalMinutes >= 0) "+" else "-"
    val absolute = kotlin.math.abs(totalMinutes)
    return "$sign${(absolute / 60).toString().padStart(2, '0')}:" +
        (absolute % 60).toString().padStart(2, '0')
}

private fun formatDistance(value: Double?, locale: Locale): String =
    value?.takeIf(Double::isFinite)
        ?.let { ActivityFormatter.distance(it, DistanceUnit.KILOMETER, locale) } ?: "—"

private fun formatCalories(value: Double?, locale: Locale): String =
    value?.takeIf(Double::isFinite)?.let { ActivityFormatter.calories(it, locale) } ?: "—"

private fun formatSpeed(value: Double?, locale: Locale): String =
    value?.takeIf(Double::isFinite)
        ?.let { ActivityFormatter.speed(it / 3.6, SpeedUnit.KILOMETERS_PER_HOUR, locale) } ?: "—"

private fun formatTime(epoch: Long): String = DateTimeFormatter.ofPattern("M/d HH:mm")
    .format(Instant.ofEpochMilli(epoch).atZone(ZoneId.systemDefault()))

private fun formatNumber(value: Number): String =
    NumberFormat.getNumberInstance(Locale.getDefault()).format(value)
