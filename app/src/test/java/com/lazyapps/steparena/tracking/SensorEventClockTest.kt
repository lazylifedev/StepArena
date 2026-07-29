package com.lazyapps.steparena.tracking

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.Instant

class SensorEventClockTest {
    private val now = Instant.parse("2026-07-29T00:00:10Z")

    @Test fun convertsRealtimeTimestamp() {
        val clock = RealtimeSensorEventClock({ now }, { 10_000_000_000L })
        assertEquals(Instant.parse("2026-07-29T00:00:07Z"), clock.toInstant(7_000_000_000L))
    }

    @Test fun preservesBatchDelay() {
        val clock = RealtimeSensorEventClock({ now }, { 100_000_000_000L })
        assertEquals(now.minusSeconds(60), clock.toInstant(40_000_000_000L))
    }

    @Test fun futureTimestampFallsBackToReceipt() {
        val clock = RealtimeSensorEventClock({ now }, { 10L })
        assertEquals(now, clock.toInstant(11L))
    }

    @Test fun negativeTimestampFallsBackToReceipt() {
        val clock = RealtimeSensorEventClock({ now }, { 10L })
        assertEquals(now, clock.toInstant(-1L))
    }
}
