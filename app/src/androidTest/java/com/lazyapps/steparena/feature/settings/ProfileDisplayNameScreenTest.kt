package com.lazyapps.steparena.feature.settings

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import com.lazyapps.steparena.core.designsystem.theme.StepArenaTheme
import com.lazyapps.steparena.test.awaitResumedHost
import org.junit.Before
import org.junit.Rule
import org.junit.Test

class ProfileDisplayNameScreenTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

    @Before fun awaitHost() = compose.awaitResumedHost()

    @Test fun displayNameFieldIsTheFirstEditableProfileSetting() {
        compose.setContent {
            StepArenaTheme { ProfileSettingsScreen() }
        }

        compose.onNodeWithTag("profile_display_name").assertIsDisplayed()
    }
}
