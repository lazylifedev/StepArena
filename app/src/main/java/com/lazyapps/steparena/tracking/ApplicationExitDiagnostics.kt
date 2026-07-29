package com.lazyapps.steparena.tracking

import android.app.ActivityManager
import android.app.ApplicationExitInfo
import android.content.Context
import android.os.Build

data class PreviousExit(
    val key: String,
    val summary: String,
    val likelyForceStop: Boolean,
)

fun Context.readPreviousExit(): PreviousExit? {
    if (Build.VERSION.SDK_INT < 30) return null
    val info = getSystemService(ActivityManager::class.java)
        .getHistoricalProcessExitReasons(packageName, 0, 1)
        .firstOrNull() ?: return null
    val reason = exitReasonLabel(info.reason)
    val description = info.description?.take(160)?.replace('\n', ' ') ?: "不明"
    val key = "${info.timestamp}:${info.reason}:${info.processName}"
    return PreviousExit(
        key = key,
        summary =
        "reason=$reason, description=$description, importance=${info.importance}, " +
            "timestamp=${info.timestamp}, process=${info.processName ?: "不明"}, " +
            "pss=${info.pss}, rss=${info.rss}",
        likelyForceStop = isLikelySettingsForceStop(info.reason, description, packageName),
    )
}

suspend fun reconcileForceStop(
    context: Context,
    repository: TrackingStateRepository,
) {
    val exit = context.readPreviousExit() ?: return
    repository.update { state ->
        if (exit.likelyForceStop && state.trackingRequested && exit.key != state.lastExitInfoKey) {
            state.copy(
                trackingRequested = false,
                trackingStatus = TrackingStatus.STOPPED,
                lastStopReason = TrackingStopReason.OS_FORCE_STOP,
                sessionId = null,
                sensorBaseline = null,
                lastSensorValue = null,
                lastExitInfoKey = exit.key,
                lastExitSummary = exit.summary,
            )
        } else {
            state
        }
    }
}

internal fun isNewExitRecord(exitKey: String, processedKey: String?): Boolean =
    exitKey != processedKey

internal fun isLikelySettingsForceStop(reason: Int, description: String, packageName: String): Boolean =
    reason == ApplicationExitInfo.REASON_USER_REQUESTED &&
        description.startsWith("stop $packageName") &&
        !description.contains("installPackageLI")

fun exitReasonLabel(reason: Int): String = when (reason) {
    ApplicationExitInfo.REASON_EXIT_SELF -> "EXIT_SELF"
    ApplicationExitInfo.REASON_SIGNALED -> "SIGNALED"
    ApplicationExitInfo.REASON_LOW_MEMORY -> "LOW_MEMORY"
    ApplicationExitInfo.REASON_CRASH -> "CRASH"
    ApplicationExitInfo.REASON_CRASH_NATIVE -> "CRASH_NATIVE"
    ApplicationExitInfo.REASON_ANR -> "ANR"
    ApplicationExitInfo.REASON_INITIALIZATION_FAILURE -> "INITIALIZATION_FAILURE"
    ApplicationExitInfo.REASON_PERMISSION_CHANGE -> "PERMISSION_CHANGE"
    ApplicationExitInfo.REASON_EXCESSIVE_RESOURCE_USAGE -> "EXCESSIVE_RESOURCE_USAGE"
    ApplicationExitInfo.REASON_USER_REQUESTED -> "USER_REQUESTED"
    ApplicationExitInfo.REASON_USER_STOPPED -> "USER_STOPPED"
    ApplicationExitInfo.REASON_DEPENDENCY_DIED -> "DEPENDENCY_DIED"
    ApplicationExitInfo.REASON_OTHER -> "OTHER"
    else -> "不明($reason)"
}
