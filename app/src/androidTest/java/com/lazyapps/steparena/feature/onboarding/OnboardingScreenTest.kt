package com.lazyapps.steparena.feature.onboarding

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.lazyapps.steparena.core.designsystem.theme.StepArenaTheme
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class OnboardingScreenTest {
    @get:Rule val composeRule = createComposeRule()

    @Test fun introductionAndPermissionRationale_areReachable() {
        var advanced = false
        composeRule.setContent {
            StepArenaTheme {
                OnboardingScreen(step = 2, onNext = { advanced = true }, onBack = {})
            }
        }
        composeRule.onNodeWithTag(OnboardingTestTags.SCREEN).assertIsDisplayed()
        composeRule.onNodeWithText("身体活動へのアクセス").assertIsDisplayed()
        composeRule.onNodeWithTag(OnboardingTestTags.NEXT).performClick()
        assertTrue(advanced)
    }
}
