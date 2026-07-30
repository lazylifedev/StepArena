package com.lazyapps.steparena.feature.home

import com.lazyapps.steparena.core.designsystem.motion.MotionLevel
import com.lazyapps.steparena.core.model.HomeSnapshot
import java.time.LocalDate
import java.time.ZoneId

sealed interface HomeContent {
    data object Loading : HomeContent
    data object Empty : HomeContent
    data class Ready(val snapshot: HomeSnapshot) : HomeContent
    data class Failure(val canRetry: Boolean = true) : HomeContent
}

data class HomeUiState(
    val content: HomeContent = HomeContent.Loading,
    val motionLevel: MotionLevel = MotionLevel.FULL,
    val sessionState: SessionState = SessionState.TRACKING_STOPPED,
    val trackingUiStatus: com.lazyapps.steparena.tracking.TrackingStatus =
        com.lazyapps.steparena.tracking.TrackingStatus.INITIALIZING,
    val sensorSupported: Boolean = true,
    val manualSession: ManualSessionUi? = null,
    val localDate: LocalDate = LocalDate.ofEpochDay(0),
    val zoneId: ZoneId = ZoneId.of("UTC"),
)

data class ManualSessionUi(
    val id: String,
    val startedAtEpochMillis: Long,
    val steps: Long,
    val distanceMeters: Double,
    val elapsedSeconds: Long,
)

enum class SessionState { TRACKING_STOPPED, TRACKING, MANUAL_WALK }

sealed interface HomeAction {
    data object StartSession : HomeAction
    data object StartManualWalk : HomeAction
    data class EndManualWalk(val sessionId: String) : HomeAction
    data object StopTracking : HomeAction
    data object OpenDiagnostics : HomeAction
    data object Retry : HomeAction
    data class SetMotion(val level: MotionLevel) : HomeAction
}
