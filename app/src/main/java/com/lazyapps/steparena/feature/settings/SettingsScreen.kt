package com.lazyapps.steparena.feature.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.Switch
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
        SettingRow("Health Connect と計測復旧", onRecoverySettings)
        SettingRow("補完履歴", onRecoveryHistory)
        GameNotificationSetting()
    }
}

@Composable
private fun GameNotificationSetting() {
    val context = LocalContext.current
    val preferences = remember { context.getSharedPreferences("game_notifications", 0) }
    var enabled by remember { mutableStateOf(preferences.getBoolean("enabled", false)) }
    GlassSurface(Modifier.fillMaxWidth()) {
        androidx.compose.foundation.layout.Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Column(Modifier.weight(1f)) {
                Text("ゲーム結果の通知", style = MaterialTheme.typography.titleMedium)
                Text("対戦結果・昇格・実績などを控えめに通知します。")
            }
            Switch(
                checked = enabled,
                onCheckedChange = {
                    enabled = it
                    preferences.edit().putBoolean("enabled", it).apply()
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
