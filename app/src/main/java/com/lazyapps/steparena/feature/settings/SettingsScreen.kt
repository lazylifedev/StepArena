package com.lazyapps.steparena.feature.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.verticalScroll
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
        SettingsHeading("計測")
        SettingRow("Health Connectと計測復旧", onRecoverySettings)
        SettingRow("補完履歴", onRecoveryHistory)
        SettingRow(stringResource(R.string.settings_diagnostics), onDiagnostics)
        SettingsHeading("プロフィール・計算")
        SettingRow(stringResource(R.string.settings_profile), onProfile)
        SettingsHeading("ゲーム")
        GameNotificationSetting()
        SettingRow("ゲーム説明をもう一度見る", onReplayOnboarding)
        SettingsHeading("データ管理")
        SettingRow("使用状況・書き出し・削除", onDataManagement)
        SettingsHeading("アプリ情報")
        SettingRow("プライバシーポリシー", onPrivacy)
        SettingRow("利用上の注意・免責", onTerms)
        SettingRow("オープンソースライセンス", onLicenses)
        SettingRow("バージョンとアプリ情報", onAbout)
    }
}

@Composable
private fun SettingsHeading(label: String) {
    Text(
        label,
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
    GlassSurface(Modifier.fillMaxWidth().heightIn(min = 48.dp).clickable(onClick = onClick)) {
        Text(label, style = MaterialTheme.typography.titleMedium)
    }
}
