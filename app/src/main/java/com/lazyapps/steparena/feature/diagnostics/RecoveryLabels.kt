package com.lazyapps.steparena.feature.diagnostics

import androidx.annotation.StringRes
import com.lazyapps.steparena.R
import com.lazyapps.steparena.core.database.model.DataQuality
import com.lazyapps.steparena.recovery.TrackingGapReason
import com.lazyapps.steparena.recovery.TrackingGapStatus
import com.lazyapps.steparena.recovery.HealthConnectAvailability

@StringRes
fun TrackingGapReason.labelRes(): Int = when (this) {
    TrackingGapReason.HEARTBEAT_STALE -> R.string.gap_reason_heartbeat
    TrackingGapReason.PROCESS_CRASH -> R.string.gap_reason_crash
    TrackingGapReason.SERVICE_RESTART -> R.string.gap_reason_service_restart
    TrackingGapReason.DEVICE_REBOOT -> R.string.gap_reason_reboot
    TrackingGapReason.SENSOR_STALE -> R.string.gap_reason_sensor
    TrackingGapReason.BATTERY_RESTRICTED -> R.string.gap_reason_battery
    TrackingGapReason.UNKNOWN -> R.string.gap_reason_unknown
}

@StringRes
fun TrackingGapStatus.labelRes(): Int = when (this) {
    TrackingGapStatus.DETECTED -> R.string.gap_status_detected
    TrackingGapStatus.RECOVERY_PENDING -> R.string.gap_status_pending
    TrackingGapStatus.RECOVERED -> R.string.gap_status_recovered
    TrackingGapStatus.PARTIALLY_RECOVERED -> R.string.gap_status_partial
    TrackingGapStatus.UNRESOLVED -> R.string.gap_status_unresolved
    TrackingGapStatus.IGNORED -> R.string.gap_status_ignored
    TrackingGapStatus.USER_REVIEW_REQUIRED -> R.string.gap_status_review
}

@StringRes
fun DataQuality.labelRes(): Int = when (this) {
    DataQuality.MEASURED -> R.string.quality_measured
    DataQuality.ESTIMATED -> R.string.quality_estimated
    DataQuality.RECOVERED -> R.string.quality_recovered
    DataQuality.MIXED -> R.string.quality_mixed
    DataQuality.UNKNOWN -> R.string.quality_unknown
}

@StringRes
fun HealthConnectAvailability.labelRes(): Int = when (this) {
    HealthConnectAvailability.AVAILABLE -> R.string.health_connect_available
    HealthConnectAvailability.UPDATE_REQUIRED -> R.string.health_connect_update_required
    HealthConnectAvailability.PROVIDER_NOT_INSTALLED -> R.string.health_connect_not_installed
    HealthConnectAvailability.NOT_SUPPORTED -> R.string.health_connect_not_supported
    HealthConnectAvailability.UNKNOWN -> R.string.health_connect_unknown
}
