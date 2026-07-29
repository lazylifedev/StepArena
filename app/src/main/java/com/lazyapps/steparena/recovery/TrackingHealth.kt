package com.lazyapps.steparena.recovery

import com.lazyapps.steparena.tracking.StepTrackingState
import java.time.Duration
import java.time.Instant

data class TrackingHealthPolicy(
    val heartbeatIntervalMinutes: Long = 5,
    val staleWarningMinutes: Long = 12,
    val severeStaleMinutes: Long = 30,
)

enum class TrackingHealthStatus { HEALTHY, DELAYED, STALE, STOPPED, USER_STOPPED, UNKNOWN }

fun evaluateTrackingHealth(
    state: StepTrackingState,
    now: Instant,
    policy: TrackingHealthPolicy = TrackingHealthPolicy(),
): TrackingHealthStatus {
    if (!state.trackingRequested) {
        return if (state.lastStopReason == com.lazyapps.steparena.tracking.TrackingStopReason.USER_REQUEST) {
            TrackingHealthStatus.USER_STOPPED
        } else {
            TrackingHealthStatus.STOPPED
        }
    }
    val heartbeat = state.lastHeartbeatAt ?: return TrackingHealthStatus.UNKNOWN
    val age = Duration.between(heartbeat, now).toMinutes().coerceAtLeast(0)
    return when {
        age >= policy.severeStaleMinutes -> TrackingHealthStatus.STOPPED
        age >= policy.staleWarningMinutes -> TrackingHealthStatus.STALE
        age > policy.heartbeatIntervalMinutes -> TrackingHealthStatus.DELAYED
        else -> TrackingHealthStatus.HEALTHY
    }
}

fun shouldNotifyForGap(
    previousNotificationSeverity: Int?,
    current: TrackingHealthStatus,
    reviewed: Boolean,
    explicitUserStop: Boolean,
): Boolean {
    if (reviewed || explicitUserStop) return false
    val severity = when (current) {
        TrackingHealthStatus.STALE -> 1
        TrackingHealthStatus.STOPPED -> 2
        else -> 0
    }
    return severity > (previousNotificationSeverity ?: 0)
}
