package com.lazyapps.steparena.feature.game

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.DirectionsWalk
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Flag
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.lazyapps.steparena.R
import com.lazyapps.steparena.core.designsystem.component.GlassSurface
import com.lazyapps.steparena.core.designsystem.motion.MotionLevel
import com.lazyapps.steparena.core.designsystem.theme.StepArenaColors
import com.lazyapps.steparena.core.designsystem.theme.StepArenaMotion
import com.lazyapps.steparena.core.designsystem.theme.StepArenaSpacing
import com.lazyapps.steparena.game.MatchOutcome
import com.lazyapps.steparena.game.MatchStatus
import kotlinx.coroutines.delay

object ChallengeTestTags {
    const val COMPARISON = "challenge_comparison"
    const val INFO = "challenge_info"
    const val INFO_SHEET = "challenge_info_sheet"
    const val TOTAL_BREAKDOWN = "challenge_total_breakdown"
    const val USER_PROGRESS = "challenge_user_progress"
    const val PARTNER_PROGRESS = "challenge_partner_progress"
    const val GOAL_FLAG = "challenge_goal_flag"
    const val CELEBRATION = "challenge_celebration"
}

@Composable
internal fun MatchPage(
    state: GameUiState,
    motionLevel: MotionLevel = MotionLevel.FULL,
    onChallengeObserved: (String, Long, Long) -> Unit = { _, _, _ -> },
    onCelebrationConsumed: (String) -> Unit = {},
) {
    var showInformation by rememberSaveable { mutableStateOf(false) }
    val match = state.todayMatch
    val comparison = match?.let {
        challengeComparison(
            current = currentChallengeSteps(it, state.currentMeasuredSteps),
            healthConnectAddedSteps = state.currentHealthConnectAddedSteps,
            partnerTargetSteps = it.opponentTargetSteps,
        )
    }

    if (match != null && comparison != null) {
        LaunchedEffect(match.id, comparison.eligibleSteps, comparison.partnerTargetSteps) {
            onChallengeObserved(
                match.id,
                comparison.eligibleSteps,
                comparison.partnerTargetSteps,
            )
        }
        val celebration = state.challengeCelebration?.takeIf { it.matchId == match.id }
        LaunchedEffect(celebration, motionLevel) {
            if (celebration != null) {
                delay(
                    when (motionLevel) {
                        MotionLevel.FULL -> 700L
                        MotionLevel.REDUCED -> 220L
                        MotionLevel.OFF -> 0L
                    },
                )
                onCelebrationConsumed(celebration.matchId)
            }
        }
    }

    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(StepArenaSpacing.md),
        verticalArrangement = Arrangement.spacedBy(StepArenaSpacing.md),
    ) {
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    stringResource(R.string.game_today_title),
                    style = MaterialTheme.typography.headlineMedium,
                    modifier = Modifier.weight(1f).semantics { heading() },
                )
                IconButton(
                    onClick = { showInformation = true },
                    modifier = Modifier
                        .size(StepArenaSpacing.minimumTouchTarget)
                        .testTag(ChallengeTestTags.INFO),
                ) {
                    Icon(
                        Icons.Default.Info,
                        contentDescription = stringResource(R.string.game_information_action),
                    )
                }
            }
        }
        if (match != null && comparison != null) {
            item {
                ChallengeComparisonCard(
                    comparison = comparison,
                    celebration = state.challengeCelebration?.takeIf { it.matchId == match.id },
                    motionLevel = motionLevel,
                    onInformation = { showInformation = true },
                )
            }
        } else {
            item {
                Text(
                    stringResource(R.string.game_today_loading),
                    modifier = Modifier.testTag(GameTestTags.EMPTY),
                )
            }
        }
        item {
            Text(
                stringResource(R.string.game_past_title),
                style = MaterialTheme.typography.titleLarge,
            )
        }
        val finalized = state.recentMatches.filter { it.status == MatchStatus.FINALIZED }
        if (finalized.isEmpty()) {
            item { Text(stringResource(R.string.game_past_empty)) }
        }
        items(finalized) { pastMatch ->
            GlassSurface(Modifier.fillMaxWidth()) {
                Text(
                    formatDate(pastMatch.localDate),
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.semantics { heading() },
                )
                Text(
                    stringResource(
                        (pastMatch.outcome ?: MatchOutcome.IN_PROGRESS).displayNameRes(),
                    ),
                    style = MaterialTheme.typography.titleMedium,
                )
                Text(stringResource(R.string.game_user_steps, formatNumber(pastMatch.eligibleUserSteps)))
                Text(
                    stringResource(
                        R.string.game_partner_target,
                        formatNumber(pastMatch.opponentTargetSteps),
                    ),
                )
                Text(
                    stringResource(
                        R.string.game_rating_change,
                        formatNumber(pastMatch.ratingBefore),
                        pastMatch.ratingAfter?.let(::formatNumber)
                            ?: stringResource(R.string.records_no_value),
                    ),
                )
            }
        }
    }

    if (showInformation) {
        ChallengeInformationSheet(
            healthConnectAddedSteps = comparison?.healthConnectAddedSteps ?: 0,
            onDismiss = { showInformation = false },
        )
    }
}

