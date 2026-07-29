package com.lazyapps.steparena.game

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.lazyapps.steparena.core.designsystem.theme.StepArenaTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class DebugGameScreenTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

    @Test fun warningAndScenarioCategoriesAreVisible() {
        compose.setContent { StepArenaTheme { DebugGameScreen({}, {}) } }
        compose.onNodeWithTag(DebugGameTestTags.WARNING).assertIsDisplayed()
        compose.onNodeWithText("歩数").assertIsDisplayed()
        compose.onNodeWithTag(DebugGameTestTags.action(DebugGameScenario.COUNTER_100))
            .assertIsDisplayed()
    }

    @Test fun actionRequiresConfirmationBeforeExecution() {
        var executed: DebugGameScenario? = null
        compose.setContent { StepArenaTheme { DebugGameScreen({}, { executed = it }) } }
        compose.onNodeWithTag(DebugGameTestTags.action(DebugGameScenario.COUNTER_5000))
            .performClick()
        compose.onNodeWithText("Debug操作の確認").assertIsDisplayed()
        assertEquals(null, executed)
        compose.onNodeWithText("実行").performClick()
        assertEquals(DebugGameScenario.COUNTER_5000, executed)
    }
}
