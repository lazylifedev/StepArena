package com.lazyapps.steparena.feature.diagnostics

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
        Text("計測状態の診断", style = MaterialTheme.typography.headlineMedium)
        DiagnosticRow("Health Connect利用可能性", healthConnectAvailability.name)
        DiagnosticRow(
            "Health Connect歩数権限",
            if (healthConnectPermissions.isEmpty()) "未許可" else "許可済み",
        )
        DiagnosticRow("Heartbeat判定", trackingHealth.name)
        DiagnosticRow("未解決gap", unresolvedGapCount.toString())
        DiagnosticRow("WorkManager", "tracking-health-monitor（実行時刻は保証されません）")
        DiagnosticRow("身体活動権限", if (diagnostics.activityPermissionGranted) "許可" else "未許可")
        DiagnosticRow("通知", if (diagnostics.notificationPermissionGranted) "有効" else "無効")
        DiagnosticRow("歩数センサー", if (diagnostics.stepSensorAvailable) "対応" else "非対応")
        DiagnosticRow("Foreground Service", state.trackingStatus.name)
        DiagnosticRow(
            "バッテリー最適化",
            if (diagnostics.batteryOptimizationIgnored) "対象外" else "対象",
            onClick = {
                val direct = Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)
                val fallback = Intent(
                    Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                    "package:${context.packageName}".toUri(),
                )
                runCatching { context.startActivity(direct) }.onFailure { context.startActivity(fallback) }
            },
        )
        DiagnosticRow("Service session ID", state.sessionId ?: "未設定")
        DiagnosticRow("sensor baseline", state.sensorBaseline?.toString() ?: "未設定")
        DiagnosticRow("last sensor value", state.lastSensorValue?.toString() ?: "未設定")
        DiagnosticRow("当日歩数", state.accumulatedTodaySteps.toString())
        DiagnosticRow("最終Heartbeat", state.lastHeartbeatAt?.toString() ?: "未設定")
        DiagnosticRow("最終センサー更新", state.lastSensorEventAt?.toString() ?: "未設定")
        DiagnosticRow("最終通知更新", state.lastNotificationAt?.toString() ?: "未設定")
        DiagnosticRow("前回プロセス終了", state.lastExitSummary ?: "記録なし")
        if (heartbeatStale) {
            Text("Serviceが停止している可能性があります", color = MaterialTheme.colorScheme.error)
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
        )
        Button(
            onClick = {
                context.getSystemService(ClipboardManager::class.java)
                    .setPrimaryClip(ClipData.newPlainText("StepArena diagnostics", safeDiagnostics))
            },
            modifier = Modifier.fillMaxWidth(),
        ) { Text("診断情報をコピー") }
        Button(
            onClick = {
                diagnosticExportText = safeDiagnostics
                exportDiagnostics.launch("StepArena-diagnostics.txt")
            },
            modifier = Modifier.fillMaxWidth(),
        ) { Text("診断情報を書き出す") }
    }
}

internal fun isHeartbeatStale(state: StepTrackingState, now: Instant): Boolean =
    state.trackingRequested &&
        (state.lastHeartbeatAt == null ||
            Duration.between(state.lastHeartbeatAt, now).toMinutes() > 10)

@Composable
private fun DiagnosticRow(label: String, value: String, onClick: (() -> Unit)? = null) {
    Card(
        Modifier.fillMaxWidth().then(
            if (onClick == null) Modifier else Modifier.clickable(onClick = onClick),
        ),
    ) {
        Column(Modifier.padding(16.dp)) {
            Text(label, style = MaterialTheme.typography.titleMedium)
            Text(value, style = MaterialTheme.typography.bodyMedium)
        }
    }
}
