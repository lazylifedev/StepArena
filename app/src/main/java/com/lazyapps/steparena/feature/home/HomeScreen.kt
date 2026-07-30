package com.lazyapps.steparena.feature.home

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.lazyapps.steparena.R
import com.lazyapps.steparena.core.designsystem.component.AnimatedMetricValue
import com.lazyapps.steparena.core.designsystem.component.EmptyState
import com.lazyapps.steparena.core.designsystem.component.ErrorState
import com.lazyapps.steparena.core.designsystem.component.GlassSurface
import com.lazyapps.steparena.core.designsystem.component.LeagueSummaryCard
import com.lazyapps.steparena.core.designsystem.component.LoadingState
import com.lazyapps.steparena.core.designsystem.component.MatchCard
import com.lazyapps.steparena.core.designsystem.component.MetricCard
import com.lazyapps.steparena.core.designsystem.component.PrimaryActionButton
import com.lazyapps.steparena.core.designsystem.component.RankBadge
import com.lazyapps.steparena.core.designsystem.component.RankProgressBar
import com.lazyapps.steparena.core.designsystem.component.SectionHeader
import com.lazyapps.steparena.core.designsystem.component.StepProgressRing
import com.lazyapps.steparena.core.designsystem.component.TrackingStatusChip
import com.lazyapps.steparena.core.designsystem.motion.MotionLevel
import com.lazyapps.steparena.core.designsystem.theme.StepArenaColors
import com.lazyapps.steparena.core.designsystem.theme.StepArenaMotion
import com.lazyapps.steparena.core.designsystem.theme.StepArenaSpacing
import com.lazyapps.steparena.core.model.DataReliability
import com.lazyapps.steparena.core.model.HomeSnapshot
import com.lazyapps.steparena.core.model.MatchOutcome
import com.lazyapps.steparena.core.model.RankTier
import com.lazyapps.steparena.core.model.TrackingStatus
import com.lazyapps.steparena.core.time.StepArenaTimeFormatter
import com.lazyapps.steparena.core.units.ActivityCalculations
import com.lazyapps.steparena.core.units.ActivityFormatter
import com.lazyapps.steparena.core.units.DistanceUnit
import com.lazyapps.steparena.core.units.SpeedUnit
import java.text.NumberFormat
import java.time.ZoneId
import java.util.Locale

object HomeTestTags {
    const val CONTENT = "home_content"
    const val START_BUTTON = "start_session_button"
    const val STOP_TRACKING_BUTTON = "stop_tracking_button"
    const val MANUAL_SESSION = "manual_session"
    const val TRACKING_STATUS = "tracking_status"
    const val MATCH_CARD = "match_card"
    const val BOTTOM_REACH_MARKER = "home_bottom_marker"
}

@Composable
fun HomeScreen(
    uiState: HomeUiState,
    onAction: (HomeAction) -> Unit,
    modifier: Modifier = Modifier,
) {
    when (val content = uiState.content) {
        HomeContent.Loading -> LoadingState(
            description = stringResource(R.string.loading_description),
            modifier = modifier,
        )
        HomeContent.Empty -> EmptyState(
            title = stringResource(R.string.empty_title),
            message = stringResource(R.string.empty_message),
            modifier = modifier.padding(StepArenaSpacing.md),
        )
        is HomeContent.Failure -> ErrorState(
            title = stringResource(R.string.error_title),
            message = stringResource(R.string.error_message),
            retryText = stringResource(R.string.retry),
            onRetry = { onAction(HomeAction.Retry) },
            modifier = modifier.padding(StepArenaSpacing.md),
        )
        is HomeContent.Ready -> HomeReadyContent(
            snapshot = content.snapshot,
            uiState = uiState,
            onAction = onAction,
            modifier = modifier,
        )
    }
}

