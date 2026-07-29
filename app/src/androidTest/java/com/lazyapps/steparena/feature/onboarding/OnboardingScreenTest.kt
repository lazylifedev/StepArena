package com.lazyapps.steparena.feature.onboarding

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.lazyapps.steparena.core.designsystem.theme.StepArenaTheme
import com.lazyapps.steparena.test.awaitResumedHost
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test

class OnboardingScreenTest {
    @get:Rule val composeRule = createAndroidComposeRule<ComponentActivity>()

    @Before fun awaitHost() = composeRule.awaitResumedHost()

    @Test fun introductionAndPermissionRationale_areReachable() {
        var advanced = false
        composeRule.setContent {
            StepArenaTheme {
                OnboardingScreen(step = 2, onNext = { advanced = true }, onBack = {})
            }
        }
        composeRule.onNodeWithTag(OnboardingTestTags.SCREEN).assertIsDisplayed()
        composeRule.onNodeWithText("必要なときに権限を確認").assertIsDisplayed()
        composeRule.onNodeWithTag(OnboardingTestTags.NEXT).performClick()
        assertTrue(advanced)
    }
}
