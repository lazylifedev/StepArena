package com.lazyapps.steparena.feature.home

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.activity.ComponentActivity
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.assertContentDescriptionEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.unit.Density
import com.lazyapps.steparena.app.navigation.StepArenaApp
import com.lazyapps.steparena.core.designsystem.motion.MotionLevel
import com.lazyapps.steparena.core.designsystem.theme.StepArenaTheme
import com.lazyapps.steparena.core.model.*
import com.lazyapps.steparena.feature.game.ChallengeTestTags
import com.lazyapps.steparena.test.awaitResumedHost
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.Assert.assertEquals
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Locale
import android.content.res.Configuration
import android.os.LocaleList

class HomeScreenTest {
    @get:Rule val composeRule = createAndroidComposeRule<ComponentActivity>()

    @Before fun awaitHost() = composeRule.awaitResumedHost()

    @Test fun requiredMetrics_areReachable() {
        setHome()
        listOf("今日の歩数", "歩行距離", "歩行時間", "消費カロリー").forEach { label ->
            composeRule.onNodeWithTag(HomeTestTags.CONTENT).performScrollToNode(hasText(label))
            composeRule.onNodeWithText(label).assertIsDisplayed()
        }
    }

    @Test fun stoppedTrackingState_isDisplayed() {
        setHome(snapshot.copy(trackingStatus = TrackingStatus.MAY_BE_STOPPED))
        composeRule.onNodeWithTag(HomeTestTags.CONTENT)
            .performScrollToNode(hasText("計測を確認"))
        composeRule.onNodeWithText("計測を確認").assertIsDisplayed()
        composeRule.onNodeWithText("タップして原因と対処を確認").assertIsDisplayed()
    }

    @Test fun homeUsesOneDateLineInsteadOfPersistentMarketingCopy() {
        setHome()
        composeRule.onNodeWithText(
            DateTimeFormatter.ofLocalizedDate(FormatStyle.FULL)
                .withLocale(Locale.JAPAN)
                .format(homeDate),
        ).assertIsDisplayed()
        composeRule.onNodeWithText("TODAY'S STEPS").assertDoesNotExist()
        composeRule.onNodeWithText("今日の一歩を、楽しい習慣へ。").assertDoesNotExist()
        composeRule.onNodeWithText("TODAY'S ARENA").assertDoesNotExist()
        composeRule.onNodeWithText("勝負の力へ", substring = true).assertDoesNotExist()
    }

    @Test fun healthConnectAddedStepsOpenBreakdownSheet() {
        setHome(
            snapshot.copy(
                metrics = snapshot.metrics.copy(steps = 5_874),
                measuredSteps = 5_864,
                recoveredSteps = 10,
            ),
        )
        composeRule.onNodeWithTag(HomeTestTags.HEALTH_BREAKDOWN)
            .assertContentDescriptionEquals("Health Connectから10歩追加。内訳を表示")
            .assertIsDisplayed()
            .performClick()
        composeRule.onNodeWithText("端末で計測").assertIsDisplayed()
        composeRule.onNodeWithText("Health Connect").assertIsDisplayed()
        composeRule.onNodeWithText("合計").assertIsDisplayed()
    }

    @Test fun healthConnectZeroHidesBreakdownEntry() {
        setHome(snapshot.copy(recoveredSteps = 0, measuredSteps = snapshot.metrics.steps.toLong()))
        composeRule.onNodeWithTag(HomeTestTags.HEALTH_BREAKDOWN).assertDoesNotExist()
    }

    @Test fun walkingExplanationOnlyAppearsFromInformationButton() {
        setState(readyState.copy(sessionState = SessionState.TRACKING))
        val explanation =
            "これから歩く区間を、1回のウォーキングとして記録します。通常の歩数計測は常に行われています。"
        composeRule.onNodeWithText(explanation).assertDoesNotExist()
        composeRule.onNodeWithTag(HomeTestTags.WALKING_INFO).performClick()
        composeRule.onNodeWithText(explanation).assertIsDisplayed()
    }

    @Test fun walkingInformationClickOnlyOpensSheetWithoutStartingManualWalk() {
        val actions = mutableListOf<HomeAction>()
        setState(readyState.copy(sessionState = SessionState.TRACKING), actions::add)

        composeRule.onNodeWithTag(HomeTestTags.WALKING_INFO).performClick()

        composeRule.onNodeWithTag(HomeTestTags.WALKING_INFO_SHEET).assertIsDisplayed()
        composeRule.onNodeWithText(
            "これから歩く区間を、1回のウォーキングとして記録します。通常の歩数計測は常に行われています。",
        ).assertIsDisplayed()
        composeRule.runOnIdle {
            assertEquals(0, actions.count { it == HomeAction.StartManualWalk })
        }
    }

    @Test fun walkingInformationClickWhileRecordingDoesNotEndManualWalk() {
        val actions = mutableListOf<HomeAction>()
        val state = readyState.copy(
            sessionState = SessionState.MANUAL_WALK,
            manualSession = ManualSessionUi("manual-uuid", 1_775_000_000_000, 842, 589.4, 480),
        )
        setState(state, actions::add)

        composeRule.onNodeWithTag(HomeTestTags.WALKING_INFO).performClick()

        composeRule.onNodeWithTag(HomeTestTags.WALKING_INFO_SHEET).assertIsDisplayed()
        composeRule.runOnIdle {
            assertEquals(0, actions.count { it is HomeAction.EndManualWalk })
        }
    }

