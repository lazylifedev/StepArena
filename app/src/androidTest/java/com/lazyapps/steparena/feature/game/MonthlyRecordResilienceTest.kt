package com.lazyapps.steparena.feature.game

import android.content.res.Configuration
import androidx.activity.ComponentActivity
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.unit.Density
import com.lazyapps.steparena.core.database.entity.GameSeasonEntity
import com.lazyapps.steparena.core.designsystem.theme.StepArenaTheme
import com.lazyapps.steparena.game.RankTier
import com.lazyapps.steparena.game.SeasonStatus
import com.lazyapps.steparena.test.awaitResumedHost
import org.junit.Before
import org.junit.Rule
import org.junit.Test

class MonthlyRecordResilienceTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

    @Before fun awaitHost() = compose.awaitResumedHost()

    @Test fun lightLandscapeAtTwoHundredPercentShowsMonthlyPrimaryValue() {
        compose.setContent {
            val configuration = Configuration(LocalConfiguration.current).apply {
                fontScale = 2f
                orientation = Configuration.ORIENTATION_LANDSCAPE
            }
            val density = LocalDensity.current
            CompositionLocalProvider(
                LocalConfiguration provides configuration,
                LocalDensity provides Density(density.density, 2f),
            ) {
                StepArenaTheme(darkTheme = false) {
                    MonthlyRecordScreen(MonthlyRecordUiState(season = season()))
                }
            }
        }
        compose.onNodeWithTag("monthly_record_screen").performScrollToNode(hasTestTag("monthly_primary_value"))
        compose.onNodeWithText("月間歩数 0歩").assertIsDisplayed()
    }

    private fun season() = GameSeasonEntity(
        id = "2026-07", startedAtEpochMillis = 0, endedAtEpochMillis = Long.MAX_VALUE,
        startRating = 1_000, endRating = null, highestRankTier = RankTier.BRONZE,
        highestRankDivision = 3, wins = 0, losses = 0, draws = 0,
        totalEligibleSteps = 0, bestWinStreak = 0, status = SeasonStatus.ACTIVE,
        rewardClaimed = false, createdAtEpochMillis = 0, updatedAtEpochMillis = 0,
    )
}
