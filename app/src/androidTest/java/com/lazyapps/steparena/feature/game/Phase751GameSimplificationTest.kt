package com.lazyapps.steparena.feature.game

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import com.lazyapps.steparena.core.database.entity.DailyMatchEntity
import com.lazyapps.steparena.core.database.entity.WeeklyLeagueEntity
import com.lazyapps.steparena.core.database.entity.WeeklyLeagueParticipantEntity
import com.lazyapps.steparena.core.designsystem.motion.MotionLevel
import com.lazyapps.steparena.core.designsystem.theme.StepArenaTheme
import com.lazyapps.steparena.game.CompetitiveStepQuality
import com.lazyapps.steparena.game.LeagueStatus
import com.lazyapps.steparena.game.MatchOutcome
import com.lazyapps.steparena.game.MatchStatus
import com.lazyapps.steparena.game.MatchType
import com.lazyapps.steparena.game.OpponentPersonality
import com.lazyapps.steparena.game.RankTier
import com.lazyapps.steparena.test.awaitResumedHost
import org.junit.Before
import org.junit.Rule
import org.junit.Test

class Phase751GameSimplificationTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

    @Before fun awaitHost() = compose.awaitResumedHost()

    @Test fun weeklyShowsAllTenIncludingRanksFourThroughEightAt340DpAndFontScaleTwo() {
        compose.setContent {
            CompositionLocalProvider(LocalDensity provides Density(LocalDensity.current.density, 2f)) {
                StepArenaTheme {
                    androidx.compose.foundation.layout.Box(Modifier.width(340.dp)) {
                        WeeklyGroupScreen(
                            WeeklyGroupUiState(league(), (1..10).map(::participant)),
                        )
                    }
                }
            }
        }

        (1..10).forEach { rank ->
            compose.onNodeWithTag(WeeklyGroupTestTags.CONTENT)
                .performScrollToNode(hasTestTag("weekly_participant_p$rank"))
            compose.onNodeWithTag("weekly_participant_p$rank").assertIsDisplayed()
        }
        (4..8).forEach { rank ->
            compose.onNodeWithTag(WeeklyGroupTestTags.CONTENT)
                .performScrollToNode(hasTestTag("weekly_participant_p$rank"))
            compose.onNodeWithText(rank.toString()).assertIsDisplayed()
        }
    }

    @Test fun compactHistoryOpensCompleteDetailSheetAt340DpAndFontScaleTwo() {
        val history = match("history", "2026-08-02", MatchStatus.FINALIZED, MatchOutcome.WIN)
        compose.setContent {
            CompositionLocalProvider(LocalDensity provides Density(LocalDensity.current.density, 2f)) {
                StepArenaTheme {
                    androidx.compose.foundation.layout.Box(Modifier.width(340.dp)) {
                        ChallengeScreen(
                            ChallengeUiState(
                                todayMatch = match("today", "2026-08-03", MatchStatus.ACTIVE, null),
                                recentMatches = listOf(history),
                                currentMeasuredSteps = 2_096,
                            ),
                            motionLevel = MotionLevel.OFF,
                        )
                    }
                }
            }
        }

        compose.onNodeWithTag(ChallengeTestTags.CONTENT)
            .performScrollToNode(hasTestTag(ChallengeTestTags.historyRow("history")))
        compose.onNodeWithTag(ChallengeTestTags.historyRow("history")).performClick()
        compose.onNodeWithTag(ChallengeTestTags.HISTORY_SHEET).assertIsDisplayed()
        compose.onNodeWithText("自分の対象歩数", substring = true).assertIsDisplayed()
        compose.onNodeWithText("レート変更前", substring = true).assertIsDisplayed()
        compose.onNodeWithText("レート変更後", substring = true).assertIsDisplayed()
        compose.onNodeWithText("レート増減", substring = true).assertIsDisplayed()
    }

    private fun league() = WeeklyLeagueEntity(
        "league", "2026-07-27", "2026-08-02", "Asia/Tokyo", LeagueStatus.FINALIZED,
        4_980, 5, "[]", 0, 0, 0,
    )

    private fun participant(rank: Int) = WeeklyLeagueParticipantEntity(
        "league", "p$rank", "ユーザー$rank", "", rank * 1_000, rank * 1_000L,
        rank, rank == 5, true, 0, 0,
    )

    private fun match(id: String, date: String, status: MatchStatus, outcome: MatchOutcome?) =
        DailyMatchEntity(
            id, date, "Asia/Tokyo", "season", MatchType.DAILY, status, outcome,
            "partner", "パートナー", "", RankTier.BRONZE, 1, OpponentPersonality.STEADY,
            6_800, 7_239, 7_239, 0, 0, "", CompetitiveStepQuality.FULL,
            1_000, 12, 1_012, null, if (status == MatchStatus.FINALIZED) 1 else null, 0, 0,
        )
}
