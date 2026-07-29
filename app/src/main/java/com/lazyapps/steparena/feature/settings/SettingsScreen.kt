package com.lazyapps.steparena.feature.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
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
) {
    Column(
        Modifier.fillMaxSize().padding(StepArenaSpacing.md).testTag("settings_list"),
        verticalArrangement = Arrangement.spacedBy(StepArenaSpacing.md),
    ) {
        Text(stringResource(R.string.settings_title), style = MaterialTheme.typography.headlineMedium)
        SettingRow(stringResource(R.string.settings_profile), onProfile)
        SettingRow(stringResource(R.string.settings_diagnostics), onDiagnostics)
        SettingRow("Health Connectと計測復旧", onRecoverySettings)
        SettingRow("補完履歴", onRecoveryHistory)
        GameNotificationSetting()
    }
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
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Column(Modifier.weight(1f)) {
                Text("ゲーム結果の通知", style = MaterialTheme.typography.titleMedium)
                Text("対戦・昇格・実績・リーグ・シーズンを通知します（22:00〜8:00を除く）")
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
private fun SettingRow(label: String, onClick: () -> Unit) {
    GlassSurface(Modifier.fillMaxWidth().clickable(onClick = onClick)) {
        Text(label, style = MaterialTheme.typography.titleMedium)
    }
}
