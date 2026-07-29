package com.lazyapps.steparena.core.designsystem.component

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.animateIntAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.unit.dp
import com.lazyapps.steparena.core.designsystem.motion.MotionLevel
import com.lazyapps.steparena.core.designsystem.theme.StepArenaColors
import com.lazyapps.steparena.core.designsystem.theme.StepArenaElevation
import com.lazyapps.steparena.core.designsystem.theme.StepArenaGlow
import com.lazyapps.steparena.core.designsystem.theme.StepArenaMotion
import com.lazyapps.steparena.core.designsystem.theme.StepArenaSpacing

@Composable
fun StepArenaBackground(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    Box(
        modifier = modifier.background(
            Brush.linearGradient(
                colors = listOf(
                    StepArenaColors.Navy950,
                    StepArenaColors.Navy900,
                    StepArenaColors.BlueBlack,
                ),
                start = Offset.Zero,
                end = Offset(900f, 1_600f),
            ),
        ),
    ) {
        content()
    }
}

@Composable
fun GlassSurface(
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    Surface(
        modifier = modifier
            .shadow(StepArenaElevation.floating, MaterialTheme.shapes.large)
            .border(1.dp, StepArenaColors.Outline, MaterialTheme.shapes.large),
        color = StepArenaColors.Glass,
        contentColor = StepArenaColors.White,
        shape = MaterialTheme.shapes.large,
    ) {
        Column(
            modifier = Modifier.padding(StepArenaSpacing.md),
            content = content,
        )
    }
}

@Composable
fun MetricCard(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    supportingText: String? = null,
) {
    GlassSurface(modifier) {
        Text(label, style = MaterialTheme.typography.bodyMedium, color = StepArenaColors.TextSecondary)
        Spacer(Modifier.height(StepArenaSpacing.xs))
        Text(value, style = MaterialTheme.typography.titleLarge)
        supportingText?.let {
            Spacer(Modifier.height(StepArenaSpacing.xxs))
            Text(it, style = MaterialTheme.typography.bodyMedium, color = StepArenaColors.TextSecondary)
        }
    }
}

@Composable
fun TrackingStatusChip(
    text: String,
    isHealthy: Boolean,
    modifier: Modifier = Modifier,
    motionLevel: MotionLevel = MotionLevel.FULL,
) {
    val statusColor = if (isHealthy) StepArenaColors.Emerald else StepArenaColors.Amber
    val animatedStatusColor by animateColorAsState(
        targetValue = statusColor,
        animationSpec = tween(motionDuration(motionLevel)),
        label = "trackingStatusColor",
    )
    Row(
        modifier = modifier
            .clip(CircleShape)
            .background(animatedStatusColor.copy(alpha = 0.14f))
            .border(1.dp, animatedStatusColor.copy(alpha = 0.5f), CircleShape)
            .padding(horizontal = StepArenaSpacing.sm, vertical = StepArenaSpacing.xs)
            .semantics { contentDescription = text },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(8.dp).background(animatedStatusColor, CircleShape))
        Spacer(Modifier.width(StepArenaSpacing.xs))
        Text(text, style = MaterialTheme.typography.labelLarge, color = animatedStatusColor)
    }
}

@Composable
fun RankBadge(text: String, modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier.shadow(StepArenaElevation.resting, CircleShape),
        color = StepArenaGlow.active,
        contentColor = StepArenaColors.CyanSoft,
        shape = CircleShape,
    ) {
        Text(
            text = text,
            modifier = Modifier.padding(horizontal = StepArenaSpacing.md, vertical = StepArenaSpacing.xs),
            style = MaterialTheme.typography.labelLarge,
        )
    }
}

