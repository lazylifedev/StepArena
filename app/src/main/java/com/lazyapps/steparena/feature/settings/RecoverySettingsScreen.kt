package com.lazyapps.steparena.feature.settings

import androidx.annotation.StringRes
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
import androidx.compose.ui.res.stringResource
import androidx.health.connect.client.PermissionController
import com.lazyapps.steparena.app.StepArenaApplication
import kotlinx.coroutines.launch
import android.content.Intent
import androidx.core.net.toUri
import com.lazyapps.steparena.recovery.HealthConnectAvailability
import com.lazyapps.steparena.R

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
        Text(stringResource(R.string.recovery_settings_title), style = MaterialTheme.typography.headlineMedium)
        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(stringResource(R.string.recovery_settings_explanation))
                Text(stringResource(R.string.recovery_settings_optional))
                Text(stringResource(R.string.recovery_settings_availability, stringResource(availability.labelRes())))
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
                            stringResource(R.string.recovery_settings_check_permission)
                        } else {
                            stringResource(R.string.recovery_settings_check_provider)
                        },
                    )
                }
            }
        }
        RecoverySwitch(
            R.string.recovery_settings_enable,
            settings.healthConnectEnabled,
        ) { update { old -> old.copy(healthConnectEnabled = it) } }
        RecoverySwitch(R.string.recovery_settings_automatic, settings.automaticRecovery) {
            update { old -> old.copy(automaticRecovery = it) }
        }
        RecoverySwitch(R.string.recovery_settings_confirm, settings.confirmBeforeRecovery) {
            update { old -> old.copy(confirmBeforeRecovery = it) }
        }
        RecoverySwitch(R.string.recovery_settings_warning, settings.trackingStopWarnings) {
            update { old -> old.copy(trackingStopWarnings = it) }
        }
        RecoverySwitch(R.string.recovery_settings_explicit_stop, settings.recoverExplicitStops) {
            update { old -> old.copy(recoverExplicitStops = it) }
        }
    }
}

@Composable
private fun RecoverySwitch(@StringRes labelRes: Int, checked: Boolean, onChecked: (Boolean) -> Unit) {
    Card(Modifier.fillMaxWidth()) {
        androidx.compose.foundation.layout.Row(
            Modifier.fillMaxWidth().padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(stringResource(labelRes), Modifier.weight(1f))
            Switch(checked = checked, onCheckedChange = onChecked)
        }
    }
}

@StringRes
private fun HealthConnectAvailability.labelRes(): Int = when (this) {
    HealthConnectAvailability.AVAILABLE -> R.string.health_connect_available
    HealthConnectAvailability.UPDATE_REQUIRED -> R.string.health_connect_update_required
    HealthConnectAvailability.PROVIDER_NOT_INSTALLED -> R.string.health_connect_not_installed
    HealthConnectAvailability.NOT_SUPPORTED -> R.string.health_connect_not_supported
    HealthConnectAvailability.UNKNOWN -> R.string.health_connect_unknown
}
