package com.lazyapps.steparena.feature.home

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.tween
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
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
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.TextButton
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.DirectionsWalk
import androidx.compose.material.icons.filled.HealthAndSafety
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.PauseCircle
import androidx.compose.material.icons.filled.PlayCircle
import androidx.compose.material.icons.filled.Smartphone
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.Role
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
import com.lazyapps.steparena.core.designsystem.component.RankBadge
import com.lazyapps.steparena.core.designsystem.component.RankProgressBar
import com.lazyapps.steparena.core.designsystem.component.SectionHeader
import com.lazyapps.steparena.core.designsystem.component.StepProgressRing
import com.lazyapps.steparena.core.designsystem.component.TrackingStatusChip
import com.lazyapps.steparena.core.designsystem.motion.MotionLevel
import com.lazyapps.steparena.core.designsystem.theme.StepArenaColors
import com.lazyapps.steparena.core.designsystem.theme.StepArenaMotion
import com.lazyapps.steparena.core.designsystem.theme.StepArenaSpacing
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
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Locale

object HomeTestTags {
    const val CONTENT = "home_content"
    const val START_BUTTON = "start_session_button"
    const val STOP_TRACKING_BUTTON = "stop_tracking_button"
    const val MANUAL_SESSION = "manual_session"
    const val TRACKING_STATUS = "tracking_status"
    const val HEALTH_BREAKDOWN = "health_breakdown"
    const val HEALTH_SHEET = "health_sheet"
    const val WALKING_INFO = "walking_info"
    const val WALKING_INFO_SHEET = "walking_info_sheet"
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
    var showHealthBreakdown by rememberSaveable { mutableStateOf(false) }
    var showWalkingInfo by rememberSaveable { mutableStateOf(false) }
    var showTrackingDetails by rememberSaveable { mutableStateOf(false) }
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
            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
                Text(
                    remember(locale, uiState.zoneId) {
                        DateTimeFormatter.ofLocalizedDate(FormatStyle.FULL)
                            .withLocale(locale)
                    }.format(uiState.localDate),
                    modifier = Modifier.semantics { heading() },
                    style = MaterialTheme.typography.labelLarge,
                    color = StepArenaColors.TextSecondary,
                )
            }
        }
        item {
            StepsPanel(
                snapshot = snapshot,
                goalProgress = goalProgress,
                numberFormat = numberFormat,
                motionLevel = uiState.motionLevel,
                onHealthClick = { showHealthBreakdown = true },
            )
        }
        item {
            TrackingPanel(
                snapshot = snapshot,
                motionLevel = uiState.motionLevel,
                onClick = {
                    if (snapshot.trackingStatus == TrackingStatus.ACTIVE) {
                        showTrackingDetails = true
                    } else {
                        onAction(HomeAction.OpenDiagnostics)
                    }
                },
            )
        }
        item {
            WalkingQuickAction(
                uiState = uiState,
                numberFormat = numberFormat,
                onInfoClick = { showWalkingInfo = true },
                onAction = onAction,
            )
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
    if (showHealthBreakdown) {
        HealthBreakdownSheet(
            snapshot = snapshot,
            numberFormat = numberFormat,
            onDismiss = { showHealthBreakdown = false },
        )
    }
    if (showWalkingInfo) {
        InformationSheet(
            title = stringResource(R.string.home_walking_record_title),
            body = stringResource(R.string.home_walking_record_explanation),
            tag = HomeTestTags.WALKING_INFO_SHEET,
            onDismiss = { showWalkingInfo = false },
        )
    }
    if (showTrackingDetails) {
        InformationSheet(
            title = stringResource(R.string.tracking_active),
            body = stringResource(R.string.home_tracking_detail),
            tag = HomeTestTags.TRACKING_STATUS,
            onDismiss = { showTrackingDetails = false },
        )
    }
}