@Composable
fun RankProgressBar(
    progress: Float,
    description: String,
    modifier: Modifier = Modifier,
    motionLevel: MotionLevel = MotionLevel.FULL,
) {
    val duration = when (motionLevel) {
        MotionLevel.FULL -> StepArenaMotion.expressive
        MotionLevel.REDUCED -> StepArenaMotion.quick
        MotionLevel.OFF -> 0
    }
    val animated by animateFloatAsState(
        targetValue = progress.coerceIn(0f, 1f),
        animationSpec = tween(duration, easing = StepArenaMotion.emphasized),
        label = "rankProgress",
    )
    androidx.compose.material3.LinearProgressIndicator(
        progress = { animated },
        modifier = modifier
            .fillMaxWidth()
            .height(8.dp)
            .clip(CircleShape)
            .semantics { contentDescription = description },
        color = StepArenaColors.Violet,
        trackColor = StepArenaColors.Gray800,
        strokeCap = StrokeCap.Round,
    )
}

@Composable
fun AnimatedMetricValue(
    value: Int,
    formattedValue: (Int) -> String,
    description: String,
    motionLevel: MotionLevel,
    modifier: Modifier = Modifier,
) {
    val duration = when (motionLevel) {
        MotionLevel.FULL -> StepArenaMotion.expressive
        MotionLevel.REDUCED -> StepArenaMotion.quick
        MotionLevel.OFF -> 0
    }
    val animated by animateIntAsState(
        targetValue = value.coerceAtLeast(0),
        animationSpec = tween(duration, easing = StepArenaMotion.emphasized),
        label = "metricValue",
    )
    Text(
        text = formattedValue(animated),
        modifier = modifier.semantics { contentDescription = description },
        style = MaterialTheme.typography.displaySmall,
    )
}

@Composable
fun StepProgressRing(
    progress: Float,
    description: String,
    modifier: Modifier = Modifier,
    motionLevel: MotionLevel = MotionLevel.FULL,
    content: @Composable () -> Unit,
) {
    val duration = when (motionLevel) {
        MotionLevel.FULL -> StepArenaMotion.expressive
        MotionLevel.REDUCED -> StepArenaMotion.quick
        MotionLevel.OFF -> 0
    }
    val animated by animateFloatAsState(
        targetValue = progress.coerceIn(0f, 1f),
        animationSpec = tween(duration, easing = StepArenaMotion.emphasized),
        label = "stepProgress",
    )
    Box(
        modifier = modifier.semantics {
            contentDescription = description
            progressBarRangeInfo = androidx.compose.ui.semantics.ProgressBarRangeInfo(
                current = animated,
                range = 0f..1f,
            )
        },
        contentAlignment = Alignment.Center,
    ) {
        Canvas(Modifier.fillMaxSize()) {
            val stroke = Stroke(width = 16.dp.toPx(), cap = StrokeCap.Round)
            drawArc(
                color = StepArenaColors.Gray800,
                startAngle = -90f,
                sweepAngle = 360f,
                useCenter = false,
                style = stroke,
                size = Size(size.width, size.height),
            )
            drawArc(
                brush = Brush.sweepGradient(listOf(StepArenaColors.Cyan, StepArenaColors.Violet)),
                startAngle = -90f,
                sweepAngle = animated * 360f,
                useCenter = false,
                style = stroke,
                size = Size(size.width, size.height),
            )
        }
        content()
    }
}

@Composable
fun PrimaryActionButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    Button(
        onClick = onClick,
        modifier = modifier
            .fillMaxWidth()
            .height(56.dp),
        enabled = enabled,
        shape = MaterialTheme.shapes.medium,
        colors = ButtonDefaults.buttonColors(
            containerColor = StepArenaColors.Cyan,
            contentColor = StepArenaColors.Navy950,
        ),
        elevation = ButtonDefaults.buttonElevation(
            defaultElevation = StepArenaElevation.resting,
            pressedElevation = StepArenaElevation.prominent,
        ),
    ) {
        Text(text, style = MaterialTheme.typography.titleMedium)
    }
}