@Composable
private fun HomeReadyContent(
    snapshot: HomeSnapshot,
    uiState: HomeUiState,
    onAction: (HomeAction) -> Unit,
    modifier: Modifier,
) {
    val locale = LocalConfiguration.current.locales[0]
    val numberFormat = remember(locale) { NumberFormat.getIntegerInstance(locale) }
    val goalProgress = ActivityCalculations.goalProgress(snapshot.metrics.steps, snapshot.metrics.goalSteps)
    var cardsVisible by remember(snapshot) { mutableStateOf(uiState.motionLevel == MotionLevel.OFF) }
    var matchExpanded by rememberSaveable { mutableStateOf(false) }
    var showStopConfirmation by rememberSaveable { mutableStateOf(false) }
    val entranceDuration = motionDuration(uiState.motionLevel)

    LaunchedEffect(snapshot, uiState.motionLevel) { cardsVisible = true }

    LazyColumn(
        modifier = modifier.fillMaxSize().testTag(HomeTestTags.CONTENT),
        contentPadding = PaddingValues(
            start = StepArenaSpacing.md,
            top = StepArenaSpacing.lg,
            end = StepArenaSpacing.md,
            bottom = StepArenaSpacing.xl,
        ),
        verticalArrangement = Arrangement.spacedBy(StepArenaSpacing.md),
    ) {
        item {
            Column {
                Text(
                    stringResource(R.string.home_title),
                    modifier = Modifier.semantics { heading() },
                    style = MaterialTheme.typography.headlineMedium,
                    color = StepArenaColors.CyanSoft,
                )
                Text(
                    stringResource(R.string.home_subtitle),
                    color = StepArenaColors.TextSecondary,
                )
            }
        }
        item {
            TrackingPanel(snapshot, locale, uiState.motionLevel)
        }
        item {
            StepsPanel(snapshot, goalProgress, numberFormat, uiState.motionLevel)
        }
        item {
            uiState.manualSession?.let { manual ->
                GlassSurface(
                    Modifier.fillMaxWidth().testTag(HomeTestTags.MANUAL_SESSION),
                ) {
                    Text(stringResource(R.string.home_manual_tracking), color = StepArenaColors.Emerald)
                    Text(
                        stringResource(R.string.home_manual_started, StepArenaTimeFormatter.time(
                            java.time.Instant.ofEpochMilli(manual.startedAtEpochMillis),
                            ZoneId.systemDefault(),
                            locale,
                            true,
                        )),
                    )
                    Text(stringResource(R.string.home_manual_steps, numberFormat.format(manual.steps)))
                    Text(
                        stringResource(
                            R.string.home_manual_metrics,
                            ActivityFormatter.distance(manual.distanceMeters, DistanceUnit.KILOMETER, locale),
                            ActivityFormatter.duration(manual.elapsedSeconds),
                        ),
                    )
                }
            }
        }
        item {
            GlassSurface(Modifier.fillMaxWidth()) {
                Text(
                    stringResource(R.string.home_walking_record_title),
                    style = MaterialTheme.typography.titleMedium,
                )
                Text(
                    stringResource(R.string.home_walking_record_explanation),
                    style = MaterialTheme.typography.bodyMedium,
                    color = StepArenaColors.TextSecondary,
                )
                PrimaryActionButton(
                    text = when (uiState.sessionState) {
                        SessionState.TRACKING_STOPPED -> stringResource(R.string.home_start_tracking)
                        SessionState.TRACKING -> stringResource(R.string.home_start_walk)
                        SessionState.MANUAL_WALK -> stringResource(R.string.home_end_walk)
                    },
                    onClick = {
                        when (uiState.sessionState) {
                            SessionState.TRACKING_STOPPED -> onAction(HomeAction.StartSession)
                            SessionState.TRACKING -> onAction(HomeAction.StartManualWalk)
                            SessionState.MANUAL_WALK -> uiState.manualSession?.let {
                                onAction(HomeAction.EndManualWalk(it.id))
                            }
                        }
                    },
                    enabled = uiState.sensorSupported,
                    modifier = Modifier.testTag(HomeTestTags.START_BUTTON),
                )
            }
        }
        if (uiState.sessionState != SessionState.TRACKING_STOPPED) {
            item {
                TextButton(
                    onClick = { showStopConfirmation = true },
                    modifier = Modifier.testTag(HomeTestTags.STOP_TRACKING_BUTTON),
                ) { Text(stringResource(R.string.home_stop_tracking)) }
            }
        }
        item {
            SectionHeader(
                stringResource(R.string.activity_metrics),
                Modifier.semantics { heading() },
            )
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(StepArenaSpacing.sm)) {
                AnimatedMetricCard(
                    labelRes = R.string.metric_distance,
                    value = snapshot.metrics.distanceMeters,
                    formatter = {
                        if (snapshot.metricsAvailable) ActivityFormatter.distance(
                            it, DistanceUnit.KILOMETER, locale,
                        ) else "―"
                    },
                    motionLevel = uiState.motionLevel,
                    modifier = Modifier.weight(1f),
                )
                AnimatedMetricCard(
                    labelRes = R.string.metric_duration,
                    value = snapshot.metrics.durationSeconds.toDouble(),
                    formatter = {
                        if (snapshot.metricsAvailable && it > 0) {
                            ActivityFormatter.duration(it.toLong())
                        } else "―"
                    },
                    motionLevel = uiState.motionLevel,
                    modifier = Modifier.weight(1f),
                )
            }
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(StepArenaSpacing.sm)) {
                AnimatedMetricCard(
                    labelRes = R.string.metric_calories,
                    value = snapshot.metrics.caloriesKcal,
                    formatter = {
                        if (snapshot.metricsAvailable) ActivityFormatter.calories(it, locale) else "―"
                    },
                    motionLevel = uiState.motionLevel,
                    modifier = Modifier.weight(1f),
                )
                AnimatedMetricCard(
                    labelRes = R.string.metric_speed,
                    value = snapshot.metrics.averageSpeedMetersPerSecond,
                    formatter = {
                        if (snapshot.metricsAvailable && it > 0) {
                            ActivityFormatter.speed(it, SpeedUnit.KILOMETERS_PER_HOUR, locale)
                        } else "―"
                    },
                    motionLevel = uiState.motionLevel,
                    modifier = Modifier.weight(1f),
                )
            }
        }
        item {
            AnimatedVisibility(
                visible = cardsVisible,
                enter = fadeIn(tween(entranceDuration)) +
                    slideInVertically(tween(entranceDuration)) { it / 8 },
            ) {
                RankPanel(snapshot, uiState.motionLevel)
            }
        }
        item {
            val opponentName = if (snapshot.match.opponentName.isBlank()) {
                stringResource(R.string.match_opponent_preparing)
            } else {
                snapshot.match.opponentName
            }
            MatchCard(
                title = stringResource(R.string.match_title),
                opponent = stringResource(
                    R.string.match_opponent,
                    opponentName,
                ),
                selfLabel = stringResource(R.string.match_you),
                opponentLabel = stringResource(R.string.match_rival),
                selfProgress = snapshot.match.selfProgress,
                opponentProgress = snapshot.match.opponentProgress,
                supportingText = matchSupportingText(snapshot, numberFormat),
                motionLevel = uiState.motionLevel,
                expandedText = stringResource(R.string.match_expanded_detail),
                expanded = matchExpanded,
                onClick = { matchExpanded = !matchExpanded },
                interactionLabel = if (matchExpanded) {
                    stringResource(R.string.match_collapse)
                } else {
                    stringResource(R.string.match_expand)
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag(HomeTestTags.MATCH_CARD),
            )
        }
        item {
            LeagueSummaryCard(
                title = stringResource(R.string.league_title),
                position = stringResource(
                    R.string.league_position,
                    snapshot.league.position,
                    snapshot.league.memberCount,
                ),
                supportingText = stringResource(
                    R.string.league_promotion_gap,
                    snapshot.league.pointsToPromotion,
                ),
                modifier = Modifier.fillMaxWidth(),
            )
        }
        item {
            Spacer(
                Modifier
                    .fillMaxWidth()
                    .height(StepArenaSpacing.minimumTouchTarget)
                    .testTag(HomeTestTags.BOTTOM_REACH_MARKER),
            )
        }
    }
    if (showStopConfirmation) {
        AlertDialog(
            onDismissRequest = { showStopConfirmation = false },
            title = { Text(stringResource(R.string.home_stop_confirm_title)) },
            text = { Text(stringResource(R.string.home_stop_confirm_body)) },
            confirmButton = {
                TextButton(onClick = {
                    showStopConfirmation = false
                    onAction(HomeAction.StopTracking)
                }) { Text(stringResource(R.string.home_stop)) }
            },
            dismissButton = {
                TextButton(onClick = { showStopConfirmation = false }) { Text(stringResource(R.string.common_cancel)) }
            },
        )
    }
}

