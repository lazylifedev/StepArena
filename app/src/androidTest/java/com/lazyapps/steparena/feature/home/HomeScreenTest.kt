package com.lazyapps.steparena.feature.home

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.unit.Density
import com.lazyapps.steparena.app.navigation.StepArenaApp
import com.lazyapps.steparena.core.designsystem.motion.MotionLevel
import com.lazyapps.steparena.core.designsystem.theme.StepArenaTheme
import com.lazyapps.steparena.core.model.*
import org.junit.Rule
import org.junit.Test
import java.time.Instant

class HomeScreenTest {
    @get:Rule val composeRule = createComposeRule()

    @Test fun requiredFiveMetrics_areReachable() {
        setHome()
        listOf("今日の歩数", "歩行距離", "歩行時間", "消費カロリー", "平均歩行速度").forEach { label ->
            composeRule.onNodeWithTag(HomeTestTags.CONTENT).performScrollToNode(hasText(label))
            composeRule.onNodeWithText(label).assertIsDisplayed()
        }
    }

    @Test fun stoppedTrackingState_isDisplayed() {
        setHome(snapshot.copy(trackingStatus = TrackingStatus.MAY_BE_STOPPED))
        composeRule.onNodeWithText("計測が停止している可能性があります").assertIsDisplayed()
    }

    @Test fun startButton_isOperable() {
        var state by mutableStateOf(readyState)
        composeRule.setContent {
            StepArenaTheme {
                HomeScreen(state, {
                    if (it == HomeAction.StartSession) state = state.copy(sessionState = SessionState.STARTED)
                })
            }
        }
        composeRule.onNodeWithTag(HomeTestTags.CONTENT)
            .performScrollToNode(hasTestTag(HomeTestTags.START_BUTTON))
        composeRule.onNodeWithTag(HomeTestTags.START_BUTTON).performClick()
        composeRule.onNodeWithTag(HomeTestTags.START_BUTTON).assertIsDisplayed()
    }

    @Test fun bottomNavigation_opensSafePlaceholder() {
        composeRule.setContent {
            StepArenaTheme { StepArenaApp(readyState, {}) }
        }
        composeRule.onNodeWithText("マッチ").performClick()
        composeRule.onNodeWithText("この機能は次の開発フェーズで実装予定です。").assertIsDisplayed()
    }

    @Test fun largeFont_canReachBottomContent() {
        composeRule.setContent {
            val density = LocalDensity.current
            androidx.compose.runtime.CompositionLocalProvider(
                LocalDensity provides Density(density.density, 1.6f),
            ) { StepArenaTheme { HomeScreen(readyState, {}) } }
        }
        composeRule.onNodeWithTag(HomeTestTags.CONTENT)
            .performScrollToNode(hasTestTag(HomeTestTags.BOTTOM_REACH_MARKER))
        composeRule.onNodeWithTag(HomeTestTags.BOTTOM_REACH_MARKER).assertIsDisplayed()
    }

    private fun setHome(value: HomeSnapshot = snapshot) {
        composeRule.setContent {
            StepArenaTheme {
                HomeScreen(HomeUiState(HomeContent.Ready(value), MotionLevel.OFF), {})
            }
        }
    }

    private val readyState get() = HomeUiState(HomeContent.Ready(snapshot), MotionLevel.OFF)
    private val snapshot = HomeSnapshot(
        RankStatus(RankTier.GOLD, 2, 1_840, 660),
        ActivityMetrics(7_420, 10_000, 5_630.0, 4_980, 286.0, 1.13),
        TrackingStatus.ACTIVE,
        Instant.parse("2026-07-29T09:21:00Z"),
        DailyMatch("Haruka", 0.74f, 0.68f, 0, MatchOutcome.IN_PROGRESS),
        3,
        LeagueStatus(7, 30, 420),
        DataReliability.PARTLY_ESTIMATED,
        false,
    )
}