@Composable
private fun ChallengeComparisonCard(
    comparison: ChallengeComparison,
    celebration: ChallengeCelebration?,
    motionLevel: MotionLevel,
    onInformation: () -> Unit,
) {
    val userSteps = formatNumber(comparison.eligibleSteps)
    val partnerSteps = formatNumber(comparison.partnerTargetSteps)
    val remainingSteps = formatNumber(comparison.remainingSteps)
    val accessibility = if (comparison.goalAchieved) {
        stringResource(R.string.game_challenge_accessibility_achieved, userSteps, partnerSteps)
    } else {
        stringResource(
            R.string.game_challenge_accessibility_remaining,
            userSteps,
            partnerSteps,
            remainingSteps,
        )
    }
    val celebrationActive = celebration != null && motionLevel != MotionLevel.OFF
    val glow by animateFloatAsState(
        targetValue = if (celebrationActive) 1f else 0f,
        animationSpec = tween(
            durationMillis = when (motionLevel) {
                MotionLevel.FULL -> StepArenaMotion.standard
                MotionLevel.REDUCED -> StepArenaMotion.quick
                MotionLevel.OFF -> 0
            },
            easing = StepArenaMotion.emphasized,
        ),
        label = "challengeGlow",
    )
    val configuration = LocalConfiguration.current
    val compactTypography = configuration.fontScale >= 1.5f

    GlassSurface(
        Modifier
            .fillMaxWidth()
            .testTag(ChallengeTestTags.COMPARISON)
            .then(
                if (celebrationActive) Modifier.testTag(ChallengeTestTags.CELEBRATION)
                else Modifier,
            )
            .drawBehind {
                if (glow > 0f) {
                    drawRoundRect(
                        color = StepArenaColors.Emerald.copy(alpha = 0.18f * glow),
                        cornerRadius = CornerRadius(26.dp.toPx()),
                    )
                }
            }
            .semantics { contentDescription = accessibility },
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(StepArenaSpacing.sm),
        ) {
            ParticipantProgress(
                label = stringResource(R.string.game_you),
                steps = userSteps,
                progress = comparison.eligibleSteps.toFloat()
                    .div(comparison.partnerTargetSteps)
                    .coerceIn(0f, 1f),
                color = StepArenaColors.Cyan,
                isUser = true,
                compactTypography = compactTypography,
                motionLevel = motionLevel,
                modifier = Modifier.weight(1f),
            )
            Icon(
                Icons.Default.Flag,
                contentDescription = null,
                tint = if (comparison.goalAchieved) {
                    StepArenaColors.Emerald
                } else {
                    StepArenaColors.TextSecondary
                },
                modifier = Modifier
                    .size(36.dp)
                    .testTag(ChallengeTestTags.GOAL_FLAG)
                    .graphicsLayer {
                        val pulse = if (motionLevel == MotionLevel.FULL) glow * 0.12f else 0f
                        scaleX = 1f + pulse
                        scaleY = 1f + pulse
                    },
            )
            ParticipantProgress(
                label = stringResource(R.string.game_partner),
                steps = partnerSteps,
                progress = 1f,
                color = StepArenaColors.Violet,
                isUser = false,
                compactTypography = compactTypography,
                motionLevel = motionLevel,
                modifier = Modifier.weight(1f),
            )
        }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = StepArenaSpacing.md)
                .clearAndSetSemantics { },
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                if (comparison.goalAchieved) Icons.Default.CheckCircle else Icons.Default.Flag,
                contentDescription = null,
                tint = if (comparison.goalAchieved) StepArenaColors.Emerald else StepArenaColors.Cyan,
            )
            Spacer(Modifier.width(StepArenaSpacing.xs))
            Text(
                if (comparison.goalAchieved) {
                    stringResource(R.string.game_goal_achieved_compact)
                } else {
                    stringResource(R.string.game_steps_remaining, remainingSteps)
                },
                style = MaterialTheme.typography.titleLarge,
                color = if (comparison.goalAchieved) StepArenaColors.Emerald else StepArenaColors.White,
            )
        }
        if (comparison.showsTotalBreakdown) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 48.dp)
                    .testTag(ChallengeTestTags.TOTAL_BREAKDOWN)
                    .clickable(onClick = onInformation)
                    .semantics { role = Role.Button }
                    .padding(top = StepArenaSpacing.sm),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    stringResource(
                        R.string.game_total_eligible_compact,
                        formatNumber(comparison.totalSteps),
                        userSteps,
                    ),
                    style = MaterialTheme.typography.bodyMedium,
                    color = StepArenaColors.TextSecondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.width(StepArenaSpacing.xs))
                Icon(
                    Icons.Default.Info,
                    contentDescription = stringResource(R.string.game_information_action),
                    modifier = Modifier.size(18.dp),
                    tint = StepArenaColors.CyanSoft,
                )
            }
        }
        if (comparison.eligibleSteps >= 30_000) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag(GameTestTags.HEALTH_CAP)
                    .clickable(onClick = onInformation)
                    .padding(vertical = StepArenaSpacing.xs),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    Icons.Default.Info,
                    contentDescription = null,
                    tint = StepArenaColors.Amber,
                )
                Spacer(Modifier.width(StepArenaSpacing.xs))
                Text(
                    stringResource(R.string.game_health_cap_compact),
                    color = StepArenaColors.Amber,
                )
            }
        }
    }
}