@Composable
private fun TrackingPanel(
    snapshot: HomeSnapshot,
    locale: Locale,
    motionLevel: MotionLevel,
) {
    GlassSurface(Modifier.fillMaxWidth().testTag(HomeTestTags.TRACKING_STATUS)) {
        TrackingStatusChip(
            text = trackingText(snapshot.trackingStatus),
            isHealthy = snapshot.trackingStatus == TrackingStatus.ACTIVE,
            motionLevel = motionLevel,
        )
        if (snapshot.trackingStatus == TrackingStatus.MAY_BE_STOPPED) {
            Text(
                stringResource(R.string.tracking_sensor_warning),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.error,
            )
        }
        snapshot.lastHealthyAt?.let {
            Spacer(Modifier.height(StepArenaSpacing.xs))
            Text(
                stringResource(
                    R.string.last_healthy_time,
                    StepArenaTimeFormatter.time(
                        instant = it,
                        zoneId = ZoneId.systemDefault(),
                        locale = locale,
                        use24Hour = true,
                    ),
                ),
                style = MaterialTheme.typography.bodyMedium,
                color = StepArenaColors.TextSecondary,
            )
        }
        reliabilityText(snapshot.reliability)?.let {
            Text(it, style = MaterialTheme.typography.bodyMedium, color = StepArenaColors.Amber)
        }
        if (snapshot.isOffline) {
            Text(
                stringResource(R.string.offline_label),
                style = MaterialTheme.typography.labelLarge,
                color = StepArenaColors.Amber,
            )
        }
    }
}

