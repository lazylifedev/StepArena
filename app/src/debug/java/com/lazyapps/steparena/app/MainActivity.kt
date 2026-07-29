package com.lazyapps.steparena.app

import android.Manifest
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.view.KeyEvent
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.lazyapps.steparena.app.navigation.StepArenaApp
import com.lazyapps.steparena.core.designsystem.theme.StepArenaTheme
import com.lazyapps.steparena.feature.home.HomeAction
import com.lazyapps.steparena.feature.home.HomeViewModel
import com.lazyapps.steparena.feature.onboarding.OnboardingScreen
import com.lazyapps.steparena.service.tracking.StepTrackingService
import com.lazyapps.steparena.tracking.DiagnosticLogEntry
import com.lazyapps.steparena.tracking.DiagnosticLogRepository
import com.lazyapps.steparena.tracking.StepTrackingState
import com.lazyapps.steparena.tracking.TrackingStateRepository
import com.lazyapps.steparena.tracking.reconcileForceStop
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    private var trackingState by mutableStateOf(StepTrackingState())
    private var debugMenuVisible by mutableStateOf(false)
    private var diagnosticEntries by mutableStateOf(emptyList<DiagnosticLogEntry>())
    private var startAfterPermission = false
    private var onboardingPermissionStep: Int? = null
    private var homeViewModel: HomeViewModel? = null
    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) { result ->
        if (startAfterPermission && result[Manifest.permission.ACTIVITY_RECOGNITION] == true) {
            homeViewModel?.startTracking()
        }
        if (onboardingPermissionStep == 2 &&
            result[Manifest.permission.ACTIVITY_RECOGNITION] == true
        ) {
            lifecycleScope.launch {
                TrackingStateRepository(applicationContext).update {
                    it.copy(onboardingStep = 3)
                }
            }
        }
        onboardingPermissionStep = null
        startAfterPermission = false
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val repository = TrackingStateRepository(applicationContext)
        lifecycleScope.launch { reconcileForceStop(applicationContext, repository) }
        lifecycleScope.launch { repository.state.collect { trackingState = it } }
        enableEdgeToEdge()
        WindowCompat.getInsetsController(window, window.decorView).isAppearanceLightStatusBars = false
        setContent {
            StepArenaTheme {
                val vm: HomeViewModel = viewModel()
                homeViewModel = vm
                val uiState by vm.uiState.collectAsStateWithLifecycle()
                if (!trackingState.onboardingComplete) {
                    OnboardingScreen(
                        step = trackingState.onboardingStep,
                        onNext = {
                            val step = trackingState.onboardingStep
                            if (step == 2) {
                                onboardingPermissionStep = step
                                requestPermissions(false, false)
                            } else {
                                if (step == 3) requestPermissions(false, true)
                                lifecycleScope.launch {
                                    repository.update {
                                        if (step >= 6) {
                                            it.copy(onboardingComplete = true, onboardingStep = 6)
                                        } else {
                                            it.copy(onboardingStep = step + 1)
                                        }
                                    }
                                }
                            }
                        },
                        onBack = {
                            lifecycleScope.launch {
                                repository.update {
                                    it.copy(onboardingStep = (it.onboardingStep - 1).coerceAtLeast(0))
                                }
                            }
                        },
                    )
                } else {
                    StepArenaApp(
                        homeUiState = uiState,
                        trackingState = trackingState,
                        onHomeAction = { action ->
                            if (action == HomeAction.StartSession) {
                                requestPermissions(true, true)
                            } else {
                                vm.onAction(action)
                            }
                        },
                    )
                }
                if (debugMenuVisible) {
                    DebugTrackingSheet(
                        state = trackingState,
                        entries = diagnosticEntries,
                        onDismiss = { debugMenuVisible = false },
                        onRefresh = {
                            diagnosticEntries = DiagnosticLogRepository(applicationContext).read()
                        },
                        onFakeValue = ::sendFakeSensor,
                    )
                }
            }
        }
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean {
        if (keyCode == KeyEvent.KEYCODE_VOLUME_DOWN && event.repeatCount == 0) {
            event.startTracking()
            return true
        }
        return super.onKeyDown(keyCode, event)
    }

    override fun onKeyLongPress(keyCode: Int, event: KeyEvent): Boolean {
        if (keyCode == KeyEvent.KEYCODE_VOLUME_DOWN) {
            diagnosticEntries = DiagnosticLogRepository(applicationContext).read()
            debugMenuVisible = true
            return true
        }
        return super.onKeyLongPress(keyCode, event)
    }

    private fun requestPermissions(startTracking: Boolean, includeNotification: Boolean) {
        val permissions = buildList {
            add(Manifest.permission.ACTIVITY_RECOGNITION)
            if (includeNotification && Build.VERSION.SDK_INT >= 33) {
                add(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
        startAfterPermission = startTracking
        permissionLauncher.launch(permissions.toTypedArray())
    }

    private fun sendFakeSensor(value: Float) {
        startService(
            Intent(this, StepTrackingService::class.java)
                .setAction(StepTrackingService.debugAction())
                .putExtra(StepTrackingService.debugValueExtra(), value),
        )
    }
}

object DebugTrackingTestTags {
    const val SHEET = "debug_tracking_sheet"
    const val LOG_LIST = "diagnostic_log_list"
    const val FAKE_SENSOR = "fake_sensor_controls"
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DebugTrackingSheet(
    state: StepTrackingState,
    entries: List<DiagnosticLogEntry>,
    onDismiss: () -> Unit,
    onRefresh: () -> Unit,
    onFakeValue: (Float) -> Unit,
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        modifier = Modifier.testTag(DebugTrackingTestTags.SHEET),
    ) {
        LazyColumn(
            Modifier.fillMaxWidth().padding(horizontal = 20.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            item {
                Text("Phase 2.1 計測診断", style = MaterialTheme.typography.titleLarge)
                DiagnosticText("session", state.sessionId)
                DiagnosticText("status", state.trackingStatus.name)
                DiagnosticText("trackingRequested", state.trackingRequested.toString())
                DiagnosticText("baseline", state.sensorBaseline?.toString())
                DiagnosticText("last sensor", state.lastSensorValue?.toString())
                DiagnosticText("today steps", state.accumulatedTodaySteps.toString())
                DiagnosticText("heartbeat", state.lastHeartbeatAt?.toString())
                DiagnosticText("notification", state.lastNotificationAt?.toString())
                DiagnosticText("previous exit", state.lastExitSummary)
            }
            item {
                Column(
                    Modifier.fillMaxWidth().testTag(DebugTrackingTestTags.FAKE_SENSOR),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Text("Fake Sensor（選択時は実センサーを解除）")
                    listOf(
                        "通常増加 100" to 100f,
                        "同一値 100" to 100f,
                        "値低下 20" to 20f,
                        "大幅増加 20000" to 20_000f,
                        "NaN" to Float.NaN,
                        "+Infinity" to Float.POSITIVE_INFINITY,
                        "-Infinity" to Float.NEGATIVE_INFINITY,
                    ).forEach { (label, value) ->
                        OutlinedButton(onClick = { onFakeValue(value) }) { Text(label) }
                    }
                }
            }
            item {
                Button(onClick = onRefresh) { Text("診断ログを更新") }
                Text(
                    "診断ログ（最大 ${DiagnosticLogRepository.MAX_ENTRIES} 件）",
                    modifier = Modifier.testTag(DebugTrackingTestTags.LOG_LIST),
                )
            }
            items(entries.takeLast(30).reversed()) {
                Text("${it.timestamp} ${it.event} steps=${it.todaySteps} delta=${it.delta ?: "-"}")
            }
        }
    }
}

@Composable
private fun DiagnosticText(label: String, value: String?) {
    Text("$label: ${value ?: "未設定"}")
}
