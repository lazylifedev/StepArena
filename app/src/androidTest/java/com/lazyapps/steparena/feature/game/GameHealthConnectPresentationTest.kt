package com.lazyapps.steparena.feature.game

import androidx.activity.ComponentActivity
import android.content.res.Configuration
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.test.assertContentDescriptionEquals
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import com.lazyapps.steparena.R
import com.lazyapps.steparena.core.database.entity.DailyMatchEntity
import com.lazyapps.steparena.core.designsystem.motion.MotionLevel
import com.lazyapps.steparena.core.designsystem.theme.StepArenaTheme
import com.lazyapps.steparena.game.CompetitiveStepQuality
import com.lazyapps.steparena.game.MatchStatus
import com.lazyapps.steparena.game.MatchType
import com.lazyapps.steparena.game.OpponentPersonality
import com.lazyapps.steparena.game.RankTier
import com.lazyapps.steparena.test.awaitResumedHost
import org.junit.Before
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class GameHealthConnectPresentationTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

    @Before fun awaitHost() = compose.awaitResumedHost()

    @Test
    fun fixtureShowsVisualComparisonAndMovesLongExplanationsIntoInformationSheet() {
        compose.setContent {
            StepArenaTheme {
                MatchPage(
                    GameUiState(
                        todayMatch = activeMatch,
                        currentMeasuredSteps = Phase721ChallengeFixture.DEVICE_MEASURED_STEPS,
                        currentHealthConnectAddedSteps =
                            Phase721ChallengeFixture.HEALTH_CONNECT_ADDED_STEPS,
                    ),
                    motionLevel = MotionLevel.OFF,
                )
            }
        }

        compose.onNodeWithTag(ChallengeTestTags.COMPARISON)
            .assertIsDisplayed()
            .assertContentDescriptionEquals(
                "あなた。今日のチャレンジ。3,619歩、パートナー5,483歩、あと1,864歩",
            )
        compose.onNodeWithTag(
            ChallengeTestTags.USER_PROGRESS,
            useUnmergedTree = true,
        ).assertExists()
        compose.onNodeWithTag(
            ChallengeTestTags.PARTNER_PROGRESS,
            useUnmergedTree = true,
        ).assertExists()
        compose.onNodeWithTag(ChallengeTestTags.TOTAL_BREAKDOWN)
            .assertIsDisplayed()
            .assertTextContains("合計 3,639 対象 3,619")
        compose.onNodeWithText(
            "Health Connectから追加された20歩は、チャレンジには使用されません。",
        ).assertDoesNotExist()
        compose.onNodeWithText("毎日の歩数を、端末内で", substring = true).assertDoesNotExist()

        compose.onNodeWithTag(ChallengeTestTags.TOTAL_BREAKDOWN).performClick()
        compose.onNodeWithTag(ChallengeTestTags.INFO_SHEET).assertIsDisplayed()
        compose.onNodeWithText(
            "Health Connectから追加された20歩は、チャレンジには使用されません。",
        ).assertIsDisplayed()
        compose.onNodeWithText("チャレンジは、端末で直接計測した歩数だけを使用します。")
            .assertIsDisplayed()
    }

    @Test
    fun noAddedStepsHideHealthConnectExplanation() {
        compose.setContent {
            StepArenaTheme {
                MatchPage(
                    GameUiState(
                        todayMatch = activeMatch,
                        currentMeasuredSteps = Phase721ChallengeFixture.DEVICE_MEASURED_STEPS,
                        currentHealthConnectAddedSteps = 0,
                    ),
                    motionLevel = MotionLevel.OFF,
                )
            }
        }

        compose.onNodeWithTag(ChallengeTestTags.TOTAL_BREAKDOWN).assertDoesNotExist()
        compose.onNodeWithText("端末で直接計測した歩数", substring = true).assertDoesNotExist()
    }

    @Test
    fun comparisonAndInformationRemainReachableAt200PercentInLandscape() {
        compose.setContent {
            val configuration = Configuration().apply {
                fontScale = 2f
                orientation = Configuration.ORIENTATION_LANDSCAPE
                screenWidthDp = 640
                screenHeightDp = 320
            }
            CompositionLocalProvider(LocalConfiguration provides configuration) {
                StepArenaTheme {
                    MatchPage(
                        GameUiState(
                            todayMatch = activeMatch,
                            currentMeasuredSteps =
                                Phase721ChallengeFixture.DEVICE_MEASURED_STEPS,
                            currentHealthConnectAddedSteps =
                                Phase721ChallengeFixture.HEALTH_CONNECT_ADDED_STEPS,
                        ),
                        motionLevel = MotionLevel.REDUCED,
                    )
                }
            }
        }

        compose.onNodeWithTag(ChallengeTestTags.INFO)
            .assertIsDisplayed()
            .assertHeightIsAtLeast(48.dp)
        compose.onNodeWithTag(ChallengeTestTags.COMPARISON).assertIsDisplayed()
    }

    @Test
    fun trackingNotificationShowsTheSameFormattedDailyGoal() {
        assertEquals(
            "今日 3,619 / 10,000歩・最終更新 12:00",
            compose.activity.getString(
                R.string.notification_tracking_text,
                "3,619",
                "10,000",
                "12:00",
            ),
        )
    }

    private val activeMatch = DailyMatchEntity(
        id = "today",
        localDate = "2026-07-30",
        zoneId = "Asia/Tokyo",
        seasonId = "season",
        matchType = MatchType.DAILY,
        status = MatchStatus.ACTIVE,
        outcome = null,
        opponentId = "partner",
        opponentName = "パートナー",
        opponentAvatarKey = "walk",
        opponentRankTier = RankTier.BRONZE,
        opponentRankDivision = 3,
        opponentPersonality = OpponentPersonality.STEADY,
        opponentTargetSteps = Phase721ChallengeFixture.PARTNER_TARGET_STEPS,
        totalUserSteps = 0,
        eligibleUserSteps = 0,
        restrictedUserSteps = 0,
        excludedUserSteps = 0,
        restrictionReasons = "",
        competitiveQuality = CompetitiveStepQuality.FULL,
        ratingBefore = 1_000,
        ratingDelta = null,
        ratingAfter = null,
        ratingBreakdown = null,
        finalizedAtEpochMillis = null,
        createdAtEpochMillis = 0,
        updatedAtEpochMillis = 0,
    )
}
