package com.lazyapps.steparena.core.designsystem.preview

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.lazyapps.steparena.core.designsystem.component.MatchCard
import com.lazyapps.steparena.core.designsystem.component.MetricCard
import com.lazyapps.steparena.core.designsystem.component.PrimaryActionButton
import com.lazyapps.steparena.core.designsystem.component.RankBadge
import com.lazyapps.steparena.core.designsystem.component.StepArenaBackground
import com.lazyapps.steparena.core.designsystem.component.StepProgressRing
import com.lazyapps.steparena.core.designsystem.component.TrackingStatusChip
import com.lazyapps.steparena.core.designsystem.theme.StepArenaSpacing
import com.lazyapps.steparena.core.designsystem.theme.StepArenaTheme

@Preview(name = "RankBadge", showBackground = true)
@Composable
private fun RankBadgePreview() = PreviewFrame { RankBadge("1,840 RP") }

@Preview(name = "MetricCard", showBackground = true)
@Composable
private fun MetricCardPreview() = PreviewFrame {
    MetricCard("歩行距離", "5.63 km")
}

@Preview(name = "TrackingStatusChip", showBackground = true)
@Composable
private fun TrackingStatusChipPreview() = PreviewFrame {
    TrackingStatusChip("正常に計測中", isHealthy = true)
}

@Preview(name = "StepProgressRing", showBackground = true)
@Composable
private fun StepProgressRingPreview() = PreviewFrame {
    StepProgressRing(
        progress = 0.74f,
        description = "目標達成率 74 パーセント",
        modifier = Modifier.size(180.dp),
    ) {
        RankBadge("74%")
    }
}

@Preview(name = "MatchCard", showBackground = true)
@Composable
private fun MatchCardPreview() = PreviewFrame {
    MatchCard(
        title = "今日のマッチ",
        opponent = "対戦相手 Haruka",
        selfLabel = "あなた",
        opponentLabel = "相手",
        selfProgress = 0.74f,
        opponentProgress = 0.68f,
        supportingText = "現在リード中・3連勝",
        interactionLabel = "タップして詳細を見る",
    )
}

@Preview(name = "PrimaryActionButton", showBackground = true)
@Composable
private fun PrimaryActionButtonPreview() = PreviewFrame {
    PrimaryActionButton("歩行セッションを開始", onClick = {})
}

@Composable
private fun PreviewFrame(content: @Composable () -> Unit) {
    StepArenaTheme {
        StepArenaBackground {
            Column(Modifier.padding(StepArenaSpacing.lg)) { content() }
        }
    }
}
