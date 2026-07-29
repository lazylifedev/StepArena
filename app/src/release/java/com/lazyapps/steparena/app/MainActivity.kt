package com.lazyapps.steparena.app

import android.Manifest
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
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    private var trackingState by mutableStateOf(StepTrackingState())
    private var startAfterPermission = false
    private var homeViewModel: HomeViewModel? = null
    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) { result ->
        if (startAfterPermission && result[Manifest.permission.ACTIVITY_RECOGNITION] == true) {
            homeViewModel?.startTracking()
        }
        startAfterPermission = false
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val repository = TrackingStateRepository(applicationContext)
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
                            when (step) {
                                2 -> requestPermissions(startTracking = false, includeNotification = false)
                                3 -> requestPermissions(startTracking = false, includeNotification = true)
                            }
                            lifecycleScope.launch {
                                repository.update {
                                    if (step >= 6) {
                                        it.copy(onboardingComplete = true, onboardingStep = 6)
                                    } else {
                                        it.copy(onboardingStep = step + 1)
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
                                requestPermissions(startTracking = true, includeNotification = true)
                            } else {
                                vm.onAction(action)
                            }
                        },
                    )
                }
            }
        }
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
