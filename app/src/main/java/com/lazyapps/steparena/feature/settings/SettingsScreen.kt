package com.lazyapps.steparena.feature.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.Icon
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.DataUsage
import androidx.compose.material.icons.filled.DirectionsWalk
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
        SettingsHeading("計測")
        SettingRow(Icons.Default.HealthAndSafety, "Health Connect", "未接続・任意の歩数補完", onRecoverySettings)
        SettingRow(Icons.Default.DirectionsWalk, "補完履歴", "補完された歩数の確認", onRecoveryHistory)
        SettingRow(Icons.Default.Info, stringResource(R.string.settings_diagnostics), "計測状態と権限の確認", onDiagnostics)
        SettingsHeading("プロフィール・計算")
        SettingRow(Icons.Default.Person, stringResource(R.string.settings_profile), "歩幅と消費カロリーの設定", onProfile)
        SettingsHeading("チャレンジ")
        GameNotificationSetting()
        SettingRow(Icons.Default.DirectionsWalk, "チャレンジ説明をもう一度見る", "端末内パートナーと歩数ルール", onReplayOnboarding)
        SettingsHeading("データ管理")
        SettingRow(Icons.Default.DataUsage, "データ管理", "書き出し・初期化・全削除", onDataManagement)
        SettingsHeading("アプリ情報")
        SettingRow(Icons.Default.Info, "プライバシーポリシー", "データの取り扱い", onPrivacy)
        SettingRow(Icons.Default.Info, "利用上の注意・免責", "安全に利用するための情報", onTerms)
        SettingRow(Icons.Default.Info, "オープンソースライセンス", "利用しているソフトウェア", onLicenses)
        SettingRow(Icons.Default.Info, "バージョンとアプリ情報", "StepArenaについて", onAbout)
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
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Icon(Icons.Default.Notifications, contentDescription = null)
            Column(Modifier.weight(1f)) {
                Text("チャレンジ記録の通知", style = MaterialTheme.typography.titleMedium)
                Text("目標達成・歩行ランク・達成記録を通知（22:00〜8:00を除く）")
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
private fun SettingRow(icon: ImageVector, label: String, supporting: String, onClick: () -> Unit) {
    GlassSurface(
        Modifier.fillMaxWidth().heightIn(min = 56.dp).clickable(
            onClickLabel = "${label}を開く",
            onClick = onClick,
        ),
    ) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Icon(icon, contentDescription = null)
            Column(Modifier.weight(1f)) {
                Text(label, style = MaterialTheme.typography.titleMedium)
                Text(supporting, style = MaterialTheme.typography.bodyMedium)
            }
            Icon(Icons.Default.ChevronRight, contentDescription = null)
        }
    }
}