@Composable
private fun TrackingPanel(
    snapshot: HomeSnapshot,
    motionLevel: MotionLevel,
    onClick: () -> Unit,
) {
    val healthy = snapshot.trackingStatus == TrackingStatus.ACTIVE
    GlassSurface(
        Modifier
            .fillMaxWidth()
            .testTag(HomeTestTags.TRACKING_STATUS)
            .clickable(onClick = onClick),
    ) {
        TrackingStatusChip(
            text = if (snapshot.trackingStatus == TrackingStatus.MAY_BE_STOPPED) {
                stringResource(R.string.home_tracking_check)
            } else {
                trackingText(snapshot.trackingStatus)
            },
            isHealthy = healthy,
            motionLevel = motionLevel,
        )
        if (!healthy) {
            Text(
                stringResource(R.string.home_tracking_tap_for_details),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.error,
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
    onHealthClick: () -> Unit,
) {
    val healthBreakdownAccessibility = stringResource(
        R.string.home_health_breakdown_accessibility,
        numberFormat.format(snapshot.recoveredSteps),
    )
    GlassSurface(Modifier.fillMaxWidth()) {
        Text(stringResource(R.string.today_steps), color = StepArenaColors.TextSecondary)
        Spacer(Modifier.height(StepArenaSpacing.sm))
        Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
            StepProgressRing(
                progress = goalProgress,
                description = if (snapshot.recoveredSteps > 0) {
                    stringResource(
                        R.string.home_steps_accessibility_with_health,
                        numberFormat.format(snapshot.metrics.steps),
                        numberFormat.format(snapshot.measuredSteps),
                        numberFormat.format(snapshot.recoveredSteps),
                        (goalProgress * 100).toInt(),
                    )
                } else {
                    stringResource(
                        R.string.home_steps_accessibility,
                        numberFormat.format(snapshot.metrics.steps),
                        (goalProgress * 100).toInt(),
                    )
                },
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
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(StepArenaSpacing.sm),
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Smartphone, null, Modifier.size(16.dp))
                            Text(
                                numberFormat.format(snapshot.measuredSteps),
                                style = MaterialTheme.typography.bodySmall,
                                color = StepArenaColors.TextSecondary,
                            )
                        }
                        if (snapshot.recoveredSteps > 0) {
                            Row(
                                modifier = Modifier
                                    .testTag(HomeTestTags.HEALTH_BREAKDOWN)
                                    .clickable(onClick = onHealthClick)
                                    .semantics {
                                        role = Role.Button
                                        contentDescription = healthBreakdownAccessibility
                                    },
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Icon(Icons.Default.HealthAndSafety, null, Modifier.size(16.dp))
                            Text(
                                    "+${numberFormat.format(snapshot.recoveredSteps)}",
                                style = MaterialTheme.typography.bodySmall,
                                    color = StepArenaColors.CyanSoft,
                            )
                        }
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
private fun WalkingQuickAction(
    uiState: HomeUiState,
    numberFormat: NumberFormat,
    onInfoClick: () -> Unit,
    onAction: (HomeAction) -> Unit,
) {
    val haptics = LocalHapticFeedback.current
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val manual = uiState.manualSession
    val active = uiState.sessionState == SessionState.MANUAL_WALK && manual != null
    val pulse = rememberInfiniteTransition(label = "walkingPulse")
    val breathing by pulse.animateFloat(
        initialValue = 1f,
        targetValue = if (active && uiState.motionLevel == MotionLevel.FULL) 1.05f else 1f,
        animationSpec = infiniteRepeatable(tween(900), RepeatMode.Reverse),
        label = "walkingBreathing",
    )
    val action: () -> Unit = {
        haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
        when (uiState.sessionState) {
            SessionState.TRACKING_STOPPED -> onAction(HomeAction.StartSession)
            SessionState.TRACKING -> onAction(HomeAction.StartManualWalk)
            SessionState.MANUAL_WALK -> if (manual != null) {
                onAction(HomeAction.EndManualWalk(manual.id))
            } else {
                Unit
            }
        }
    }
    GlassSurface(
        Modifier
            .fillMaxWidth()
            .testTag(HomeTestTags.START_BUTTON)
            .scale(if (pressed) 0.98f else breathing)
            .semantics { role = Role.Button }
            .clickable(
                enabled = uiState.sensorSupported,
                interactionSource = interaction,
                indication = null,
                onClick = action,
            ),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Icon(
                Icons.AutoMirrored.Filled.DirectionsWalk,
                contentDescription = null,
                tint = if (active) StepArenaColors.Emerald else StepArenaColors.CyanSoft,
                modifier = Modifier.size(36.dp),
            )
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = StepArenaSpacing.sm)
                    .then(if (active) Modifier.testTag(HomeTestTags.MANUAL_SESSION) else Modifier),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                if (active) {
                    Text(ActivityFormatter.duration(manual!!.elapsedSeconds), style = MaterialTheme.typography.titleLarge)
                    Text(
                        stringResource(R.string.home_manual_steps, numberFormat.format(manual.steps)),
                        color = StepArenaColors.TextSecondary,
                    )
                } else {
                    Text(
                        if (uiState.sessionState == SessionState.TRACKING_STOPPED) {
                            stringResource(R.string.home_start_tracking)
                        } else {
                            stringResource(R.string.home_walking_record_title)
                        },
                        style = MaterialTheme.typography.titleMedium,
                    )
                }
            }
            Icon(
                if (active) Icons.Default.PauseCircle else Icons.Default.PlayCircle,
                contentDescription = if (active) {
                    stringResource(R.string.home_end_walk)
                } else {
                    stringResource(R.string.home_start_walk)
                },
                tint = StepArenaColors.CyanSoft,
                modifier = Modifier.size(36.dp),
            )
            IconButton(
                onClick = onInfoClick,
                modifier = Modifier.testTag(HomeTestTags.WALKING_INFO),
            ) {
                Icon(Icons.Default.Info, stringResource(R.string.home_show_information))
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun HealthBreakdownSheet(
    snapshot: HomeSnapshot,
    numberFormat: NumberFormat,
    onDismiss: () -> Unit,
) {
    ModalBottomSheet(onDismissRequest = onDismiss, modifier = Modifier.testTag(HomeTestTags.HEALTH_SHEET)) {
        Column(
            Modifier.fillMaxWidth().padding(StepArenaSpacing.lg),
            verticalArrangement = Arrangement.spacedBy(StepArenaSpacing.md),
        ) {
            Text(stringResource(R.string.today_steps), style = MaterialTheme.typography.headlineMedium)
            BreakdownRow(stringResource(R.string.home_measured_label), numberFormat.format(snapshot.measuredSteps))
            BreakdownRow(
                stringResource(R.string.home_health_connect_label),
                "+${numberFormat.format(snapshot.recoveredSteps)}",
            )
            BreakdownRow(stringResource(R.string.home_total_label), numberFormat.format(snapshot.metrics.steps))
            Text(stringResource(R.string.home_health_connect_detail), color = StepArenaColors.TextSecondary)
        }
    }
}

@Composable
private fun BreakdownRow(label: String, value: String) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label)
        Text(value, style = MaterialTheme.typography.titleMedium)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun InformationSheet(title: String, body: String, tag: String, onDismiss: () -> Unit) {
    ModalBottomSheet(onDismissRequest = onDismiss, modifier = Modifier.testTag(tag)) {
        Column(
            Modifier.fillMaxWidth().padding(StepArenaSpacing.lg),
            verticalArrangement = Arrangement.spacedBy(StepArenaSpacing.sm),
        ) {
            Text(title, style = MaterialTheme.typography.headlineSmall)
            Text(body, color = StepArenaColors.TextSecondary)
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
