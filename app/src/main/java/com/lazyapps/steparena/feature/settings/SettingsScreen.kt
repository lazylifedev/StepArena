package com.lazyapps.steparena.feature.settings

import androidx.annotation.StringRes
import androidx.compose.foundation.clickable
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.Icon
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.DirectionsWalk
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.DataUsage
import androidx.compose.material.icons.filled.HealthAndSafety
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Person
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.unit.dp
import com.lazyapps.steparena.R
import com.lazyapps.steparena.core.designsystem.component.GlassSurface
import com.lazyapps.steparena.core.designsystem.theme.StepArenaSpacing
import com.lazyapps.steparena.game.GameNotificationDispatcher
import com.lazyapps.steparena.app.StepArenaApplication
import com.lazyapps.steparena.auth.AccountAuthState
import com.lazyapps.steparena.auth.GoogleCredentialProvider
import com.lazyapps.steparena.auth.GoogleCredentialResult
import kotlinx.coroutines.launch
import com.lazyapps.steparena.backup.BackupErrorCategory
import com.lazyapps.steparena.backup.BackupState
import com.lazyapps.steparena.backup.BackupStatus
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@Composable
fun SettingsScreen(
    onProfile: () -> Unit,
    onDiagnostics: () -> Unit,
    onRecoverySettings: () -> Unit = {},
    onRecoveryHistory: () -> Unit = {},
    onDataManagement: () -> Unit = {},
    onPrivacy: () -> Unit = {},
    onTerms: () -> Unit = {},
    onLicenses: () -> Unit = {},
    onAbout: () -> Unit = {},
    onReplayOnboarding: () -> Unit = {},
) {
    val context = LocalContext.current
    val repository = (context.applicationContext as StepArenaApplication).accountAuthRepository
    val authState by repository.state.collectAsStateWithLifecycle()
    val backupRepository = (context.applicationContext as StepArenaApplication).cloudBackupRepository
    val backupState by backupRepository.state.collectAsStateWithLifecycle(initialValue = BackupState())
    val scope = rememberCoroutineScope()
    val serverClientId = stringResource(R.string.default_web_client_id)
    Column(
        Modifier.fillMaxSize().verticalScroll(androidx.compose.foundation.rememberScrollState())
            .padding(StepArenaSpacing.md).testTag("settings_list"),
        verticalArrangement = Arrangement.spacedBy(StepArenaSpacing.md),
    ) {
        Text(stringResource(R.string.settings_title), style = MaterialTheme.typography.headlineMedium)
        SettingsHeading(R.string.settings_heading_account)
        AccountSection(
            state = authState,
            onLinkGoogle = {
                if (!repository.startGoogleLink()) return@AccountSection
                val activity = context.findActivity() ?: run {
                    repository.configurationFailed()
                    return@AccountSection
                }
                scope.launch {
                    when (val result = GoogleCredentialProvider(activity).request(
                        serverClientId,
                    )) {
                        is GoogleCredentialResult.Success -> repository.linkGoogle(result.idToken)
                        GoogleCredentialResult.Cancelled -> repository.googleSelectionCancelled()
                        GoogleCredentialResult.ConfigurationError -> repository.configurationFailed()
                        GoogleCredentialResult.GeneralError -> repository.credentialFailed()
                    }
                }
            },
        )
        CloudBackupSection(
            googleLinked = authState is AccountAuthState.GoogleLinked,
            state = backupState,
            onBackupNow = { scope.launch { backupRepository.backupNow() } },
        )
        SettingsHeading(R.string.settings_heading_tracking)
        SettingRow(Icons.Default.HealthAndSafety, R.string.settings_health_connect, R.string.settings_health_connect_summary, onRecoverySettings)
        SettingRow(Icons.AutoMirrored.Filled.DirectionsWalk, R.string.settings_recovery_history, R.string.settings_recovery_history_summary, onRecoveryHistory)
        SettingRow(Icons.Default.Info, R.string.settings_diagnostics, R.string.settings_diagnostics_summary, onDiagnostics)
        SettingsHeading(R.string.settings_heading_profile)
        SettingRow(Icons.Default.Person, R.string.settings_profile, R.string.settings_profile_summary, onProfile)
        SettingsHeading(R.string.settings_heading_challenge)
        GameNotificationSetting()
        SettingRow(Icons.AutoMirrored.Filled.DirectionsWalk, R.string.settings_replay_onboarding, R.string.settings_replay_onboarding_summary, onReplayOnboarding)
        SettingsHeading(R.string.settings_heading_data)
        SettingRow(Icons.Default.DataUsage, R.string.data_management_title, R.string.settings_data_summary, onDataManagement)
        SettingsHeading(R.string.settings_heading_info)
        SettingRow(Icons.Default.Info, R.string.info_privacy_title, R.string.settings_privacy_summary, onPrivacy)
        SettingRow(Icons.Default.Info, R.string.info_terms_title, R.string.settings_terms_summary, onTerms)
        SettingRow(Icons.Default.Info, R.string.info_licenses_title, R.string.settings_licenses_summary, onLicenses)
        SettingRow(Icons.Default.Info, R.string.settings_about, R.string.settings_about_summary, onAbout)
    }
}

