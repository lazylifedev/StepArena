package com.lazyapps.steparena.feature.settings

import androidx.annotation.StringRes
import androidx.compose.foundation.clickable
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.Icon
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
    Column(
        Modifier.fillMaxSize().verticalScroll(androidx.compose.foundation.rememberScrollState())
            .padding(StepArenaSpacing.md).testTag("settings_list"),
        verticalArrangement = Arrangement.spacedBy(StepArenaSpacing.md),
    ) {
        Text(stringResource(R.string.settings_title), style = MaterialTheme.typography.headlineMedium)
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
