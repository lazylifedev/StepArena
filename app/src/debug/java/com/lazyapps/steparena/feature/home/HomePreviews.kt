package com.lazyapps.steparena.feature.home

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Density
import com.lazyapps.steparena.core.designsystem.motion.MotionLevel
import com.lazyapps.steparena.core.designsystem.theme.StepArenaTheme
import com.lazyapps.steparena.core.model.ActivityMetrics
import com.lazyapps.steparena.core.model.DailyMatch
import com.lazyapps.steparena.core.model.DataReliability
import com.lazyapps.steparena.core.model.HomeSnapshot
import com.lazyapps.steparena.core.model.LeagueStatus
import com.lazyapps.steparena.core.model.MatchOutcome
import com.lazyapps.steparena.core.model.RankStatus
import com.lazyapps.steparena.core.model.RankTier
import com.lazyapps.steparena.core.model.TrackingStatus
import java.time.Instant

@Preview(name = "通常計測中", showSystemUi = true)
@Composable
private fun ActivePreview() = HomePreview()

@Preview(name = "計測停止疑い", showSystemUi = true)
@Composable
private fun StoppedPreview() = HomePreview(
    snapshot = previewSnapshot.copy(trackingStatus = TrackingStatus.MAY_BE_STOPPED),
)

@Preview(name = "データなし", showSystemUi = true)
@Composable
private fun EmptyPreview() = PreviewTheme {
    HomeScreen(HomeUiState(content = HomeContent.Empty), onAction = {})
}

@Preview(name = "目標達成", showSystemUi = true)
@Composable
private fun GoalAchievedPreview() = HomePreview(
    snapshot = previewSnapshot.copy(
        metrics = previewSnapshot.metrics.copy(steps = 10_420),
        match = previewSnapshot.match.copy(outcome = MatchOutcome.WON, selfProgress = 1f),
    ),
)

@Preview(name = "小型画面", widthDp = 320, heightDp = 568)
@Composable
private fun CompactPreview() = HomePreview()

@Preview(name = "大きなフォント", widthDp = 360, heightDp = 720, fontScale = 1.6f)
@Composable
private fun LargeFontPreview() = HomePreview()

@Preview(name = "Full Motion", showSystemUi = true)
@Composable
private fun FullMotionPreview() = HomePreview(motionLevel = MotionLevel.FULL)

@Preview(name = "Reduced Motion", showSystemUi = true)
@Composable
private fun ReducedMotionPreview() = HomePreview(motionLevel = MotionLevel.REDUCED)

@Preview(name = "ダークテーマ", showSystemUi = true, uiMode = android.content.res.Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun DarkPreview() = HomePreview()

@Composable
private fun HomePreview(
    snapshot: HomeSnapshot = previewSnapshot,
    motionLevel: MotionLevel = MotionLevel.OFF,
) = PreviewTheme {
    HomeScreen(
        uiState = HomeUiState(
            content = HomeContent.Ready(snapshot),
            motionLevel = motionLevel,
        ),
        onAction = {},
    )
}

@Composable
private fun PreviewTheme(content: @Composable () -> Unit) {
    StepArenaTheme(darkTheme = true, content = content)
}

private val previewSnapshot = HomeSnapshot(
    rank = RankStatus(RankTier.GOLD, 2, 1_840, 660),
    metrics = ActivityMetrics(7_420, 10_000, 5_630.0, 4_980, 286.0, 1.13),
    trackingStatus = TrackingStatus.ACTIVE,
    lastHealthyAt = Instant.parse("2026-07-29T09:21:00Z"),
    match = DailyMatch("Haruka", 0.74f, 0.68f, 0, MatchOutcome.IN_PROGRESS),
    winStreak = 3,
    league = LeagueStatus(7, 30, 420),
    reliability = DataReliability.PARTLY_ESTIMATED,
    isOffline = false,
)