@Composable
internal fun CloudBackupSection(
    googleLinked: Boolean,
    state: BackupState,
    onBackupNow: () -> Unit,
) {
    val status = when {
        !googleLinked -> stringResource(R.string.backup_requires_google)
        state.status == BackupStatus.RUNNING -> stringResource(R.string.backup_running)
        state.status == BackupStatus.COMPLETE -> stringResource(R.string.backup_complete)
        state.status == BackupStatus.FAILED -> when (state.lastErrorCategory) {
            BackupErrorCategory.NETWORK -> stringResource(R.string.backup_error_network)
            BackupErrorCategory.AUTHENTICATION -> stringResource(R.string.backup_error_auth)
            BackupErrorCategory.INTEGRITY -> stringResource(R.string.backup_error_integrity)
            BackupErrorCategory.PERMISSION -> stringResource(R.string.backup_error_permission)
            else -> stringResource(R.string.backup_error_configuration)
        }
        else -> stringResource(R.string.backup_ready)
    }
    GlassSurface(Modifier.fillMaxWidth().testTag("cloud_backup_section")) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(stringResource(R.string.backup_title), style = MaterialTheme.typography.titleMedium)
            Text(status, modifier = Modifier.testTag("cloud_backup_status"))
            state.lastSuccessfulBackupAt?.let { instant ->
                val formatted = DateTimeFormatter.ofPattern("yyyy/MM/dd HH:mm")
                    .withZone(ZoneId.systemDefault()).format(instant)
                Text(stringResource(R.string.backup_last_success, formatted))
                Text(stringResource(R.string.backup_item_count, state.lastDocumentCount))
            }
            if (googleLinked) Button(
                onClick = onBackupNow,
                enabled = state.status != BackupStatus.RUNNING,
                modifier = Modifier.testTag("backup_now_button"),
            ) {
                Text(stringResource(if (state.status == BackupStatus.FAILED) R.string.retry else R.string.backup_now))
            }
        }
    }
}

