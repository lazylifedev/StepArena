package com.lazyapps.steparena.tracking

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.Instant
import java.time.LocalDate

class NotificationStepPreviewTest {
    private val date = LocalDate.parse("2026-07-31")
    private val start = Instant.parse("2026-07-31T01:00:00Z")

    @Test fun oneDetectorEvent_previewsOneStep() {
        val preview = initialized(100)

        val value = preview.onDetector(date, start)

        assertEquals(100, value.officialSteps)
        assertEquals(1, value.pendingDetectorSteps)
        assertEquals(101, value.displayedSteps)
        assertEquals(start, value.lastDetectorAt)
    }

    @Test fun tenDetectorEvents_previewTenSteps() {
        val preview = initialized(100)

        repeat(10) { preview.onDetector(date, start.plusMillis(it.toLong())) }

        assertEquals(10, preview.snapshot.pendingDetectorSteps)
        assertEquals(110, preview.snapshot.displayedSteps)
    }

    @Test fun matchingCounterDelta_reconcilesWithoutDoubleCounting() {
        val preview = initialized(100)
        repeat(10) { preview.onDetector(date, start) }

        val value = preview.onCounter(110, date, start.plusSeconds(1))

        assertEquals(110, value.officialSteps)
        assertEquals(0, value.pendingDetectorSteps)
        assertEquals(110, value.displayedSteps)
    }

    @Test fun partialCounterCatchUp_keepsStablePreview() {
        val preview = initialized(100)
        repeat(10) { preview.onDetector(date, start) }

        val partial = preview.onCounter(104, date, start.plusSeconds(1))
        val complete = preview.onCounter(110, date, start.plusSeconds(2))

        assertEquals(6, partial.pendingDetectorSteps)
        assertEquals(110, partial.displayedSteps)
        assertEquals(0, complete.pendingDetectorSteps)
        assertEquals(110, complete.displayedSteps)
    }

    @Test fun unconfirmedDetectorEventsExpireBackToOfficialSteps() {
        val preview = initialized(100)
        repeat(10) { preview.onDetector(date, start) }

        val expired = preview.expire(start.plusSeconds(NotificationStepPreview.PENDING_TTL_SECONDS + 1))

        assertEquals(0, expired.pendingDetectorSteps)
        assertEquals(100, expired.displayedSteps)
    }

    @Test fun delayedCounterWithinTtlConsumesMatchingPendingEvents() {
        val preview = initialized(100)
        repeat(10) { preview.onDetector(date, start) }

        val reconciled = preview.onCounter(
            110, date, start.plusSeconds(NotificationStepPreview.PENDING_TTL_SECONDS),
        )

        assertEquals(0, reconciled.pendingDetectorSteps)
        assertEquals(110, reconciled.displayedSteps)
    }

    @Test fun pendingEventsAreBounded() {
        val preview = initialized(100)
        repeat(NotificationStepPreview.MAX_PENDING_EVENTS + 10) {
            preview.onDetector(date, start.plusMillis(it.toLong()))
        }

        assertEquals(NotificationStepPreview.MAX_PENDING_EVENTS.toLong(), preview.snapshot.pendingDetectorSteps)
        assertEquals(100L + NotificationStepPreview.MAX_PENDING_EVENTS, preview.snapshot.displayedSteps)
    }

    @Test fun dateChange_discardsPendingDetectorSteps() {
        val preview = initialized(100)
        repeat(3) { preview.onDetector(date, start) }

        val nextDate = preview.onDetector(date.plusDays(1), start.plusSeconds(1))

        assertEquals(0, nextDate.officialSteps)
        assertEquals(1, nextDate.pendingDetectorSteps)
        assertEquals(1, nextDate.displayedSteps)
    }

    @Test fun serviceRestart_startsFromOfficialValueWithoutPendingSteps() {
        val beforeRestart = initialized(100)
        beforeRestart.onDetector(date, start)

        val restarted = NotificationStepPreview().reset(100, date)

        assertEquals(0, restarted.pendingDetectorSteps)
        assertEquals(100, restarted.displayedSteps)
    }

    @Test fun detectorUnsupported_counterAloneUpdatesOfficialDisplay() {
        val preview = initialized(100)

        val value = preview.onCounter(110, date, start)

        assertEquals(0, value.pendingDetectorSteps)
        assertEquals(110, value.displayedSteps)
    }

    @Test fun continuousWalking_interleavedEventsNeverRegressOrDoubleCount() {
        val preview = initialized(1_000)
        val displays = buildList {
            repeat(20) { index ->
                add(preview.onDetector(date, start.plusMillis(index * 20L)).displayedSteps)
                if (index % 4 == 3) {
                    add(
                        preview.onCounter(
                            1_000L + index + 1,
                            date,
                            start.plusMillis(index * 20L + 10),
                        ).displayedSteps,
                    )
                }
            }
        }

        assertEquals(displays.sorted(), displays)
        assertEquals(1_020, preview.snapshot.displayedSteps)
        assertEquals(0, preview.snapshot.pendingDetectorSteps)
    }

    @Test fun screenStateDoesNotAffectSensorReconciliation() {
        val screenOffPreview = initialized(100)
        val screenOnPreview = initialized(100)

        repeat(5) {
            screenOffPreview.onDetector(date, start.plusMillis(it.toLong()))
            screenOnPreview.onDetector(date, start.plusMillis(it.toLong()))
        }
        val off = screenOffPreview.onCounter(105, date, start.plusSeconds(1))
        val on = screenOnPreview.onCounter(105, date, start.plusSeconds(1))

        assertEquals(on, off)
    }

    private fun initialized(officialSteps: Long) = NotificationStepPreview().apply {
        reset(officialSteps, date)
    }
}
