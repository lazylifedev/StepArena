package com.lazyapps.steparena.feature.game

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import com.lazyapps.steparena.core.designsystem.theme.StepArenaTheme
import com.lazyapps.steparena.test.awaitResumedHost
import org.junit.Before
import org.junit.Rule
import org.junit.Test

class ArenaNavigationScreenTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

    @Before fun awaitHost() = compose.awaitResumedHost()

    @Test
    fun deepLinkedArenaPageRestoresTheMatchingTabWithoutAchievements() {
        compose.setContent {
            StepArenaTheme {
                ArenaContent(
                    state = GameUiState(),
                    initialPage = ArenaPage.WEEKLY_GROUP,
                )
            }
        }

        compose.onNodeWithTag(ArenaTestTags.SCREEN).assertIsDisplayed()
        compose.onNodeWithTag(ArenaTestTags.tab(ArenaPage.WEEKLY_GROUP)).assertIsSelected()
        compose.onAllNodesWithTag("arena_tab_achievements").assertCountEquals(0)
    }

    @Test
    fun achievementsAreAnIndependentScreenWithoutArenaTabs() {
        compose.setContent {
            StepArenaTheme { AchievementScreen(AchievementUiState()) }
        }

        compose.onAllNodesWithTag(ArenaTestTags.SCREEN).assertCountEquals(0)
        compose.onAllNodesWithTag(ArenaTestTags.tab(ArenaPage.CHALLENGE)).assertCountEquals(0)
    }
}