@Composable
internal fun AccountSection(
    state: AccountAuthState,
    onLinkGoogle: () -> Unit,
) {
    val linked = state as? AccountAuthState.GoogleLinked
    val account = when (state) {
        is AccountAuthState.LinkingGoogle -> state.account
        is AccountAuthState.AccountConflict -> state.account
        is AccountAuthState.NetworkError -> state.account
        is AccountAuthState.ConfigurationError -> state.account
        is AccountAuthState.GeneralError -> state.account
        is AccountAuthState.GoogleLinked -> state.account
        else -> null
    }
    val busy = state is AccountAuthState.Initializing ||
        state is AccountAuthState.SigningInAnonymously || state is AccountAuthState.LinkingGoogle
    GlassSurface(Modifier.fillMaxWidth().testTag("account_section")) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                stringResource(if (linked != null) R.string.account_google_linked else R.string.account_google_unlinked),
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.testTag("account_status"),
            )
            if (linked == null) Text(stringResource(R.string.account_link_support))
            account?.displayName?.takeIf { it.isNotBlank() }?.let { Text(it) }
            account?.email?.takeIf { it.isNotBlank() }?.let { Text(it) }
            when (state) {
                is AccountAuthState.AccountConflict -> Text(stringResource(R.string.account_conflict), color = MaterialTheme.colorScheme.error)
                is AccountAuthState.NetworkError -> Text(stringResource(R.string.account_network_error), color = MaterialTheme.colorScheme.error)
                is AccountAuthState.ConfigurationError, is AccountAuthState.GeneralError ->
                    Text(stringResource(R.string.account_general_error), color = MaterialTheme.colorScheme.error)
                is AccountAuthState.Anonymous -> when (state.error) {
                    com.lazyapps.steparena.auth.AuthError.NETWORK -> Text(stringResource(R.string.account_network_error), color = MaterialTheme.colorScheme.error)
                    com.lazyapps.steparena.auth.AuthError.CONFIGURATION, com.lazyapps.steparena.auth.AuthError.GENERAL ->
                        Text(stringResource(R.string.account_general_error), color = MaterialTheme.colorScheme.error)
                    null -> Unit
                }
                else -> Unit
            }
            if (linked == null) {
                Button(
                    onClick = onLinkGoogle,
                    enabled = !busy,
                    modifier = Modifier.testTag("link_google_button"),
                ) {
                    Text(stringResource(R.string.account_link_google))
                    if (busy) {
                        Spacer(Modifier.width(8.dp))
                        CircularProgressIndicator(
                            modifier = Modifier.size(18.dp).testTag("account_progress"),
                            strokeWidth = 2.dp,
                        )
                    }
                }
            }
        }
    }
}

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}

@Composable
private fun SettingsHeading(@StringRes labelRes: Int) {
    Text(
        stringResource(labelRes),
        style = MaterialTheme.typography.titleLarge,
        modifier = Modifier.semantics { heading() },
    )
}

@Composable
private fun GameNotificationSetting() {
    val context = LocalContext.current
    val preferences = remember {
        context.getSharedPreferences(GameNotificationDispatcher.PREFERENCES, 0)
    }
    var enabled by remember {
        mutableStateOf(preferences.getBoolean(GameNotificationDispatcher.KEY_ENABLED, false))
    }
    GlassSurface(Modifier.fillMaxWidth().testTag("game_notification_setting")) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Icon(Icons.Default.Notifications, contentDescription = null)
            Column(Modifier.weight(1f)) {
                Text(stringResource(R.string.settings_game_notifications), style = MaterialTheme.typography.titleMedium)
                Text(stringResource(if (enabled) R.string.state_enabled else R.string.state_disabled))
            }
            Switch(
                checked = enabled,
                onCheckedChange = {
                    enabled = it
                    preferences.edit()
                        .putBoolean(GameNotificationDispatcher.KEY_ENABLED, it)
                        .apply()
                },
            )
        }
    }
}

@Composable
private fun SettingRow(
    icon: ImageVector,
    @StringRes labelRes: Int,
    @StringRes supportingRes: Int,
    onClick: () -> Unit,
) {
    val label = stringResource(labelRes)
    val clickLabel = stringResource(R.string.settings_open, label)
    GlassSurface(
        Modifier.fillMaxWidth().heightIn(min = 56.dp).clickable(
            onClickLabel = clickLabel,
            onClick = onClick,
        ),
    ) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Icon(icon, contentDescription = null)
            Column(Modifier.weight(1f)) {
                Text(label, style = MaterialTheme.typography.titleMedium)
                Text(stringResource(supportingRes), style = MaterialTheme.typography.bodyMedium)
            }
            Icon(Icons.Default.ChevronRight, contentDescription = null)
        }
    }
}
