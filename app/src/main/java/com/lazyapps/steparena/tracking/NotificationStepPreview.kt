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
    private val pendingEvents = ArrayDeque<Instant>()
    var snapshot: NotificationStepPreviewSnapshot = NotificationStepPreviewSnapshot()
        private set

    fun reset(officialSteps: Long, localDate: LocalDate): NotificationStepPreviewSnapshot {
        pendingEvents.clear()
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
        if (snapshot.localDate != localDate) pendingEvents.clear()
        expireEvents(at)
        pendingEvents.addLast(at)
        while (pendingEvents.size > MAX_PENDING_EVENTS) pendingEvents.removeFirst()
        val current = snapshot.takeIf { it.localDate == localDate }
            ?: NotificationStepPreviewSnapshot(localDate = localDate)
        val pending = pendingEvents.size.toLong()
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
        if (snapshot.localDate != localDate) pendingEvents.clear()
        expireEvents(at)
        val officialDelta = (safeOfficial - current.officialSteps).coerceAtLeast(0)
        repeat(officialDelta.coerceAtMost(pendingEvents.size.toLong()).toInt()) {
            pendingEvents.removeFirst()
        }
        val pending = pendingEvents.size.toLong()
        val reconciledDisplay = safeOfficial + pending
        return publish(
            current.copy(
                officialSteps = safeOfficial,
                pendingDetectorSteps = pending,
                displayedSteps = reconciledDisplay,
                lastCounterAt = at,
            ),
        )
    }

    fun expire(at: Instant): NotificationStepPreviewSnapshot {
        expireEvents(at)
        return publish(
            snapshot.copy(
                pendingDetectorSteps = pendingEvents.size.toLong(),
                displayedSteps = snapshot.officialSteps + pendingEvents.size,
            ),
        )
    }

    private fun expireEvents(at: Instant) {
        val cutoff = at.minusSeconds(PENDING_TTL_SECONDS)
        while (pendingEvents.firstOrNull()?.isBefore(cutoff) == true) pendingEvents.removeFirst()
    }

    private fun publish(value: NotificationStepPreviewSnapshot): NotificationStepPreviewSnapshot {
        snapshot = value
        return value
    }

    companion object {
        const val PENDING_TTL_SECONDS = 25L
        const val MAX_PENDING_EVENTS = 256
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
