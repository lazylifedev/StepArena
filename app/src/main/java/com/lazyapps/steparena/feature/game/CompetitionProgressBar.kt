package com.lazyapps.steparena.feature.game

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.lazyapps.steparena.core.designsystem.motion.MotionLevel
import com.lazyapps.steparena.core.designsystem.theme.StepArenaColors
import com.lazyapps.steparena.core.designsystem.theme.StepArenaMotion
import com.lazyapps.steparena.game.competitionProgress

@Composable
internal fun CompetitionProgressBar(
    steps: Long,
    baseColor: Color,
    motionLevel: MotionLevel,
    modifier: Modifier = Modifier,
) {
    val progress = competitionProgress(steps)
    val animatedCurrentProgress by animateFloatAsState(
        targetValue = progress.currentBandProgress,
        animationSpec = tween(
            durationMillis = when (motionLevel) {
                MotionLevel.FULL -> StepArenaMotion.expressive
                MotionLevel.REDUCED -> StepArenaMotion.quick
                MotionLevel.OFF -> 0
            },
            easing = StepArenaMotion.emphasized,
        ),
        label = "competitionProgress",
    )
    val shape = RoundedCornerShape(50)

    Box(
        modifier = modifier
            .height(10.dp)
            .clip(shape)
            .background(StepArenaColors.Gray800)
            .semantics {
                contentDescription = "${progress.displaySteps} steps, band ${progress.currentBand + 1}"
            },
    ) {
        // Each later layer is painted from the left over the previous full
        // band, matching the 10,000-step lap model.
        for (band in 0..progress.currentBand) {
            val fill = if (band < progress.currentBand) 1f else animatedCurrentProgress
            Box(
                modifier = Modifier
                    .fillMaxWidth(fill)
                    .fillMaxHeight()
                    .background(competitionBandColor(baseColor, band)),
            )
        }
    }
}

private fun competitionBandColor(baseColor: Color, band: Int): Color =
    androidx.compose.ui.graphics.lerp(
        baseColor,
        StepArenaColors.Emerald,
        (band.coerceIn(0, 9) / 9f) * 0.68f,
    )
