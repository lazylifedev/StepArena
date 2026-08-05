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
import com.lazyapps.steparena.tracking.NotificationStepPreviewDiagnostics
import com.lazyapps.steparena.tracking.MotionCaptureDiagnostics
import com.lazyapps.steparena.tracking.readTrackingDiagnostics
import com.lazyapps.steparena.app.StepArenaApplication
import com.lazyapps.steparena.recovery.TrackingHealthStatus
import com.lazyapps.steparena.recovery.evaluateTrackingHealth
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import android.content.ClipData
import android.content.ClipboardManager
import android.os.Build
import com.lazyapps.steparena.BuildConfig
import com.lazyapps.steparena.release.safeDiagnosticLines
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import com.lazyapps.steparena.R
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.launch

object DiagnosticsTestTags { const val SCREEN = "tracking_diagnostics" }

@Composable
fun TrackingDiagnosticsScreen(state: StepTrackingState = StepTrackingState()) {
    val context = LocalContext.current
    val app = context.applicationContext as StepArenaApplication
    val diagnostics = context.readTrackingDiagnostics()
    val notificationPreview by NotificationStepPreviewDiagnostics.snapshot.collectAsState()
    val motionDiagnostics by MotionCaptureDiagnostics.snapshot.collectAsState()
    val today = LocalDate.now()
    val zone = ZoneId.systemDefault()
    val daily by app.activityRepository.observeToday(today, zone).collectAsState(initial = null)
    val hours by app.activityRepository.observeHours(today, zone).collectAsState(initial = emptyList())
    val recoverySettings by app.recoverySettingsRepository.settings.collectAsState(
        initial = com.lazyapps.steparena.recovery.RecoverySettings(),
    )
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
    val unset = stringResource(R.string.state_unset)
    var submitState by remember { mutableStateOf<String?>(null) }
    val submitScope = rememberCoroutineScope()
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
        if (BuildConfig.FLAVOR == "qa") {
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Submit official progress to QA", style = MaterialTheme.typography.titleMedium)
                    Text("当日の整合性済み歩数をQAへ送信します。")
                    Button(onClick = {
                        submitState = "送信中…"
                        submitScope.launch {
                            submitState = runCatching {
                                app.officialProgressRepository.submitToday().let { "${it.status}: ${it.officialSteps ?: "-"}" }
                            }.getOrElse { "送信失敗: ${it.message ?: "unknown"}" }
                        }
                    }, enabled = submitState != "送信中…") { Text("QAへ送信") }
                    submitState?.let { Text(it) }
                }
            }
        }
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
        DiagnosticRow(R.string.diagnostics_sensor_registration, stringResource(if (state.stepCounterRegistered) R.string.state_registered else R.string.state_not_registered))
        DiagnosticRow(R.string.diagnostics_motion_validation, stringResource(if (motionDiagnostics.gyroscopeAvailable && motionDiagnostics.accelerationMode != "UNAVAILABLE") R.string.state_supported else R.string.state_unsupported))
        DiagnosticRow(R.string.diagnostics_gyroscope, stringResource(if (motionDiagnostics.gyroscopeAvailable) R.string.state_supported else R.string.state_unsupported))
        DiagnosticRow(R.string.diagnostics_linear_acceleration, motionDiagnostics.accelerationMode)
        DiagnosticRow(R.string.diagnostics_motion_capture, stringResource(if (motionDiagnostics.capturing) R.string.state_collecting else R.string.state_waiting))
        DiagnosticRow(R.string.diagnostics_motion_last_assessment, motionDiagnostics.lastAssessment.name)
        DiagnosticRow(R.string.diagnostics_motion_last_evaluated, relativeTime(motionDiagnostics.lastEvaluatedAt, Instant.now()))
        DiagnosticRow(R.string.diagnostics_tracking_mode, stringResource(R.string.state_real_sensor))
        DiagnosticRow(R.string.diagnostics_last_sensor_received, relativeTime(state.lastSensorEventAt, Instant.now()))
        DiagnosticRow(R.string.diagnostics_last_step_increase, relativeTime(state.lastStepIncreaseAt, Instant.now()))
        DiagnosticRow(R.string.diagnostics_current_raw, state.lastSensorValue?.toString() ?: stringResource(R.string.state_not_received))
        DiagnosticRow(R.string.diagnostics_previous_raw, state.previousSensorValue?.toString() ?: stringResource(R.string.state_not_received))
        DiagnosticRow(R.string.diagnostics_today_steps, state.accumulatedTodaySteps.toString())
        DiagnosticRow(
            R.string.diagnostics_official_steps,
            notificationPreview.officialSteps.toString(),
        )
        DiagnosticRow(
            R.string.diagnostics_pending_detector_steps,
            notificationPreview.pendingDetectorSteps.toString(),
        )
        DiagnosticRow(
            R.string.diagnostics_notification_displayed_steps,
            notificationPreview.displayedSteps.toString(),
        )
        DiagnosticRow(
            R.string.diagnostics_last_detector_received,
            relativeTime(notificationPreview.lastDetectorAt, Instant.now()),
        )
        DiagnosticRow(
            R.string.diagnostics_last_counter_received,
            relativeTime(notificationPreview.lastCounterAt, Instant.now()),
        )
        DiagnosticRow(
            R.string.diagnostics_recovered_steps,
            if (recoverySettings.healthConnectEnabled) {
                (daily?.externalRecoveredSteps ?: 0).toString()
            } else "0",
        )
        DiagnosticRow(
            R.string.diagnostics_estimated_steps,
            hours.sumOf { it.estimatedSteps }.toString(),
        )
        DiagnosticRow(
            R.string.diagnostics_today_total,
            (state.accumulatedTodaySteps +
                if (recoverySettings.healthConnectEnabled) daily?.externalRecoveredSteps ?: 0 else 0)
                .toString(),
        )
        DiagnosticRow(
            R.string.diagnostics_notification_value,
            notificationPreview.displayedSteps.toString(),
        )
        DiagnosticRow(R.string.diagnostics_home_value, state.accumulatedTodaySteps.toString())
        DiagnosticRow(R.string.diagnostics_hourly_total, hours.sumOf { it.steps }.toString())
        DiagnosticRow(R.string.diagnostics_daily_total, (daily?.steps ?: 0).toString())
        DiagnosticRow(
            R.string.diagnostics_last_daily_update,
            daily?.updatedAtEpochMillis?.let { Instant.ofEpochMilli(it).toString() } ?: unset,
        )
        DiagnosticRow(
            R.string.diagnostics_recovery_enabled,
            stringResource(
                if (recoverySettings.healthConnectEnabled) R.string.state_enabled
                else R.string.state_disabled,
            ),
        )
        DiagnosticRow(
            R.string.diagnostics_scenario_mode,
            stringResource(if (app.isolatedScenario) R.string.state_enabled else R.string.state_disabled),
        )
        val synchronized = state.accumulatedTodaySteps == hours.sumOf { it.steps } &&
            state.accumulatedTodaySteps == (daily?.steps ?: state.accumulatedTodaySteps)
        Text(
            stringResource(
                if (synchronized) R.string.diagnostics_steps_synchronized
                else R.string.diagnostics_steps_mismatch,
            ),
            color = if (synchronized) MaterialTheme.colorScheme.primary
            else MaterialTheme.colorScheme.error,
        )
        DiagnosticRow(R.string.diagnostics_foreground_service, stringResource(if (state.serviceRunning) R.string.state_running else R.string.state_stopped))
        if (BuildConfig.DEBUG) {
            DiagnosticRow(R.string.diagnostics_fake_sensor, "OFF")
        }
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
        DiagnosticRow(R.string.diagnostics_session_id, state.sessionId ?: unset)
        DiagnosticRow(R.string.diagnostics_sensor_baseline, state.sensorBaseline?.toString() ?: unset)
        DiagnosticRow(R.string.diagnostics_last_sensor_value, state.lastSensorValue?.toString() ?: unset)
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
                "databaseSchema" to "8",
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
internal fun relativeTime(value: Instant?, now: Instant): String {
    if (value == null) return stringResource(R.string.state_not_received)
    val minutes = Duration.between(value, now).toMinutes().coerceAtLeast(0)
    return if (minutes == 0L) stringResource(R.string.state_less_than_minute)
    else stringResource(R.string.state_minutes_ago, minutes)
}

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