@Composable
private fun RankPanel(snapshot: HomeSnapshot, motionLevel: MotionLevel) {
    val progress = ActivityCalculations.rankProgress(
        snapshot.rank.points,
        snapshot.rank.pointsToNextRank,
    )
    GlassSurface(Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column {
                Text(stringResource(R.string.rank_section), color = StepArenaColors.TextSecondary)
                Text(
                    rankName(snapshot.rank.tier),
                    style = MaterialTheme.typography.headlineMedium,
                )
                Text(stringResource(R.string.rank_division, snapshot.rank.division))
            }
            RankBadge(stringResource(R.string.rank_points, snapshot.rank.points))
        }
        Spacer(Modifier.height(StepArenaSpacing.md))
        RankProgressBar(
            progress = progress,
            description = stringResource(
                R.string.rank_progress_description,
                (progress * 100).toInt(),
            ),
            motionLevel = motionLevel,
        )
        Spacer(Modifier.height(StepArenaSpacing.xs))
        Text(
            stringResource(R.string.points_to_next_rank, snapshot.rank.pointsToNextRank),
            style = MaterialTheme.typography.bodyMedium,
            color = StepArenaColors.TextSecondary,
        )
    }
}

@Composable
private fun StepsPanel(
    snapshot: HomeSnapshot,
    goalProgress: Float,
    numberFormat: NumberFormat,
    motionLevel: MotionLevel,
) {
    GlassSurface(Modifier.fillMaxWidth()) {
        Text(stringResource(R.string.today_steps), color = StepArenaColors.TextSecondary)
        Spacer(Modifier.height(StepArenaSpacing.sm))
        Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
            StepProgressRing(
                progress = goalProgress,
                description = stringResource(
                    R.string.goal_progress,
                    numberFormat.format(snapshot.metrics.goalSteps),
                    (goalProgress * 100).toInt(),
                ),
                modifier = Modifier.size(220.dp),
                motionLevel = motionLevel,
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    AnimatedMetricValue(
                        value = snapshot.metrics.steps,
                        formattedValue = { numberFormat.format(it) },
                        description = stringResource(
                            R.string.steps_accessibility,
                            numberFormat.format(snapshot.metrics.steps),
                        ),
                        motionLevel = motionLevel,
                    )
                    Text(
                        stringResource(R.string.steps_value, ""),
                        color = StepArenaColors.TextSecondary,
                    )
                    if (snapshot.recoveredSteps > 0) {
                        Column {
                            Text(
                                stringResource(
                                    R.string.home_measured_steps,
                                    numberFormat.format(snapshot.measuredSteps),
                                ),
                                style = MaterialTheme.typography.bodySmall,
                                color = StepArenaColors.TextSecondary,
                            )
                            Text(
                                stringResource(
                                    R.string.home_health_connect_steps,
                                    numberFormat.format(snapshot.recoveredSteps),
                                ),
                                style = MaterialTheme.typography.bodySmall,
                                color = StepArenaColors.TextSecondary,
                            )
                        }
                    }
                    Text(
                        stringResource(
                            R.string.goal_progress_compact,
                            numberFormat.format(snapshot.metrics.goalSteps),
                            (goalProgress * 100).toInt(),
                        ),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            }
        }
        AnimatedVisibility(
            visible = goalProgress >= 1f,
            enter = fadeIn(tween(motionDuration(motionLevel))),
            exit = fadeOut(tween(motionDuration(motionLevel))),
        ) {
            Text(
                stringResource(R.string.goal_achieved),
                modifier = Modifier
                    .align(Alignment.CenterHorizontally)
                    .padding(top = StepArenaSpacing.sm),
                color = StepArenaColors.Emerald,
                style = MaterialTheme.typography.titleMedium,
            )
        }
    }
}

