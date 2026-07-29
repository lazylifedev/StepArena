package com.lazyapps.steparena.feature.settings

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.lazyapps.steparena.feature.diagnostics.RecoveryHistoryScreen
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class RecoverySettingsScreenTest {
    @get:Rule val compose = createComposeRule()

    @Test fun optionalHealthConnectExplanationAndDefaultsAreVisible() {
        compose.setContent { RecoverySettingsScreen() }
        compose.onNodeWithText("Health Connect と計測復旧").assertIsDisplayed()
        compose.onNodeWithText("Health Connect補完を使用").assertIsDisplayed()
        compose.onNodeWithText("計測停止警告").assertIsDisplayed()
    }

    @Test fun emptyRecoveryHistoryIsVisible() {
        compose.setContent { RecoveryHistoryScreen() }
        compose.onNodeWithText("補完履歴").assertIsDisplayed()
        compose.onNodeWithText("補完履歴はありません").assertIsDisplayed()
    }
}
