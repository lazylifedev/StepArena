package com.lazyapps.steparena.tracking

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

class StepCounterTest {
    private val counter = StepCounter(unusualDeltaThreshold = 1_000)
    private val now = Instant.parse("2026-07-29T12:00:00Z")
    private val tokyo = ZoneId.of("Asia/Tokyo")

    @Test fun firstValue_createsBaselineWithoutAdding() {
        val result = counter.accept(500f, requested(), now, tokyo, "boot-1")
        assertTrue(result is StepEventResult.Baseline)
        assertEquals(0, result.state.accumulatedTodaySteps)
        assertEquals(500L, result.state.lastSensorValue)
    }

    @Test fun normalDelta_addsOnlyDifference() {
        val result = counter.accept(515f, previous(500), now, tokyo, "boot-1")
        assertEquals(15, result.state.accumulatedTodaySteps)
    }

    @Test fun sameValue_doesNotAdd() {
        val result = counter.accept(500f, previous(500), now, tokyo, "boot-1")
        assertTrue(result is StepEventResult.Ignored)
        assertEquals(0, result.state.accumulatedTodaySteps)
    }

    @Test fun decreasedValue_rebaselinesWithoutNegativeDelta() {
        val result = counter.accept(20f, previous(500).copy(accumulatedTodaySteps = 123), now, tokyo, "boot-1")
        assertTrue(result is StepEventResult.Reset)
        assertEquals(123, result.state.accumulatedTodaySteps)
        assertEquals(20L, result.state.sensorBaseline)
    }

    @Test fun reboot_rebaselinesAndKeepsConfirmedSteps() {
        val result = counter.accept(30f, previous(500).copy(accumulatedTodaySteps = 123), now, tokyo, "boot-2")
        assertTrue(result is StepEventResult.Baseline)
        assertEquals(123, result.state.accumulatedTodaySteps)
        assertEquals(TrackingStatus.RESTARTED, result.state.trackingStatus)
    }

    @Test fun dateChange_startsNewDayAtZero() {
        val yesterday = previous(500).copy(currentLocalDate = LocalDate.of(2026, 7, 28), accumulatedTodaySteps = 99)
        val result = counter.accept(510f, yesterday, now, tokyo, "boot-1")
        assertEquals(0, result.state.accumulatedTodaySteps)
        assertEquals(510L, result.state.sensorBaseline)
    }

    @Test fun timezoneChange_rebaselines() {
        val result = counter.accept(510f, previous(500).copy(currentZoneId = "UTC"), now, tokyo, "boot-1")
        assertEquals(0, result.state.accumulatedTodaySteps)
        assertEquals(tokyo.id, result.state.currentZoneId)
    }

    @Test fun duplicateEvent_isIgnored() {
        val first = counter.accept(510f, previous(500), now, tokyo, "boot-1").state
        val duplicate = counter.accept(510f, first, now.plusSeconds(1), tokyo, "boot-1")
        assertEquals(10, duplicate.state.accumulatedTodaySteps)
    }

    @Test fun invalidFloatsAndNegativeValues_areIgnored() {
        listOf(Float.NaN, Float.POSITIVE_INFINITY, Float.NEGATIVE_INFINITY, -1f).forEach {
            assertTrue(counter.accept(it, previous(500), now, tokyo, "boot-1") is StepEventResult.Ignored)
        }
    }

    @Test fun largeDelta_isKeptAndMarkedForReview() {
        val result = counter.accept(2_000f, previous(500), now, tokyo, "boot-1")
        assertTrue(result is StepEventResult.Added)
        assertTrue((result as StepEventResult.Added).unusuallyLarge)
        assertEquals(1_500, result.state.accumulatedTodaySteps)
        assertTrue(result.state.needsReview)
    }

    @Test fun restoredState_continuesWithoutDoubleCounting() {
        val restored = previous(550).copy(accumulatedTodaySteps = 50)
        val result = counter.accept(555f, restored, now, tokyo, "boot-1")
        assertEquals(55, result.state.accumulatedTodaySteps)
    }

    @Test fun trackingNotRequested_ignoresSensor() {
        val result = counter.accept(510f, previous(500).copy(trackingRequested = false), now, tokyo, "boot-1")
        assertTrue(result is StepEventResult.Ignored)
        assertFalse(result.state.trackingRequested)
    }

    private fun requested() = StepTrackingState(
        trackingRequested = true,
        currentLocalDate = LocalDate.of(2026, 7, 29),
        currentZoneId = tokyo.id,
    )

    private fun previous(value: Long) = requested().copy(
        bootSessionId = "boot-1",
        sensorBaseline = 500,
        lastSensorValue = value,
    )
}
