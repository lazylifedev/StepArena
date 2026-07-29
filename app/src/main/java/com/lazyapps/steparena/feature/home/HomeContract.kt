package com.lazyapps.steparena.feature.home

import com.lazyapps.steparena.core.designsystem.motion.MotionLevel
import com.lazyapps.steparena.core.model.HomeSnapshot

sealed interface HomeContent {
    data object Loading : HomeContent
    data object Empty : HomeContent
    data class Ready(val snapshot: HomeSnapshot) : HomeContent
    data class Failure(val canRetry: Boolean = true) : HomeContent
}

data class HomeUiState(
    val content: HomeContent = HomeContent.Loading,
    val motionLevel: MotionLevel = MotionLevel.FULL,
    val sessionState: SessionState = SessionState.IDLE,
    val trackingUiStatus: com.lazyapps.steparena.tracking.TrackingStatus =
        com.lazyapps.steparena.tracking.TrackingStatus.INITIALIZING,
    val sensorSupported: Boolean = true,
)

enum class SessionState { IDLE, STARTED }

sealed interface HomeAction {
    data object StartSession : HomeAction
    data object StopTracking : HomeAction
    data object OpenDiagnostics : HomeAction
    data object Retry : HomeAction
    data class SetMotion(val level: MotionLevel) : HomeAction
}
