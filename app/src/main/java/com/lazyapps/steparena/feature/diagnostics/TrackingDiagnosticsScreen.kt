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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import com.lazyapps.steparena.tracking.StepTrackingState
import com.lazyapps.steparena.tracking.readTrackingDiagnostics
import java.time.Duration
import java.time.Instant

object DiagnosticsTestTags { const val SCREEN = "tracking_diagnostics" }

@Composable
fun TrackingDiagnosticsScreen(state: StepTrackingState = StepTrackingState()) {
    val context = LocalContext.current
    val diagnostics = context.readTrackingDiagnostics()
    val heartbeatStale = isHeartbeatStale(state, Instant.now())
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState())
            .padding(20.dp).testTag(DiagnosticsTestTags.SCREEN),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text("計測状態の診断", style = MaterialTheme.typography.headlineMedium)
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