@Composable
fun MatchCard(
    title: String,
    opponent: String,
    selfLabel: String,
    opponentLabel: String,
    selfProgress: Float,
    opponentProgress: Float,
    supportingText: String,
    modifier: Modifier = Modifier,
    motionLevel: MotionLevel = MotionLevel.FULL,
    expandedText: String? = null,
    expanded: Boolean = false,
    onClick: () -> Unit = {},
) {
    GlassSurface(modifier.clickable(onClick = onClick)) {
        SectionHeader(title)
        Spacer(Modifier.height(StepArenaSpacing.md))
        Text(opponent, style = MaterialTheme.typography.titleLarge)
        Spacer(Modifier.height(StepArenaSpacing.sm))
        ProgressLine(selfLabel, selfProgress, StepArenaColors.Cyan, motionLevel)
        Spacer(Modifier.height(StepArenaSpacing.xs))
        ProgressLine(opponentLabel, opponentProgress, StepArenaColors.Violet, motionLevel)
        Spacer(Modifier.height(StepArenaSpacing.sm))
        Text(supportingText, style = MaterialTheme.typography.bodyMedium, color = StepArenaColors.TextSecondary)
        AnimatedVisibility(
            visible = expanded && expandedText != null,
            enter = fadeIn(tween(motionDuration(motionLevel))),
            exit = fadeOut(tween(motionDuration(motionLevel))),
        ) {
            Text(
                text = expandedText.orEmpty(),
                modifier = Modifier.padding(top = StepArenaSpacing.sm),
                style = MaterialTheme.typography.bodyMedium,
                color = StepArenaColors.CyanSoft,
            )
        }
    }
}

@Composable
private fun ProgressLine(
    label: String,
    progress: Float,
    color: Color,
    motionLevel: MotionLevel,
) {
    val animatedProgress by animateFloatAsState(
        targetValue = progress.coerceIn(0f, 1f),
        animationSpec = tween(
            durationMillis = motionDuration(motionLevel),
            easing = StepArenaMotion.emphasized,
        ),
        label = "matchProgress",
    )
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(label, modifier = Modifier.width(72.dp), style = MaterialTheme.typography.bodyMedium)
        androidx.compose.material3.LinearProgressIndicator(
            progress = { animatedProgress },
            modifier = Modifier.weight(1f).height(7.dp).clip(CircleShape),
            color = color,
            trackColor = StepArenaColors.Gray800,
        )
        Spacer(Modifier.width(StepArenaSpacing.xs))
        Text("${(animatedProgress * 100).toInt()}%", style = MaterialTheme.typography.labelLarge)
    }
}

@Composable
fun LeagueSummaryCard(
    title: String,
    position: String,
    supportingText: String,
    modifier: Modifier = Modifier,
) {
    GlassSurface(modifier) {
        SectionHeader(title)
        Spacer(Modifier.height(StepArenaSpacing.sm))
        Text(position, style = MaterialTheme.typography.headlineMedium, color = StepArenaColors.CyanSoft)
        Text(supportingText, style = MaterialTheme.typography.bodyMedium, color = StepArenaColors.TextSecondary)
    }
}

@Composable
fun SectionHeader(text: String, modifier: Modifier = Modifier) {
    Text(text, modifier = modifier, style = MaterialTheme.typography.titleLarge)
}

@Composable
fun EmptyState(title: String, message: String, modifier: Modifier = Modifier) {
    StatePanel(title, message, modifier)
}

@Composable
fun ErrorState(
    title: String,
    message: String,
    retryText: String,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
) {
    StatePanel(title, message, modifier) {
        Spacer(Modifier.height(StepArenaSpacing.md))
        PrimaryActionButton(retryText, onRetry)
    }
}

@Composable
fun LoadingState(description: String, modifier: Modifier = Modifier) {
    Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        CircularProgressIndicator(
            color = StepArenaColors.Cyan,
            modifier = Modifier.semantics { contentDescription = description },
        )
    }
}

@Composable
private fun StatePanel(
    title: String,
    message: String,
    modifier: Modifier,
    content: @Composable ColumnScope.() -> Unit = {},
) {
    Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        GlassSurface(Modifier.fillMaxWidth()) {
            Text(title, style = MaterialTheme.typography.headlineMedium)
            Spacer(Modifier.height(StepArenaSpacing.xs))
            Text(message, color = StepArenaColors.TextSecondary)
            content()
        }
    }
}

private fun motionDuration(level: MotionLevel): Int = when (level) {
    MotionLevel.FULL -> StepArenaMotion.standard
    MotionLevel.REDUCED -> StepArenaMotion.quick
    MotionLevel.OFF -> 0
}
