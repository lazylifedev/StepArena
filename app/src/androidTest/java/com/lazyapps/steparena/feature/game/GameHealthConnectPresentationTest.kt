package com.lazyapps.steparena.feature.game

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import com.lazyapps.steparena.core.database.entity.DailyMatchEntity
import com.lazyapps.steparena.core.designsystem.theme.StepArenaTheme
import com.lazyapps.steparena.game.CompetitiveStepQuality
import com.lazyapps.steparena.game.MatchStatus
import com.lazyapps.steparena.game.MatchType
import com.lazyapps.steparena.game.OpponentPersonality
import com.lazyapps.steparena.game.RankTier
import org.junit.Rule
import org.junit.Test

class GameHealthConnectPresentationTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun addedStepsShowTotalAndMeasuredChallengeStepsWithoutReflectedZero() {
        compose.setContent {
            StepArenaTheme {
                MatchPage(
                    GameUiState(
                        todayMatch = activeMatch,
                        currentMeasuredSteps = 5_864,
                        currentHealthConnectAddedSteps = 10,
                    ),
                )
            }
        }

        compose.onNodeWithText("今日の合計").assertIsDisplayed()
        compose.onNodeWithText("5,874歩").assertIsDisplayed()
        compose.onNodeWithText("チャレンジ対象歩数 5,864歩").assertIsDisplayed()
        compose.onNodeWithText(
            "Health Connectから追加された10歩は、チャレンジには使用されません。",
        ).assertIsDisplayed()
        compose.onNodeWithText("チャレンジは、端末で直接計測した歩数だけを使用します。")
            .assertIsDisplayed()
        compose.onNodeWithText("0歩が反映", substring = true).assertDoesNotExist()
    }

    @Test
    fun noAddedStepsHideHealthConnectExplanation() {
        compose.setContent {
            StepArenaTheme {
                MatchPage(
                    GameUiState(
                        todayMatch = activeMatch,
                        currentMeasuredSteps = 5_864,
                        currentHealthConnectAddedSteps = 0,
                    ),
                )
            }
        }

        compose.onNodeWithText("Health Connectから追加", substring = true).assertDoesNotExist()
        compose.onNodeWithText("端末で直接計測した歩数", substring = true).assertDoesNotExist()
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
        opponentTargetSteps = 4_263,
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
