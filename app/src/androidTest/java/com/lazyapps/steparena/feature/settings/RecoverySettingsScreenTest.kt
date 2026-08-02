package com.lazyapps.steparena.feature.settings

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performScrollTo
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.lazyapps.steparena.feature.diagnostics.RecoveryHistoryScreen
import com.lazyapps.steparena.test.awaitResumedHost
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class RecoverySettingsScreenTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

    @Before fun awaitHost() = compose.awaitResumedHost()

    @Test fun optionalHealthConnectExplanationAndDefaultsAreVisible() {
        compose.setContent { RecoverySettingsScreen() }
        compose.onNodeWithText("Health Connect と計測復旧").assertIsDisplayed()
        compose.onNodeWithText("Health Connect補完を使用").assertIsDisplayed()
        compose.onNodeWithText("計測停止警告").performScrollTo().assertIsDisplayed()
    }

    @Test fun emptyRecoveryHistoryIsVisible() {
        compose.setContent { RecoveryHistoryScreen() }
        compose.onNodeWithText("補完履歴").assertIsDisplayed()
        compose.onNodeWithText("補完履歴はありません").assertIsDisplayed()
    }
}
