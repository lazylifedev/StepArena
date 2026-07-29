package com.lazyapps.steparena.app

import android.Manifest
import android.content.Intent
import android.os.Bundle
import android.os.Build
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.core.view.WindowCompat
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.lazyapps.steparena.app.navigation.StepArenaApp
import com.lazyapps.steparena.core.designsystem.theme.StepArenaTheme
import com.lazyapps.steparena.feature.home.HomeViewModel
import com.lazyapps.steparena.feature.home.HomeAction
import com.lazyapps.steparena.feature.onboarding.OnboardingScreen
import com.lazyapps.steparena.tracking.StepTrackingState
import com.lazyapps.steparena.tracking.TrackingStateRepository
import com.lazyapps.steparena.tracking.reconcileForceStop
import com.lazyapps.steparena.game.GameNotificationDispatcher
import kotlinx.coroutines.launch
import com.lazyapps.steparena.release.ONBOARDING_VERSION

class MainActivity : ComponentActivity() {
    private var trackingState by mutableStateOf(StepTrackingState())
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
                            requestPermissions(startTracking = true, includeNotification = true)
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
                                requestPermissions(startTracking = true, includeNotification = true)
                            } else {
                                vm.onAction(action)
                            }
                        },
                        initialRoute = initialGameRoute,
                        onReplayOnboarding = {
                            lifecycleScope.launch { repository.update { it.copy(onboardingComplete = false, onboardingStep = 0) } }
                        },
                        onAllDataDeleted = { recreate() },
                    )
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        initialGameRoute = notificationRoute(intent)
    }

    private fun notificationRoute(intent: Intent): String {
        val route = intent.getStringExtra(GameNotificationDispatcher.EXTRA_DESTINATION)
        return route?.takeIf { it in setOf("match", "rank", "achievements", "league", "season") } ?: "home"
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
}
