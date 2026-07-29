package com.lazyapps.steparena.recovery

import com.lazyapps.steparena.tracking.StepTrackingState
import com.lazyapps.steparena.tracking.TrackingStopReason
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant

class TrackingHealthTest {
    private val now = Instant.parse("2026-07-29T12:00:00Z")

    @Test fun heartbeatProgressesWithoutPrematureStopClaim() {
        fun at(minutes: Long) = StepTrackingState(
            trackingRequested = true,
            lastHeartbeatAt = now.minusSeconds(minutes * 60),
        )
        assertEquals(TrackingHealthStatus.HEALTHY, evaluateTrackingHealth(at(5), now))
        assertEquals(TrackingHealthStatus.DELAYED, evaluateTrackingHealth(at(6), now))
        assertEquals(TrackingHealthStatus.STALE, evaluateTrackingHealth(at(12), now))
        assertEquals(TrackingHealthStatus.STOPPED, evaluateTrackingHealth(at(30), now))
    }

    @Test fun explicitStopIsSeparateAndNeverNotified() {
        val state = StepTrackingState(
            trackingRequested = false,
            lastStopReason = TrackingStopReason.USER_REQUEST,
        )
        assertEquals(TrackingHealthStatus.USER_STOPPED, evaluateTrackingHealth(state, now))
        assertFalse(shouldNotifyForGap(null, TrackingHealthStatus.STALE, false, true))
    }

    @Test fun notificationOccursOnlyForFirstOrWorsenedSeverity() {
        assertTrue(shouldNotifyForGap(null, TrackingHealthStatus.STALE, false, false))
        assertFalse(shouldNotifyForGap(1, TrackingHealthStatus.STALE, false, false))
        assertTrue(shouldNotifyForGap(1, TrackingHealthStatus.STOPPED, false, false))
        assertFalse(shouldNotifyForGap(null, TrackingHealthStatus.STOPPED, true, false))
    }
}