@Composable
private fun ParticipantProgress(
    label: String,
    steps: String,
    progress: Float,
    color: Color,
    isUser: Boolean,
    compactTypography: Boolean,
    motionLevel: MotionLevel,
    modifier: Modifier = Modifier,
) {
    val animatedProgress by animateFloatAsState(
        targetValue = progress,
        animationSpec = tween(
            durationMillis = when (motionLevel) {
                MotionLevel.FULL -> StepArenaMotion.expressive
                MotionLevel.REDUCED -> StepArenaMotion.quick
                MotionLevel.OFF -> 0
            },
            easing = StepArenaMotion.emphasized,
        ),
        label = if (isUser) "challengeUserProgress" else "challengePartnerProgress",
    )
    Column(
        modifier = modifier.clearAndSetSemantics { },
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(StepArenaSpacing.xs),
    ) {
        Surface(
            shape = MaterialTheme.shapes.extraLarge,
            color = color.copy(alpha = 0.14f),
            contentColor = color,
        ) {
            Icon(
                if (isUser) Icons.AutoMirrored.Filled.DirectionsWalk else Icons.Default.Person,
                contentDescription = null,
                modifier = Modifier.padding(StepArenaSpacing.xs).size(28.dp),
            )
        }
        Text(
            label,
            style = MaterialTheme.typography.labelLarge,
            color = color,
            maxLines = 1,
        )
        Text(
            steps,
            style = if (compactTypography) {
                MaterialTheme.typography.titleMedium
            } else {
                MaterialTheme.typography.headlineMedium
            },
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
            maxLines = 1,
        )
        LinearProgressIndicator(
            progress = { animatedProgress },
            modifier = Modifier
                .fillMaxWidth()
                .clip(MaterialTheme.shapes.extraLarge)
                .testTag(
                    if (isUser) {
                        ChallengeTestTags.USER_PROGRESS
                    } else {
                        ChallengeTestTags.PARTNER_PROGRESS
                    },
                ),
            color = color,
            trackColor = StepArenaColors.Gray800,
            strokeCap = StrokeCap.Round,
        )
    }
}

@Composable
@OptIn(ExperimentalMaterial3Api::class)
private fun ChallengeInformationSheet(
    healthConnectAddedSteps: Long,
    onDismiss: () -> Unit,
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        modifier = Modifier.testTag(ChallengeTestTags.INFO_SHEET),
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(
                start = StepArenaSpacing.lg,
                end = StepArenaSpacing.lg,
                bottom = StepArenaSpacing.xl,
            ),
            verticalArrangement = Arrangement.spacedBy(StepArenaSpacing.md),
        ) {
            Text(
                stringResource(R.string.game_information_title),
                style = MaterialTheme.typography.headlineMedium,
                modifier = Modifier.semantics { heading() },
            )
            Text(stringResource(R.string.game_today_explanation))
            Text(
                if (healthConnectAddedSteps > 0) {
                    stringResource(
                        R.string.game_health_connect_steps,
                        formatNumber(healthConnectAddedSteps),
                    )
                } else {
                    stringResource(R.string.game_health_connect_policy)
                },
            )
            Text(stringResource(R.string.game_health_connect_reason))
            Text(stringResource(R.string.game_health_cap))
            TextButton(
                onClick = onDismiss,
                modifier = Modifier.align(Alignment.End),
            ) {
                Text(stringResource(R.string.common_close))
            }
        }
    }
}
