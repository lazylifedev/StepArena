package com.lazyapps.steparena.tracking

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.time.Instant
import java.time.LocalDate

data class NotificationStepPreviewSnapshot(
    val localDate: LocalDate? = null,
    val officialSteps: Long = 0,
    val pendingDetectorSteps: Long = 0,
    val displayedSteps: Long = 0,
    val lastDetectorAt: Instant? = null,
    val lastCounterAt: Instant? = null,
)

/**
 * Service-lifetime notification preview. Detector events are never persisted or exposed as
 * official activity data; the cumulative counter remains the sole source of recorded steps.
 */
class NotificationStepPreview {
    var snapshot: NotificationStepPreviewSnapshot = NotificationStepPreviewSnapshot()
        private set

    fun reset(officialSteps: Long, localDate: LocalDate): NotificationStepPreviewSnapshot {
        val safeOfficial = officialSteps.coerceAtLeast(0)
        return publish(
            NotificationStepPreviewSnapshot(
                localDate = localDate,
                officialSteps = safeOfficial,
                displayedSteps = safeOfficial,
            ),
        )
    }

    fun onDetector(localDate: LocalDate, at: Instant): NotificationStepPreviewSnapshot {
        val current = snapshot.takeIf { it.localDate == localDate }
            ?: NotificationStepPreviewSnapshot(localDate = localDate)
        val pending = current.pendingDetectorSteps + 1
        return publish(
            current.copy(
                pendingDetectorSteps = pending,
                displayedSteps = current.officialSteps + pending,
                lastDetectorAt = at,
            ),
        )
    }

    fun onCounter(
        officialSteps: Long,
        localDate: LocalDate,
        at: Instant,
    ): NotificationStepPreviewSnapshot {
        val safeOfficial = officialSteps.coerceAtLeast(0)
        val current = snapshot.takeIf { it.localDate == localDate }
            ?: NotificationStepPreviewSnapshot(localDate = localDate)
        val officialDelta = (safeOfficial - current.officialSteps).coerceAtLeast(0)
        val pending = (current.pendingDetectorSteps - officialDelta).coerceAtLeast(0)
        val reconciledDisplay = safeOfficial + pending
        return publish(
            current.copy(
                officialSteps = safeOfficial,
                pendingDetectorSteps = pending,
                displayedSteps = maxOf(current.displayedSteps, reconciledDisplay),
                lastCounterAt = at,
            ),
        )
    }

    private fun publish(value: NotificationStepPreviewSnapshot): NotificationStepPreviewSnapshot {
        snapshot = value
        return value
    }
}

/** Process-local diagnostics mirror. The service-owned pending count is not persisted. */
object NotificationStepPreviewDiagnostics {
    private val mutableSnapshot = MutableStateFlow(NotificationStepPreviewSnapshot())
    val snapshot = mutableSnapshot.asStateFlow()

    fun publish(value: NotificationStepPreviewSnapshot) {
        mutableSnapshot.value = value
    }

    fun clear() {
        mutableSnapshot.value = NotificationStepPreviewSnapshot()
    }
}
