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
import com.lazyapps.steparena.BuildConfig
import com.lazyapps.steparena.feature.game.RealUserChallengeScreen
import com.lazyapps.steparena.core.designsystem.theme.StepArenaTheme
import com.lazyapps.steparena.feature.home.HomeAction
import com.lazyapps.steparena.feature.home.HomeViewModel
import com.lazyapps.steparena.feature.onboarding.OnboardingScreen
import com.lazyapps.steparena.game.DebugGameController
import com.lazyapps.steparena.game.DebugGameScenario
import com.lazyapps.steparena.game.DebugGameScreen
import com.lazyapps.steparena.game.GameNotificationDispatcher
import com.lazyapps.steparena.app.navigation.canonicalGameRoute
import com.lazyapps.steparena.game.DebugGameMaintenanceWorker
import com.lazyapps.steparena.game.GameMaintenanceWorker
import com.lazyapps.steparena.recovery.TrackingHealthWorker
import androidx.work.WorkManager
import com.lazyapps.steparena.service.tracking.StepTrackingService
import com.lazyapps.steparena.tracking.DiagnosticLogEntry
import com.lazyapps.steparena.tracking.DiagnosticLogRepository
import com.lazyapps.steparena.tracking.StepTrackingState
import com.lazyapps.steparena.tracking.TrackingStateRepository
import com.lazyapps.steparena.tracking.reconcileForceStop
import kotlinx.coroutines.launch
import com.lazyapps.steparena.release.ONBOARDING_VERSION
import com.lazyapps.steparena.service.tracking.TrackingServiceReconciler

class MainActivity : ComponentActivity() {
    private var trackingState by mutableStateOf(StepTrackingState())
    private var debugMenuVisible by mutableStateOf(false)
    private var debugGameVisible by mutableStateOf(false)
    private var diagnosticEntries by mutableStateOf(emptyList<DiagnosticLogEntry>())
    private var startAfterPermission = false
    private var onboardingPermissionStep: Int? = null
    private var homeViewModel: HomeViewModel? = null
    private var initialGameRoute by mutableStateOf("home")
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
        initialGameRoute = notificationRoute(intent)
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
                            lifecycleScope.launch {
                                repository.update { it.copy(onboardingStep = (step + 1).coerceAtMost(4)) }
                            }
                        },
                        onStartTracking = {
                            lifecycleScope.launch {
                                repository.update {
                                    it.copy(
                                        onboardingComplete = true, onboardingStep = 4,
                                        onboardingVersion = ONBOARDING_VERSION,
                                        trackingExplanationSeen = true,
                                        notificationExplanationSeen = true,
                                        healthConnectExplanationSeen = true,
                                        gameRulesExplanationSeen = true,
                                    )
                                }
                            }
                            requestPermissions(true, true)
                        },
                        onLater = {
                            lifecycleScope.launch {
                                repository.update {
                                    it.copy(
                                        onboardingComplete = true, onboardingStep = 4,
                                        onboardingVersion = ONBOARDING_VERSION,
                                        trackingExplanationSeen = true,
                                        notificationExplanationSeen = true,
                                        healthConnectExplanationSeen = true,
                                        gameRulesExplanationSeen = true,
                                    )
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
                        initialRoute = initialGameRoute,
                        environmentBanner = if (
                            (application as DebugStepArenaApplication).isIsolatedScenario
                        ) "隔離テストデータ" else null,
                        onReplayOnboarding = {
                            lifecycleScope.launch { repository.update { it.copy(onboardingComplete = false, onboardingStep = 0) } }
                        },
                        onAllDataDeleted = { recreate() },
                        challengeContent = if (BuildConfig.FLAVOR == "qa") {
                            { RealUserChallengeScreen() }
                        } else null,
                    )
                }
                if (debugGameVisible) {
                    DebugGameScreen(
                        isolated = (application as DebugStepArenaApplication).isIsolatedScenario,
                        onClose = { debugGameVisible = false },
                        onRun = ::runDebugGameScenario,
                        onStartIsolated = { changeDebugMode(DebugDataMode.ISOLATED_SCENARIO) },
                        onReturnNormal = { changeDebugMode(DebugDataMode.NORMAL_DATA) },
                    )
                } else if (debugMenuVisible) {
                    DebugTrackingSheet(
                        state = trackingState,
                        entries = diagnosticEntries,
                        onDismiss = { debugMenuVisible = false },
                        onRefresh = {
                            diagnosticEntries = DiagnosticLogRepository(applicationContext).read()
                        },
                        onFakeValue = ::sendFakeSensor,
                        onOpenGame = {
                            debugMenuVisible = false
                            debugGameVisible = true
                        },
                    )
                }
            }
        }
    }

    override fun onStart() {
        super.onStart()
        lifecycleScope.launch {
            TrackingServiceReconciler(applicationContext).reconcileForeground()
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

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        initialGameRoute = notificationRoute(intent)
    }

    private fun notificationRoute(intent: Intent): String {
        val area = intent.getStringExtra(GameNotificationDispatcher.EXTRA_DATA_AREA)
        if (area == "debug_scenario" &&
            !(application as DebugStepArenaApplication).isIsolatedScenario
        ) {
            debugGameVisible = true
            return "home"
        }
        return canonicalGameRoute(intent.getStringExtra(GameNotificationDispatcher.EXTRA_DESTINATION))
    }

    private fun runDebugGameScenario(scenario: DebugGameScenario) {
        lifecycleScope.launch {
            DebugGameController(application as DebugStepArenaApplication).run(scenario)
        }
    }

    private fun changeDebugMode(mode: DebugDataMode) {
        lifecycleScope.launch {
            val app = application as DebugStepArenaApplication
            app.debugStateStore.setMode(mode)
            if (mode == DebugDataMode.ISOLATED_SCENARIO) {
                WorkManager.getInstance(this@MainActivity)
                    .cancelUniqueWork(GameMaintenanceWorker.UNIQUE_WORK)
                WorkManager.getInstance(this@MainActivity)
                    .cancelUniqueWork(TrackingHealthWorker.UNIQUE_NAME)
                DebugGameMaintenanceWorker.schedule(this@MainActivity)
            } else {
                DebugGameMaintenanceWorker.cancel(this@MainActivity)
                GameMaintenanceWorker.schedule(this@MainActivity)
                TrackingHealthWorker.schedule(this@MainActivity)
            }
            viewModelStore.clear()
            recreate()
        }
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
    onOpenGame: () -> Unit,
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
                Button(onClick = onOpenGame, modifier = Modifier.fillMaxWidth()) {
                    Text("開発用ゲームシナリオ")
                }
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
