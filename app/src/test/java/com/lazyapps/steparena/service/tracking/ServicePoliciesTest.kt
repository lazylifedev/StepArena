package com.lazyapps.steparena.service.tracking

import com.lazyapps.steparena.tracking.StepTrackingState
import com.lazyapps.steparena.tracking.TrackingStatus
import org.junit.Assert.assertFalse
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant

class ServicePoliciesTest {
    @Test fun staleSessionAction_isRejected() {
        assertFalse(isCurrentSessionRequest("old", "new"))
        assertTrue(isCurrentSessionRequest("new", "new"))
        assertTrue(isCurrentSessionRequest(null, "new"))
    }

    @Test fun heartbeat_doesNotMaskRegistrationFailure() {
        val state = StepTrackingState(
            trackingRequested = true,
            trackingStatus = TrackingStatus.ERROR,
            stepCounterRegistered = false,
        )
        val result = heartbeatState(state, Instant.parse("2026-07-30T00:00:00Z"))
        assertEquals(TrackingStatus.ERROR, result.trackingStatus)
        assertFalse(result.stepCounterRegistered)
        assertTrue(result.serviceRunning)
    }

    @Test fun heartbeat_doesNotMaskMissingPermission() {
        val state = StepTrackingState(trackingStatus = TrackingStatus.PERMISSION_REQUIRED)
        assertEquals(
            TrackingStatus.PERMISSION_REQUIRED,
            heartbeatState(state, Instant.EPOCH).trackingStatus,
        )
    }

    @Test fun heartbeat_recoversStaleHeartbeat() {
        val state = StepTrackingState(
            trackingRequested = true,
            trackingStatus = TrackingStatus.SERVICE_HEARTBEAT_STALE,
        )
        assertEquals(
            TrackingStatus.TRACKING,
            heartbeatState(state, Instant.EPOCH).trackingStatus,
        )
    }
}
