package com.lazyapps.steparena.feature.home

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.unit.Density
import com.lazyapps.steparena.app.navigation.StepArenaApp
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
import org.junit.Rule
import org.junit.Test
import java.time.Instant

class HomeScreenTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun requiredFiveMetrics_areReachable() {
        setHome()

        listOf("今日の歩数", "歩行距離", "歩行時間", "消費カロリー", "平均歩行時速")
            .forEach { label ->
                composeRule.onNodeWithTag(HomeTestTags.CONTENT)
                    .performScrollToNode(hasText(label))
                composeRule.onNodeWithText(label).assertIsDisplayed()
            }
    }

    @Test
    fun stoppedTrackingState_isDisplayed() {
        setHome(snapshot = snapshot.copy(trackingStatus = TrackingStatus.MAY_BE_STOPPED))

        composeRule.onNodeWithText("計測が停止している可能性があります").assertIsDisplayed()
    }

    @Test
    fun startButton_isOperable() {
        var state by mutableStateOf(readyState)
        composeRule.setContent {
            StepArenaTheme {
                HomeScreen(
                    uiState = state,
                    onAction = {
                        if (it == HomeAction.StartSession) {
                            state = state.copy(sessionState = SessionState.STARTED)
                        }
                    },
                )
            }
        }

        composeRule.onNodeWithTag(HomeTestTags.CONTENT)
            .performScrollToNode(hasTestTag(HomeTestTags.START_BUTTON))
        composeRule.onNodeWithTag(HomeTestTags.START_BUTTON).performClick()
        composeRule.onNodeWithText("歩行セッションを開始しました").assertExists()
    }

    @Test
    fun bottomNavigation_opensSafePlaceholder() {
        composeRule.setContent {
            StepArenaTheme {
                StepArenaApp(homeUiState = readyState, onHomeAction = {})
            }
        }

        composeRule.onNodeWithText("マッチ").performClick()
        composeRule.onNodeWithText("この機能は次の開発フェーズで実装予定です。").assertIsDisplayed()
    }

    @Test
    fun largeFont_canReachBottomContent() {
        composeRule.setContent {
            val density = LocalDensity.current
            androidx.compose.runtime.CompositionLocalProvider(
                LocalDensity provides Density(density.density, fontScale = 1.6f),
            ) {
                StepArenaTheme {
                    HomeScreen(uiState = readyState, onAction = {})
                }
            }
        }

        composeRule.onNodeWithTag(HomeTestTags.CONTENT)
            .performScrollToNode(hasTestTag(HomeTestTags.BOTTOM_REACH_MARKER))
        composeRule.onNodeWithTag(HomeTestTags.BOTTOM_REACH_MARKER).assertIsDisplayed()
    }

    private fun setHome(snapshot: HomeSnapshot = this.snapshot) {
        composeRule.setContent {
            StepArenaTheme {
                HomeScreen(
                    uiState = HomeUiState(
                        content = HomeContent.Ready(snapshot),
                        motionLevel = MotionLevel.OFF,
                    ),
                    onAction = {},
                )
            }
        }
    }

    private val readyState get() = HomeUiState(
        content = HomeContent.Ready(snapshot),
        motionLevel = MotionLevel.OFF,
    )

    private val snapshot = HomeSnapshot(
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
}