@Composable
private fun AnimatedMetricCard(
    labelRes: Int,
    value: Double,
    formatter: (Double) -> String,
    motionLevel: MotionLevel,
    modifier: Modifier = Modifier,
) {
    val animated by animateFloatAsState(
        targetValue = value.coerceAtLeast(0.0).toFloat(),
        animationSpec = tween(
            durationMillis = motionDuration(motionLevel),
            easing = StepArenaMotion.emphasized,
        ),
        label = "activityMetric",
    )
    MetricCard(
        label = stringResource(labelRes),
        value = formatter(animated.toDouble()),
        modifier = modifier.fillMaxWidth(),
    )
}

@Composable
private fun trackingText(status: TrackingStatus): String = stringResource(
    when (status) {
        TrackingStatus.ACTIVE -> R.string.tracking_active
        TrackingStatus.NOT_STARTED -> R.string.tracking_not_started
        TrackingStatus.MAY_BE_STOPPED -> R.string.tracking_may_be_stopped
        TrackingStatus.PERMISSION_REQUIRED -> R.string.tracking_permission_required
        TrackingStatus.BATTERY_SETTING_REQUIRED -> R.string.tracking_battery_required
    },
)

@Composable
private fun rankName(tier: RankTier): String = stringResource(
    when (tier) {
        RankTier.BRONZE -> R.string.rank_bronze
        RankTier.SILVER -> R.string.rank_silver
        RankTier.GOLD -> R.string.rank_gold
        RankTier.PLATINUM -> R.string.rank_platinum
        RankTier.DIAMOND -> R.string.rank_diamond
    },
)

@Composable
private fun reliabilityText(reliability: DataReliability): String? = when (reliability) {
    DataReliability.COMPLETE -> null
    DataReliability.PARTLY_ESTIMATED -> stringResource(R.string.estimated_data_label)
    DataReliability.PARTLY_RECOVERED -> stringResource(R.string.recovered_data_label)
    DataReliability.NO_DATA -> stringResource(R.string.empty_message)
}

@Composable
private fun matchSupportingText(snapshot: HomeSnapshot, format: NumberFormat): String =
    when (snapshot.match.outcome) {
        MatchOutcome.WON -> stringResource(R.string.match_won, snapshot.winStreak)
        MatchOutcome.LOST -> stringResource(R.string.match_lost)
        MatchOutcome.IN_PROGRESS -> if (snapshot.match.stepsToLead == 0) {
            stringResource(R.string.match_leading, snapshot.winStreak)
        } else {
            stringResource(
                R.string.match_steps_to_lead,
                format.format(snapshot.match.stepsToLead),
                snapshot.winStreak,
            )
        }
    }

private fun motionDuration(level: MotionLevel): Int = when (level) {
    MotionLevel.FULL -> StepArenaMotion.expressive
    MotionLevel.REDUCED -> StepArenaMotion.quick
    MotionLevel.OFF -> 0
}
