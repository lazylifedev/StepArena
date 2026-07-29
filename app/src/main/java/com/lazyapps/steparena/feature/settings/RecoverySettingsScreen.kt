package com.lazyapps.steparena.feature.settings

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.produceState
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.health.connect.client.PermissionController
import com.lazyapps.steparena.app.StepArenaApplication
import kotlinx.coroutines.launch
import android.content.Intent
import androidx.core.net.toUri
import com.lazyapps.steparena.recovery.HealthConnectAvailability

@Composable
fun RecoverySettingsScreen() {
    val context = LocalContext.current
    val app = context.applicationContext as StepArenaApplication
    val settings by app.recoverySettingsRepository.settings.collectAsState(
        initial = com.lazyapps.steparena.recovery.RecoverySettings(),
    )
    val scope = rememberCoroutineScope()
    val availability by produceState(initialValue = HealthConnectAvailability.UNKNOWN) {
        value = app.externalActivityDataSource.availability()
    }
    val permissionLauncher = rememberLauncherForActivityResult(
        PermissionController.createRequestPermissionResultContract(),
    ) { }
    fun update(transform: (com.lazyapps.steparena.recovery.RecoverySettings) ->
        com.lazyapps.steparena.recovery.RecoverySettings
    ) {
        scope.launch { app.recoverySettingsRepository.update(transform(settings)) }
    }

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text("Health Connect と計測復旧", style = MaterialTheme.typography.headlineMedium)
        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    "StepArenaが停止していた時間の歩数を補うため、" +
                        "Health Connectの歩数データを読み取ります。",
                )
                Text(
                    "Health Connectの使用は任意です。許可しなくても通常の歩数計測は利用できます。",
                )
                Text("利用可能性: ${availability.name}")
                Button(
                    onClick = {
                        if (availability == HealthConnectAvailability.AVAILABLE) {
                            permissionLauncher.launch(
                                app.externalActivityDataSource.requiredPermissions(),
                            )
                        } else {
                            val market = Intent(
                                Intent.ACTION_VIEW,
                                "market://details?id=com.google.android.apps.healthdata".toUri(),
                            )
                            val web = Intent(
                                Intent.ACTION_VIEW,
                                "https://play.google.com/store/apps/details?id=com.google.android.apps.healthdata"
                                    .toUri(),
                            )
                            runCatching { context.startActivity(market) }
                                .onFailure { runCatching { context.startActivity(web) } }
                        }
                    },
                ) {
                    Text(
                        if (availability == HealthConnectAvailability.AVAILABLE) {
                            "歩数の読取権限を確認"
                        } else {
                            "Health Connectを確認"
                        },
                    )
                }
            }
        }
        RecoverySwitch(
            "Health Connect補完を使用",
            settings.healthConnectEnabled,
        ) { update { old -> old.copy(healthConnectEnabled = it) } }
        RecoverySwitch("自動補完", settings.automaticRecovery) {
            update { old -> old.copy(automaticRecovery = it) }
        }
        RecoverySwitch("補完前に確認", settings.confirmBeforeRecovery) {
            update { old -> old.copy(confirmBeforeRecovery = it) }
        }
        RecoverySwitch("計測停止警告", settings.trackingStopWarnings) {
            update { old -> old.copy(trackingStopWarnings = it) }
        }
        RecoverySwitch("明示停止区間を補完", settings.recoverExplicitStops) {
            update { old -> old.copy(recoverExplicitStops = it) }
        }
    }
}

@Composable
private fun RecoverySwitch(label: String, checked: Boolean, onChecked: (Boolean) -> Unit) {
    Card(Modifier.fillMaxWidth()) {
        androidx.compose.foundation.layout.Row(
            Modifier.fillMaxWidth().padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(label, Modifier.weight(1f))
            Switch(checked = checked, onCheckedChange = onChecked)
        }
    }
}
