package com.lazyapps.steparena.feature.game

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performScrollToNode
import com.lazyapps.steparena.core.database.entity.DailyMatchEntity
import com.lazyapps.steparena.core.designsystem.motion.MotionLevel
import com.lazyapps.steparena.core.designsystem.theme.StepArenaTheme
import com.lazyapps.steparena.game.CompetitiveStepQuality
import com.lazyapps.steparena.game.MatchStatus
import com.lazyapps.steparena.game.MatchType
import com.lazyapps.steparena.game.OpponentPersonality
import com.lazyapps.steparena.game.PartnerProgress
import com.lazyapps.steparena.game.PartnerSyncState
import com.lazyapps.steparena.game.RankTier
import com.lazyapps.steparena.test.awaitResumedHost
import org.junit.Before
import org.junit.Rule
import org.junit.Test

class ChallengeOfficialStepsUiTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    @Before fun awaitHost() = compose.awaitResumedHost()

    @Test fun leadDifferenceAndUpdatedAtAreShownWithoutPercent() {
        setScreen(1_093, 5_499)
        compose.onNodeWithText("あと4,406歩で追いつく").assertIsDisplayed()
        compose.onNodeWithTag(ChallengeTestTags.CONTENT).performScrollToNode(hasText("最終更新 06:43"))
        compose.onNodeWithText("最終更新 06:43").assertIsDisplayed()
        compose.onNodeWithText("%").assertDoesNotExist()
    }

    @Test fun tiedStateIsDisplayed() {
        setScreen(5_000, 5_000)
        compose.onNodeWithText("同点").assertIsDisplayed()
    }

    @Test fun unsyncedStateDoesNotTreatMissingStepsAsZero() {
        setScreen(5_000, null)
        compose.onNodeWithText("相手の更新待ち").assertIsDisplayed()
        compose.onNodeWithText("同点").assertDoesNotExist()
    }

    @Test fun thirtyTwoAndThirtyEightThousandKeepCompetitionDifference() {
        setScreen(32_000, 38_000)
        compose.onNodeWithText("あと6,000歩で追いつく").assertIsDisplayed()
        compose.onNodeWithTag(ChallengeTestTags.CONTENT).performScrollToNode(hasText("あと6,000歩で追いつく"))
        compose.onNodeWithTag(ChallengeTestTags.USER_PROGRESS, useUnmergedTree = true).assertIsDisplayed()
        compose.onNodeWithTag(ChallengeTestTags.PARTNER_PROGRESS, useUnmergedTree = true).assertIsDisplayed()
    }

    private fun setScreen(mySteps: Long, partnerSteps: Long?) {
        compose.setContent {
            StepArenaTheme {
                ChallengeScreen(
                    ChallengeUiState(
                        todayMatch = match(), currentMeasuredSteps = mySteps, currentEligibleSteps = mySteps,
                        partnerProgress = partnerSteps?.let {
                            PartnerProgress(
                                it,
                                java.time.LocalDateTime.of(2026, 8, 5, 6, 43)
                                    .atZone(java.time.ZoneId.of("Asia/Tokyo")).toInstant().toEpochMilli(),
                                "2026-08-05", "Asia/Tokyo", PartnerSyncState.SYNCED,
                            )
                        },
                        partnerProgressProvided = true,
                    ),
                    motionLevel = MotionLevel.OFF,
                )
            }
        }
    }

    private fun match() = DailyMatchEntity(
        "ui", "2026-08-05", "Asia/Tokyo", "season", MatchType.DAILY, MatchStatus.ACTIVE, null,
        "bot", "Bot", "", RankTier.GOLD, 2, OpponentPersonality.STEADY, 10_000,
        0, 0, 0, 0, "", CompetitiveStepQuality.FULL, 1_000, null, null, null, null, 0, 0,
    )
}
