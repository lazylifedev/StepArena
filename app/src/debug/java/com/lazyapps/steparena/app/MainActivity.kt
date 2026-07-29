package com.lazyapps.steparena.app

import android.os.Bundle
import android.view.KeyEvent
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.core.view.WindowCompat
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.FilterChip
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.lazyapps.steparena.app.navigation.StepArenaApp
import com.lazyapps.steparena.core.designsystem.motion.MotionLevel
import com.lazyapps.steparena.core.designsystem.theme.StepArenaTheme
import com.lazyapps.steparena.feature.home.HomeAction
import com.lazyapps.steparena.feature.home.SessionState

class MainActivity : ComponentActivity() {
    private var debugMenuVisible by mutableStateOf(false)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        WindowCompat.getInsetsController(window, window.decorView).isAppearanceLightStatusBars = false
        setContent {
            StepArenaTheme {
                DebugUiEvaluationHost(
                    menuVisible = debugMenuVisible,
                    onDismissMenu = { debugMenuVisible = false },
                )
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
            debugMenuVisible = true
            return true
        }
        return super.onKeyLongPress(keyCode, event)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DebugUiEvaluationHost(
    menuVisible: Boolean,
    onDismissMenu: () -> Unit,
) {
    var scenario by remember { mutableStateOf(DebugHomeScenario.NORMAL) }
    var motion by remember { mutableStateOf(MotionLevel.FULL) }
    var sessionStarted by remember { mutableStateOf(false) }
    val evaluationState = remember(scenario, motion, sessionStarted) {
        scenario.uiState(motion).let { state ->
            if (sessionStarted) state.copy(sessionState = SessionState.STARTED) else state
        }
    }

    StepArenaApp(
        homeUiState = evaluationState,
        onHomeAction = { action ->
            when (action) {
                is HomeAction.SetMotion -> motion = action.level
                HomeAction.StartSession -> sessionStarted = true
                HomeAction.StopTracking -> sessionStarted = false
                HomeAction.OpenDiagnostics -> Unit
                HomeAction.Retry -> Unit
            }
        },
    )

    if (menuVisible) {
        ModalBottomSheet(onDismissRequest = onDismissMenu) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text("ホーム状態", style = MaterialTheme.typography.titleLarge)
                LazyColumn(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    items(DebugHomeScenario.entries) { option ->
                        FilterChip(
                            selected = scenario == option,
                            onClick = {
                                scenario = option
                                sessionStarted = false
                                onDismissMenu()
                            },
                            label = { Text(option.label) },
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                    item {
                        Text(
                            text = "モーション",
                            modifier = Modifier.padding(top = 12.dp),
                            style = MaterialTheme.typography.titleMedium,
                        )
                    }
                    items(MotionLevel.entries) { option ->
                        FilterChip(
                            selected = motion == option,
                            onClick = { motion = option },
                            label = { Text(option.debugLabel) },
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                }
            }
        }
    }
}

private val MotionLevel.debugLabel: String
    get() = when (this) {
        MotionLevel.FULL -> "Full Motion"
        MotionLevel.REDUCED -> "Reduced Motion"
        MotionLevel.OFF -> "Motion Off"
    }
