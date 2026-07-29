package com.lazyapps.steparena.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.core.view.WindowCompat
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.lazyapps.steparena.app.navigation.StepArenaApp
import com.lazyapps.steparena.core.designsystem.theme.StepArenaTheme
import com.lazyapps.steparena.feature.home.HomeViewModel

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        WindowCompat.getInsetsController(window, window.decorView).isAppearanceLightStatusBars = false
        setContent {
            StepArenaTheme {
                val homeViewModel: HomeViewModel = viewModel()
                val uiState by homeViewModel.uiState.collectAsStateWithLifecycle()
                StepArenaApp(
                    homeUiState = uiState,
                    onHomeAction = homeViewModel::onAction,
                )
            }
        }
    }
}
