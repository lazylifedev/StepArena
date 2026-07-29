package com.lazyapps.steparena.recovery

import java.time.Duration
import java.time.Instant
import java.security.MessageDigest

enum class HealthConnectAvailability {
    AVAILABLE, UPDATE_REQUIRED, PROVIDER_NOT_INSTALLED, NOT_SUPPORTED, UNKNOWN,
}

enum class ExternalRecordingMethod { ACTIVE, AUTOMATIC, MANUAL, UNKNOWN }
enum class ExternalDataOriginType { ANDROID_ON_DEVICE, TRUSTED_FITNESS_APP, STEP_ARENA, UNKNOWN_APP }
enum class TrackingGapReason {
    HEARTBEAT_STALE, PROCESS_CRASH, SERVICE_RESTART, DEVICE_REBOOT, SENSOR_STALE,
    BATTERY_RESTRICTED, UNKNOWN,
}
enum class TrackingGapStatus {
    DETECTED, RECOVERY_PENDING, RECOVERED, PARTIALLY_RECOVERED, UNRESOLVED,
    IGNORED, USER_REVIEW_REQUIRED,
}
enum class RecoverySource { HEALTH_CONNECT, COUNTER_DELTA, ESTIMATE }

data class ExternalStepSegment(
    val start: Instant,
    val end: Instant,
    val steps: Long,
    val dataOriginPackage: String,
    val recordId: String?,
    val lastModifiedAt: Instant?,
    val recordingMethod: ExternalRecordingMethod,
) {
    fun validation(): SegmentValidation = when {
        steps < 0 -> SegmentValidation.REJECTED_NEGATIVE_STEPS
        !start.isBefore(end) -> SegmentValidation.REJECTED_INVALID_INTERVAL
        Duration.between(start, end) > Duration.ofHours(24) -> SegmentValidation.REVIEW_LONG_INTERVAL
        steps > 100_000 -> SegmentValidation.REVIEW_LARGE_COUNT
        else -> SegmentValidation.VALID
    }

    fun clippedTo(rangeStart: Instant, rangeEnd: Instant): ExternalStepSegment? {
        val clippedStart = maxOf(start, rangeStart)
        val clippedEnd = minOf(end, rangeEnd)
        if (!clippedStart.isBefore(clippedEnd)) return null
        if (clippedStart == start && clippedEnd == end) return this
        val fullMillis = Duration.between(start, end).toMillis()
        val clippedMillis = Duration.between(clippedStart, clippedEnd).toMillis()
        val allocated = if (fullMillis <= 0) 0 else steps * clippedMillis / fullMillis
        return copy(start = clippedStart, end = clippedEnd, steps = allocated)
    }

    fun fingerprint(): String = sha256(
        listOf(dataOriginPackage, start, end, steps, lastModifiedAt).joinToString("|"),
    )
}

enum class SegmentValidation {
    VALID, REVIEW_LONG_INTERVAL, REVIEW_LARGE_COUNT, REJECTED_NEGATIVE_STEPS,
    REJECTED_INVALID_INTERVAL,
}

data class ExternalStepResult(
    val segments: List<ExternalStepSegment> = emptyList(),
    val error: ExternalReadError? = null,
)
enum class ExternalReadError { NOT_AVAILABLE, PERMISSION_REQUIRED, API_FAILURE }

fun classifyDataOrigin(packageName: String, ownPackage: String): ExternalDataOriginType = when {
    packageName == ownPackage -> ExternalDataOriginType.STEP_ARENA
    packageName == "android" || packageName.startsWith("com.android.healthconnect.") ->
        ExternalDataOriginType.ANDROID_ON_DEVICE
    packageName.isBlank() -> ExternalDataOriginType.UNKNOWN_APP
    else -> ExternalDataOriginType.UNKNOWN_APP
}

internal fun sha256(value: String): String =
    MessageDigest.getInstance("SHA-256").digest(value.toByteArray())
        .joinToString("") { "%02x".format(it) }
