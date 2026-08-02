package com.lazyapps.steparena.tracking

import java.time.Instant
import java.time.LocalDate

enum class TrackingStatus {
    INITIALIZING, READY, STARTING, TRACKING, STOPPING, STOPPED, RESTARTED,
    SENSOR_UNSUPPORTED, PERMISSION_REQUIRED, NOTIFICATION_PERMISSION_RECOMMENDED,
    BATTERY_RESTRICTED, SERVICE_HEARTBEAT_STALE, SENSOR_DATA_STALE, ERROR,
}

enum class TrackingStopReason {
    USER_REQUEST, OS_FORCE_STOP, PERMISSION_REVOKED, SENSOR_UNAVAILABLE, SERVICE_ERROR,
}
enum class StepDataSource { STEP_COUNTER }

data class StepTrackingState(
    val trackingRequested: Boolean = false,
    val trackingStatus: TrackingStatus = TrackingStatus.INITIALIZING,
    val bootSessionId: String = "",
    val sensorBaseline: Long? = null,
    val lastSensorValue: Long? = null,
    val accumulatedTodaySteps: Long = 0,
    val currentLocalDate: LocalDate = LocalDate.now(),
    val currentZoneId: String = java.time.ZoneId.systemDefault().id,
    val lastSensorEventAt: Instant? = null,
    val previousSensorValue: Long? = null,
    val lastStepIncreaseAt: Instant? = null,
    val stepCounterRegistered: Boolean = false,
    val stepDetectorRegistered: Boolean = false,
    val serviceRunning: Boolean = false,
    val lastHeartbeatAt: Instant? = null,
    val lastServiceStartedAt: Instant? = null,
    val lastServiceStoppedAt: Instant? = null,
    val lastStopReason: TrackingStopReason? = null,
    val sessionId: String? = null,
    val onboardingStep: Int = 0,
    val onboardingComplete: Boolean = false,
    val onboardingVersion: Int = 0,
    val trackingExplanationSeen: Boolean = false,
    val notificationExplanationSeen: Boolean = false,
    val healthConnectExplanationSeen: Boolean = false,
    val gameRulesExplanationSeen: Boolean = false,
    val privacyPolicyVersionSeen: Int = 0,
    val batteryGuidanceAcknowledged: Boolean = false,
    val notificationGuidanceAcknowledged: Boolean = false,
    val needsReview: Boolean = false,
    val lastNotificationAt: Instant? = null,
    val lastExitInfoKey: String? = null,
    val lastExitSummary: String? = null,
)

data class DailyStepSummary(
    val localDate: LocalDate,
    val zoneId: String,
    val steps: Long,
    val finalizedAt: Instant,
    val source: StepDataSource = StepDataSource.STEP_COUNTER,
)

sealed interface StepEventResult {
    val state: StepTrackingState
    data class Baseline(override val state: StepTrackingState) : StepEventResult
    data class Added(val delta: Long, val unusuallyLarge: Boolean, override val state: StepTrackingState) : StepEventResult
    data class Reset(override val state: StepTrackingState) : StepEventResult
    data class Ignored(override val state: StepTrackingState) : StepEventResult
}
