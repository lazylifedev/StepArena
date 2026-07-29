package com.lazyapps.steparena.game

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.hasTestTag
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
        compose.setContent { StepArenaTheme { DebugGameScreen({}, { executed = it }, isolated = true) } }
        val counterTag = DebugGameTestTags.action(DebugGameScenario.COUNTER_5000)
        compose.onNodeWithTag(DebugGameTestTags.SCREEN).performScrollToNode(hasTestTag(counterTag))
        compose.onNodeWithTag(counterTag)
            .performClick()
        compose.onNodeWithText("Debug操作の確認").assertIsDisplayed()
        assertEquals(null, executed)
        compose.onNodeWithText("実行").performClick()
        assertEquals(DebugGameScenario.COUNTER_5000, executed)
    }

    @Test fun phase51RequiredResetAndNpcTargetAreReachable() {
        compose.setContent { StepArenaTheme { DebugGameScreen({}, {}, isolated = true) } }
        val npcTag = DebugGameTestTags.action(DebugGameScenario.SET_NPC_4000)
        compose.onNodeWithTag(DebugGameTestTags.SCREEN).performScrollToNode(hasTestTag(npcTag))
        compose.onNodeWithTag(npcTag)
            .assertIsDisplayed()
        val resetTag = DebugGameTestTags.action(DebugGameScenario.RESET_DEBUG_DATA)
        compose.onNodeWithTag(DebugGameTestTags.SCREEN).performScrollToNode(hasTestTag(resetTag))
        compose.onNodeWithTag(resetTag)
            .assertIsDisplayed()
    }

    @Test fun isolatedModeRequiresExplicitConfirmedStartAndShowsBanner() {
        var started = false
        compose.setContent {
            StepArenaTheme {
                DebugGameScreen(
                    onClose = {},
                    onRun = {},
                    isolated = false,
                    onStartIsolated = { started = true },
                )
            }
        }
        compose.onNodeWithText("通常データ（シナリオ操作は禁止）").assertIsDisplayed()
        compose.onNodeWithTag(DebugGameTestTags.START_ISOLATED).performClick()
        assertEquals(false, started)
        compose.onNodeWithText("切り替える").assertIsDisplayed()
    }
}