    @Test fun walkingCardClickStartsOrEndsExactlyOnce() {
        var state by mutableStateOf(readyState.copy(sessionState = SessionState.TRACKING))
        val actions = mutableListOf<HomeAction>()
        composeRule.setContent {
            StepArenaTheme { HomeScreen(state, actions::add) }
        }
        composeRule.onNodeWithTag(HomeTestTags.START_BUTTON).performClick()
        composeRule.runOnIdle {
            assertEquals(1, actions.count { it == HomeAction.StartManualWalk })
            actions.clear()
            state = readyState.copy(
                sessionState = SessionState.MANUAL_WALK,
                manualSession = ManualSessionUi(
                    "manual-uuid",
                    1_775_000_000_000,
                    842,
                    589.4,
                    480,
                ),
            )
        }
        composeRule.waitForIdle()
        composeRule.onNodeWithTag(HomeTestTags.START_BUTTON).performClick()
        composeRule.runOnIdle {
            assertEquals(1, actions.count { it == HomeAction.EndManualWalk("manual-uuid") })
        }
    }

    @Test fun dateUsesProvidedValueAndReformatsWhenLocaleChanges() {
        var locale by mutableStateOf(Locale.US)
        val state = readyState
        composeRule.setContent {
            val configuration = Configuration().apply {
                setLocales(LocaleList(locale))
            }
            CompositionLocalProvider(LocalConfiguration provides configuration) {
                StepArenaTheme { HomeScreen(state, {}) }
            }
        }
        val usDate = DateTimeFormatter.ofLocalizedDate(FormatStyle.FULL)
            .withLocale(Locale.US)
            .format(homeDate)
        composeRule.onNodeWithText(usDate).assertIsDisplayed()

        composeRule.runOnIdle { locale = Locale.JAPAN }

        val japaneseDate = DateTimeFormatter.ofLocalizedDate(FormatStyle.FULL)
            .withLocale(Locale.JAPAN)
            .format(homeDate)
        composeRule.onNodeWithText(japaneseDate).assertIsDisplayed()
        composeRule.onNodeWithText(usDate).assertDoesNotExist()
    }

    @Test fun startButton_isOperable() {
        var state by mutableStateOf(readyState)
        composeRule.setContent {
            StepArenaTheme {
                HomeScreen(state, {
                    if (it == HomeAction.StartSession) {
                        state = state.copy(sessionState = SessionState.TRACKING)
                    }
                })
            }
        }
        composeRule.onNodeWithTag(HomeTestTags.CONTENT)
            .performScrollToNode(hasTestTag(HomeTestTags.START_BUTTON))
        composeRule.onNodeWithTag(HomeTestTags.START_BUTTON).performClick()
        composeRule.onNodeWithTag(HomeTestTags.START_BUTTON).assertIsDisplayed()
    }

    @Test fun primaryAction_distinguishesTrackingAndManualWalk() {
        setState(readyState.copy(sessionState = SessionState.TRACKING))
        composeRule.onNodeWithTag(HomeTestTags.CONTENT)
            .performScrollToNode(hasText("ウォーキング記録"))
        composeRule.onNodeWithText("ウォーキング記録").assertIsDisplayed()
        composeRule.onNodeWithText("歩数計測を停止").assertIsDisplayed()
    }

    @Test fun manualWalk_displaysSessionMetricsAndEndAction() {
        setState(
            readyState.copy(
                sessionState = SessionState.MANUAL_WALK,
                manualSession = ManualSessionUi("manual-uuid", 1_775_000_000_000, 842, 589.4, 480),
            ),
        )
        composeRule.onNodeWithTag(HomeTestTags.CONTENT)
            .performScrollToNode(hasText("ウォーキング記録 842歩"))
        composeRule.onNodeWithText("ウォーキング記録 842歩").assertIsDisplayed()
        composeRule.onNodeWithText("歩数計測を停止").assertIsDisplayed()
    }

    @Test fun bottomNavigation_opensLocalMatch() {
        composeRule.setContent {
            StepArenaTheme { StepArenaApp(readyState, {}) }
        }
        composeRule.onNodeWithText("チャレンジ").performClick()
        composeRule.onNodeWithTag(ChallengeTestTags.INFO).assertDoesNotExist()
        composeRule.onNodeWithText("毎日の歩数を、端末内で", substring = true)
            .assertDoesNotExist()
    }

    @Test fun largeFont_canReachBottomContent() {
        composeRule.setContent {
            val density = LocalDensity.current
            androidx.compose.runtime.CompositionLocalProvider(
                LocalDensity provides Density(density.density, 2f),
            ) { StepArenaTheme { HomeScreen(readyState, {}) } }
        }
        composeRule.onNodeWithTag(HomeTestTags.CONTENT)
            .performScrollToNode(hasTestTag(HomeTestTags.BOTTOM_REACH_MARKER))
        composeRule.onNodeWithTag(HomeTestTags.BOTTOM_REACH_MARKER).assertIsDisplayed()
    }

    private fun setHome(value: HomeSnapshot = snapshot) {
        composeRule.setContent {
            StepArenaTheme {
                HomeScreen(readyState.copy(content = HomeContent.Ready(value)), {})
            }
        }
    }

    private fun setState(state: HomeUiState, onAction: (HomeAction) -> Unit = {}) {
        composeRule.setContent { StepArenaTheme { HomeScreen(state, onAction) } }
    }

    private val homeDate = LocalDate.of(2026, 7, 30)
    private val readyState get() = HomeUiState(
        content = HomeContent.Ready(snapshot),
        motionLevel = MotionLevel.OFF,
        localDate = homeDate,
        zoneId = ZoneId.of("Asia/Tokyo"),
    )
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
