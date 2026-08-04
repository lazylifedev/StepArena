package com.lazyapps.steparena.feature.settings

import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.junit4.createComposeRule
import com.lazyapps.steparena.auth.AccountAuthState
import com.lazyapps.steparena.auth.AccountProfile
import com.lazyapps.steparena.core.designsystem.theme.StepArenaTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class AccountSectionTest {
    @get:Rule val compose = createComposeRule()

    @Test fun anonymousState_isAccessibleAndActionable() {
        var clicks = 0
        compose.setContent {
            StepArenaTheme {
                AccountSection(
                    state = AccountAuthState.Anonymous(),
                    onLinkGoogle = { clicks++ },
                )
            }
        }
        compose.onNodeWithText("Googleアカウント未連携").assertIsDisplayed()
        compose.onNodeWithTag("link_google_button").assertIsEnabled().assertHasClickAction().performClick()
        assertEquals(1, clicks)
    }

    @Test fun linkingState_disablesButtonAndShowsProgress() {
        compose.setContent {
            StepArenaTheme {
                AccountSection(state = AccountAuthState.LinkingGoogle(anonymous()))
            }
        }
        compose.onNodeWithTag("link_google_button").assertIsNotEnabled()
        compose.onNodeWithTag("account_progress", useUnmergedTree = true).assertIsDisplayed()
    }

    @Test fun linkedState_showsProfileWithoutLinkButton() {
        val linked = AccountProfile("not-shown", false, "表示名", "masked@example.invalid", providers = setOf("google.com"))
        compose.setContent {
            StepArenaTheme {
                AccountSection(state = AccountAuthState.GoogleLinked(linked))
            }
        }
        compose.onNodeWithText("Googleアカウント連携済み").assertIsDisplayed()
        compose.onNodeWithText("表示名").assertIsDisplayed()
        compose.onNodeWithTag("link_google_button").assertDoesNotExist()
    }

    @Test fun conflict_isShownAndExplicitActionsAreAvailable() {
        var signInClicks = 0
        var cancelClicks = 0
        compose.setContent {
            StepArenaTheme {
                AccountSection(
                    state = AccountAuthState.AccountConflict(anonymous()),
                    onSignInExisting = { signInClicks++ },
                    onCancelConflict = { cancelClicks++ },
                )
            }
        }
        compose.onNodeWithTag("account_conflict_title").assertIsDisplayed()
        compose.onNodeWithText("既存アカウントでログイン").assertIsDisplayed()
        compose.onNodeWithTag("sign_in_existing_button").assertIsEnabled().performClick()
        compose.onNodeWithTag("account_conflict_cancel").assertIsEnabled().performClick()
        assertEquals(1, signInClicks)
        assertEquals(1, cancelClicks)
    }

    @Test fun existingSignIn_disablesBothActionsAndShowsProgress() {
        compose.setContent {
            StepArenaTheme {
                AccountSection(state = AccountAuthState.SigningIntoExistingAccount(anonymous()))
            }
        }
        compose.onNodeWithTag("sign_in_existing_button").assertIsNotEnabled()
        compose.onNodeWithTag("account_conflict_cancel").assertIsNotEnabled()
        compose.onNodeWithTag("account_progress", useUnmergedTree = true).assertIsDisplayed()
    }

    private fun anonymous() = AccountProfile("not-shown", true)
}
