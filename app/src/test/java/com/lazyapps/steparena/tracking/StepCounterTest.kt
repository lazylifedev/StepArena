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

    @Test fun dateChange_startsNewDayWithFirstDeltaPreserved() {
        val yesterday = previous(500).copy(currentLocalDate = LocalDate.of(2026, 7, 28), accumulatedTodaySteps = 99)
        val result = counter.accept(510f, yesterday, now, tokyo, "boot-1")
        assertEquals(10, result.state.accumulatedTodaySteps)
        assertEquals(500L, result.state.sensorBaseline)
    }

    @Test fun timezoneChange_rebaselines() {
        val result = counter.accept(510f, previous(500).copy(currentZoneId = "UTC"), now, tokyo, "boot-1")
        assertEquals(10, result.state.accumulatedTodaySteps)
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

    @Test fun continuousSequence_addsEachDifferenceOnce() {
        var state = counter.accept(10_000f, requested(), now, tokyo, "boot-1").state
        (10_001..10_050).forEachIndexed { index, value ->
            state = counter.accept(value.toFloat(), state, now.plusSeconds(index.toLong() + 1), tokyo, "boot-1").state
        }
        assertEquals(50, state.accumulatedTodaySteps)
    }

    @Test fun requestedSequence_addsExactlyOneHundred() {
        var state = counter.accept(1_000f, requested(), now, tokyo, "boot-1").state
        listOf(1_001f, 1_002f, 1_100f).forEachIndexed { index, raw ->
            state = counter.accept(raw, state, now.plusSeconds(index + 1L), tokyo, "boot-1").state
        }
        assertEquals(100, state.accumulatedTodaySteps)
        assertEquals(1_002L, state.previousSensorValue)
        assertEquals(1_100L, state.lastSensorValue)
    }

    @Test fun oneHundredConsecutiveEvents_areAllCounted() {
        var state = counter.accept(2_000f, requested(), now, tokyo, "boot-1").state
        repeat(100) { index ->
            state = counter.accept(
                2_001f + index,
                state,
                now.plusSeconds(index + 1L),
                tokyo,
                "boot-1",
            ).state
        }
        assertEquals(100, state.accumulatedTodaySteps)
    }

    @Test fun stopAndRestart_excludesStoppedInterval() {
        val beforeStop = counter.accept(10_050f, previous(10_000), now, tokyo, "boot-1").state
        val stopped = beforeStop.copy(trackingRequested = false)
        val ignored = counter.accept(10_100f, stopped, now.plusSeconds(1), tokyo, "boot-1").state
        val restarted = ignored.copy(
            trackingRequested = true,
            sensorBaseline = null,
            lastSensorValue = null,
        )
        val baseline = counter.accept(10_100f, restarted, now.plusSeconds(2), tokyo, "boot-1").state
        val final = counter.accept(10_150f, baseline, now.plusSeconds(3), tokyo, "boot-1").state
        assertEquals(100, final.accumulatedTodaySteps)
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
