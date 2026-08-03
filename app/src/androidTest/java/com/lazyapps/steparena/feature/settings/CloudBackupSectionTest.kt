package com.lazyapps.steparena.feature.settings

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import com.lazyapps.steparena.backup.*
import com.lazyapps.steparena.core.designsystem.theme.StepArenaTheme
import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class CloudBackupSectionTest {
    @get:Rule val compose = createComposeRule()

    @Test fun unlinkedUserHasNoBackupAction() {
        compose.setContent { StepArenaTheme { CloudBackupSection(false, BackupState()) {} } }
        compose.onNodeWithTag("cloud_backup_status").assertIsDisplayed()
        compose.onNodeWithTag("backup_now_button").assertDoesNotExist()
    }

    @Test fun linkedUserCanRequestBackup() {
        var clicks = 0
        compose.setContent { StepArenaTheme { CloudBackupSection(true, BackupState()) { clicks++ } } }
        compose.onNodeWithTag("backup_now_button").assertIsEnabled().performClick()
        assertEquals(1, clicks)
    }

    @Test fun runningBackupDisablesDuplicateAction() {
        compose.setContent { StepArenaTheme { CloudBackupSection(true, BackupState(status = BackupStatus.RUNNING)) {} } }
        compose.onNodeWithTag("backup_now_button").assertIsNotEnabled()
    }

    @Test fun completedBackupShowsPersistedSummary() {
        val state = BackupState(
            status = BackupStatus.COMPLETE,
            lastSuccessfulBackupAt = Instant.parse("2026-08-03T00:00:00Z"),
            initialBackupCompleted = true,
            lastDocumentCount = 12,
        )
        compose.setContent { StepArenaTheme { CloudBackupSection(true, state) {} } }
        compose.onNodeWithTag("cloud_backup_status").assertIsDisplayed()
        compose.onNodeWithTag("backup_now_button").assertIsEnabled()
    }
}
