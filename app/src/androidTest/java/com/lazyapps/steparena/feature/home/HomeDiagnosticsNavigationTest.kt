package com.lazyapps.steparena.feature.home

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeUp
import com.lazyapps.steparena.app.navigation.StepArenaApp
import com.lazyapps.steparena.core.designsystem.motion.MotionLevel
import com.lazyapps.steparena.core.model.ActivityMetrics
import com.lazyapps.steparena.core.model.DataReliability
import com.lazyapps.steparena.core.model.DailyMatch
import com.lazyapps.steparena.core.model.HomeSnapshot
import com.lazyapps.steparena.core.model.LeagueStatus
import com.lazyapps.steparena.core.model.MatchOutcome
import com.lazyapps.steparena.core.model.RankStatus
import com.lazyapps.steparena.core.model.RankTier
import com.lazyapps.steparena.core.model.TrackingStatus
import com.lazyapps.steparena.core.designsystem.theme.StepArenaTheme
import com.lazyapps.steparena.feature.diagnostics.DiagnosticsTestTags
import com.lazyapps.steparena.tracking.StepTrackingState
import com.lazyapps.steparena.test.awaitResumedHost
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId

class HomeDiagnosticsNavigationTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    @Before
    fun awaitHost() = composeRule.awaitResumedHost()

    @Test
    fun stoppedTrackingPanel_opensDiagnostics_andBackReturnsHome() {
        composeRule.setContent {
            StepArenaTheme {
                StepArenaApp(
                    homeUiState = HomeUiState(
                        content = HomeContent.Ready(snapshot),
                        motionLevel = MotionLevel.OFF,
                        sessionState = SessionState.TRACKING_STOPPED,
                        localDate = LocalDate.of(2026, 7, 30),
                        zoneId = ZoneId.of("Asia/Tokyo"),
                    ),
                    onHomeAction = {},
                    trackingState = StepTrackingState(),
                )
            }
        }

        composeRule.onNodeWithTag(HomeTestTags.TRACKING_STATUS).performClick()
        composeRule.onNodeWithTag(DiagnosticsTestTags.SCREEN).assertIsDisplayed()

        composeRule.onNodeWithTag(DiagnosticsTestTags.SCREEN)
            .performTouchInput { swipeUp() }
        composeRule.onNodeWithTag(DiagnosticsTestTags.SCREEN).assertIsDisplayed()

        composeRule.activityRule.scenario.onActivity { activity ->
            activity.onBackPressedDispatcher.onBackPressed()
        }
        composeRule.onNodeWithTag(HomeTestTags.CONTENT).assertIsDisplayed()
        composeRule.onNodeWithTag(HomeTestTags.TRACKING_STATUS).assertIsDisplayed()
    }

    @Test
    fun savedSettingsBackStack_homeDiagnosticsBackReturnsHomeAndKeepsHomeSelected() {
        setAppContent()

        composeRule.onNodeWithText(composeRule.activity.getString(com.lazyapps.steparena.R.string.nav_settings))
            .performClick()
        composeRule.onNodeWithText(composeRule.activity.getString(com.lazyapps.steparena.R.string.nav_home))
            .performClick()
        composeRule.onNodeWithTag(HomeTestTags.TRACKING_STATUS).performClick()
        composeRule.onNodeWithTag(DiagnosticsTestTags.SCREEN).assertIsDisplayed()

        composeRule.activityRule.scenario.onActivity { activity ->
            activity.onBackPressedDispatcher.onBackPressed()
        }
        composeRule.onNodeWithTag(HomeTestTags.CONTENT).assertIsDisplayed()
        composeRule.onNodeWithTag("bottom_navigation_home")
            .assertIsSelected()
    }

    @Test
    fun settingsDiagnosticsBackReturnsSettingsAndKeepsSettingsSelected() {
        setAppContent()

        composeRule.onNodeWithText(composeRule.activity.getString(com.lazyapps.steparena.R.string.nav_settings))
            .performClick()
        composeRule.onNodeWithText(composeRule.activity.getString(com.lazyapps.steparena.R.string.settings_diagnostics))
            .performClick()
        composeRule.onNodeWithTag(DiagnosticsTestTags.SCREEN).assertIsDisplayed()

        composeRule.activityRule.scenario.onActivity { activity ->
            activity.onBackPressedDispatcher.onBackPressed()
        }
        composeRule.onNodeWithText(composeRule.activity.getString(com.lazyapps.steparena.R.string.settings_diagnostics))
            .assertIsDisplayed()
        composeRule.onNodeWithTag("bottom_navigation_settings")
            .assertIsSelected()
    }

    private fun setAppContent() {
        composeRule.setContent {
            StepArenaTheme {
                StepArenaApp(
                    homeUiState = HomeUiState(
                        content = HomeContent.Ready(snapshot),
                        motionLevel = MotionLevel.OFF,
                        sessionState = SessionState.TRACKING_STOPPED,
                        localDate = LocalDate.of(2026, 7, 30),
                        zoneId = ZoneId.of("Asia/Tokyo"),
                    ),
                    onHomeAction = {},
                    trackingState = StepTrackingState(),
                )
            }
        }
    }

    private val snapshot = HomeSnapshot(
        rank = RankStatus(RankTier.GOLD, 2, 1_840, 660),
        metrics = ActivityMetrics(7_420, 10_000, 5_630.0, 4_980, 286.0, 1.13),
        trackingStatus = TrackingStatus.MAY_BE_STOPPED,
        lastHealthyAt = null,
        match = DailyMatch("Haruka", 0.74f, 0.68f, 0, MatchOutcome.IN_PROGRESS),
        winStreak = 3,
        league = LeagueStatus(7, 30, 420),
        reliability = DataReliability.PARTLY_ESTIMATED,
        isOffline = false,
    )
}
