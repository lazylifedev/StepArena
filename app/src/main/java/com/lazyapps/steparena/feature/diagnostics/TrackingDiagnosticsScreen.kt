package com.lazyapps.steparena.feature.diagnostics

import androidx.annotation.StringRes
import android.content.Intent
import android.provider.Settings
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.produceState
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.res.stringResource
import androidx.core.net.toUri
import com.lazyapps.steparena.tracking.StepTrackingState
import com.lazyapps.steparena.tracking.readTrackingDiagnostics
import com.lazyapps.steparena.app.StepArenaApplication
import com.lazyapps.steparena.recovery.TrackingHealthStatus
import com.lazyapps.steparena.recovery.evaluateTrackingHealth
import java.time.Duration
import java.time.Instant
import android.content.ClipData
import android.content.ClipboardManager
import android.os.Build
import com.lazyapps.steparena.BuildConfig
import com.lazyapps.steparena.release.safeDiagnosticLines
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import com.lazyapps.steparena.R

object DiagnosticsTestTags { const val SCREEN = "tracking_diagnostics" }

@Composable
fun TrackingDiagnosticsScreen(state: StepTrackingState = StepTrackingState()) {
    val context = LocalContext.current
    val app = context.applicationContext as StepArenaApplication
    val diagnostics = context.readTrackingDiagnostics()
    val healthConnectAvailability by produceState(
        initialValue = com.lazyapps.steparena.recovery.HealthConnectAvailability.UNKNOWN,
    ) { value = app.externalActivityDataSource.availability() }
    val healthConnectPermissions by produceState(initialValue = emptySet<String>()) {
        value = app.externalActivityDataSource.grantedPermissions()
    }
    val unresolvedGapCount by app.gapRecoveryRepository.observeUnresolvedCount()
        .collectAsState(initial = 0)
    val trackingHealth = evaluateTrackingHealth(state, Instant.now())
    val heartbeatStale = trackingHealth in setOf(
        TrackingHealthStatus.STALE,
        TrackingHealthStatus.STOPPED,
    )
    var diagnosticExportText by remember { mutableStateOf("") }
    val exportDiagnostics = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("text/plain"),
    ) { uri ->
        if (uri != null) runCatching {
            context.contentResolver.openOutputStream(uri)?.bufferedWriter(Charsets.UTF_8)?.use {
                it.write(diagnosticExportText)
            }
        }
    }
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState())
            .padding(20.dp).testTag(DiagnosticsTestTags.SCREEN),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text(stringResource(R.string.diagnostics_title), style = MaterialTheme.typography.headlineMedium)
        DiagnosticRow(R.string.diagnostics_health_connect, stringResource(healthConnectAvailability.labelRes()))
        DiagnosticRow(
            R.string.diagnostics_health_permission,
            stringResource(if (healthConnectPermissions.isEmpty()) R.string.state_not_granted else R.string.state_granted),
        )
        DiagnosticRow(R.string.diagnostics_heartbeat, stateText(trackingHealth.name))
        DiagnosticRow(R.string.diagnostics_unresolved_gap, unresolvedGapCount.toString())
        DiagnosticRow(R.string.diagnostics_work_manager, stringResource(R.string.diagnostics_work_manager_value))
        DiagnosticRow(R.string.diagnostics_activity_permission, stringResource(if (diagnostics.activityPermissionGranted) R.string.state_granted else R.string.state_not_granted))
        DiagnosticRow(R.string.diagnostics_notification, stringResource(if (diagnostics.notificationPermissionGranted) R.string.state_enabled else R.string.state_disabled))
        DiagnosticRow(R.string.diagnostics_step_sensor, stringResource(if (diagnostics.stepSensorAvailable) R.string.state_supported else R.string.state_unsupported))
        DiagnosticRow(R.string.diagnostics_foreground_service, stateText(state.trackingStatus.name))
        DiagnosticRow(
            R.string.diagnostics_battery,
            stringResource(if (diagnostics.batteryOptimizationIgnored) R.string.state_excluded else R.string.state_included),
            onClick = {
                val direct = Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)
                val fallback = Intent(
                    Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                    "package:${context.packageName}".toUri(),
                )
                runCatching { context.startActivity(direct) }.onFailure { context.startActivity(fallback) }
            },
        )
        val unset = stringResource(R.string.state_unset)
        DiagnosticRow(R.string.diagnostics_session_id, state.sessionId ?: unset)
        DiagnosticRow(R.string.diagnostics_sensor_baseline, state.sensorBaseline?.toString() ?: unset)
        DiagnosticRow(R.string.diagnostics_last_sensor_value, state.lastSensorValue?.toString() ?: unset)
        DiagnosticRow(R.string.diagnostics_today_steps, state.accumulatedTodaySteps.toString())
        DiagnosticRow(R.string.diagnostics_last_heartbeat, state.lastHeartbeatAt?.toString() ?: unset)
        DiagnosticRow(R.string.diagnostics_last_sensor_update, state.lastSensorEventAt?.toString() ?: unset)
        DiagnosticRow(R.string.diagnostics_last_notification, state.lastNotificationAt?.toString() ?: unset)
        DiagnosticRow(R.string.diagnostics_last_exit, state.lastExitSummary ?: stringResource(R.string.state_no_record))
        if (heartbeatStale) {
            Text(stringResource(R.string.diagnostics_service_may_be_stopped), color = MaterialTheme.colorScheme.error)
        }
        val safeDiagnostics = safeDiagnosticLines(
            mapOf(
                "appVersion" to "${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})",
                "androidVersion" to Build.VERSION.RELEASE,
                "deviceModel" to Build.MODEL,
                "databaseSchema" to "5",
                "trackingStatus" to state.trackingStatus.name,
                "trackingRequested" to state.trackingRequested.toString(),
                "stepCounterAvailable" to diagnostics.stepSensorAvailable.toString(),
                "healthConnectAvailability" to healthConnectAvailability.name,
                "healthConnectPermission" to healthConnectPermissions.isNotEmpty().toString(),
                "unresolvedGapCount" to unresolvedGapCount.toString(),
                "lastErrorCode" to state.lastStopReason?.name,
            ),
            unset,
        )
        Button(
            onClick = {
                context.getSystemService(ClipboardManager::class.java)
                    .setPrimaryClip(ClipData.newPlainText("StepArena diagnostics", safeDiagnostics))
            },
            modifier = Modifier.fillMaxWidth(),
        ) { Text(stringResource(R.string.diagnostics_copy)) }
        Button(
            onClick = {
                diagnosticExportText = safeDiagnostics
                exportDiagnostics.launch("StepArena-diagnostics.txt")
            },
            modifier = Modifier.fillMaxWidth(),
        ) { Text(stringResource(R.string.diagnostics_export)) }
    }
}

internal fun isHeartbeatStale(state: StepTrackingState, now: Instant): Boolean =
    state.trackingRequested &&
        (state.lastHeartbeatAt == null ||
            Duration.between(state.lastHeartbeatAt, now).toMinutes() > 10)

@Composable
private fun DiagnosticRow(@StringRes labelRes: Int, value: String, onClick: (() -> Unit)? = null) {
    Card(
        Modifier.fillMaxWidth().then(
            if (onClick == null) Modifier else Modifier.clickable(onClick = onClick),
        ),
    ) {
        Column(Modifier.padding(16.dp)) {
            Text(stringResource(labelRes), style = MaterialTheme.typography.titleMedium)
            Text(value, style = MaterialTheme.typography.bodyMedium)
        }
    }
}

@Composable
private fun stateText(value: String): String = value.lowercase()
    .replace('_', ' ')
