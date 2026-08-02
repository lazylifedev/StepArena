package com.lazyapps.steparena.feature.game

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import com.lazyapps.steparena.core.database.entity.DailyMatchEntity
import com.lazyapps.steparena.core.database.entity.GamePlayerProfileEntity
import com.lazyapps.steparena.core.database.entity.WeeklyLeagueEntity
import com.lazyapps.steparena.core.database.entity.WeeklyLeagueParticipantEntity
import com.lazyapps.steparena.core.designsystem.motion.MotionLevel
import com.lazyapps.steparena.core.designsystem.theme.StepArenaTheme
import com.lazyapps.steparena.game.*
import com.lazyapps.steparena.test.awaitResumedHost
import org.junit.Before
import org.junit.Rule
import org.junit.Test

class PlayerDisplayNameScreenTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

    @Before fun awaitHost() = compose.awaitResumedHost()

    @Test fun challengeUsesConfiguredDisplayName() {
        compose.setContent {
            StepArenaTheme {
                ChallengeScreen(
                    ChallengeUiState(todayMatch = match(), currentMeasuredSteps = 3_619, displayName = "歩子"),
                    motionLevel = MotionLevel.OFF,
                )
            }
        }
        compose.onNodeWithContentDescription("歩子", substring = true).assertIsDisplayed()
    }

    @Test fun rankUsesConfiguredDisplayName() {
        compose.setContent {
            StepArenaTheme {
                RankScreen(
                    RankUiState(
                        profile = GamePlayerProfileEntity(createdAtEpochMillis = 0, updatedAtEpochMillis = 0),
                        displayName = "歩子",
                    ),
                )
            }
        }
        compose.onNodeWithText("歩子").assertIsDisplayed()
    }

    @Test fun weeklyGroupUsesStructuredParticipantDisplayName() {
        compose.setContent {
            StepArenaTheme {
                WeeklyGroupScreen(
                    WeeklyGroupUiState(
                        league = league(),
                        participants = listOf(participant()),
                    ),
                )
            }
        }
        compose.onNodeWithText("歩子").assertIsDisplayed()
    }

    private fun match() = DailyMatchEntity(
        id = "today", localDate = "2026-07-31", zoneId = "Asia/Tokyo", seasonId = "2026-07",
        matchType = MatchType.DAILY, status = MatchStatus.ACTIVE, outcome = null,
        opponentId = "opponent", opponentName = "相手", opponentAvatarKey = "partner",
        opponentRankTier = RankTier.BRONZE, opponentRankDivision = 3,
        opponentPersonality = OpponentPersonality.STEADY, opponentTargetSteps = 4_000,
        totalUserSteps = 3_619, eligibleUserSteps = 3_619, restrictedUserSteps = 0,
        excludedUserSteps = 0, restrictionReasons = "", competitiveQuality = CompetitiveStepQuality.FULL,
        ratingBefore = 1_000, ratingDelta = null, ratingAfter = null, ratingBreakdown = null,
        finalizedAtEpochMillis = null, createdAtEpochMillis = 0, updatedAtEpochMillis = 0,
    )

    private fun league() = WeeklyLeagueEntity(
        id = "league", weekStartLocalDate = "2026-07-27", weekEndLocalDate = "2026-08-02",
        zoneId = "Asia/Tokyo", status = LeagueStatus.ACTIVE, userPoints = 3, userRank = 1,
        participantsJson = "[]", finalizedAtEpochMillis = null, createdAtEpochMillis = 0,
        updatedAtEpochMillis = 0,
    )

    private fun participant() = WeeklyLeagueParticipantEntity(
        leagueId = "league", participantId = "player", displayName = "歩子", avatarKey = "local_player",
        points = 3, eligibleSteps = 3_619, rank = 1, isLocalPlayer = true,
        generatedLocally = true, createdAtEpochMillis = 0, updatedAtEpochMillis = 0,
    )
}
