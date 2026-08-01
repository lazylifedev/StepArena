package com.lazyapps.steparena.service.tracking

import com.lazyapps.steparena.tracking.StepTrackingState
import com.lazyapps.steparena.tracking.TrackingStatus
import org.junit.Assert.assertFalse
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant

class ServicePoliciesTest {
    @Test fun counter_recoversOnlyFromStaleStatusesUsingPreviousState() {
        val previous = StepTrackingState(
            trackingRequested = true,
            trackingStatus = TrackingStatus.SENSOR_DATA_STALE,
        )
        val result = previous.copy(trackingStatus = TrackingStatus.RESTARTED)
        assertEquals(TrackingStatus.TRACKING, recoverStatusOnCounterEvent(previous, result).trackingStatus)
        assertEquals(TrackingStatus.RESTARTED, recoverStatusOnCounterEvent(previous.copy(trackingStatus = TrackingStatus.TRACKING), result).trackingStatus)
    }

    @Test fun counter_preservesResetResult() {
        val previous = StepTrackingState(trackingRequested = true, trackingStatus = TrackingStatus.TRACKING)
        val result = previous.copy(trackingStatus = TrackingStatus.RESTARTED)
        assertEquals(TrackingStatus.RESTARTED, recoverStatusOnCounterEvent(previous, result).trackingStatus)
    }

    @Test fun detector_recoversStaleStatusAndRecordsEventTime() {
        val eventAt = Instant.parse("2026-07-30T00:00:01Z")
        val result = recoverStatusOnDetectorEvent(
            StepTrackingState(
                trackingRequested = true,
                trackingStatus = TrackingStatus.SERVICE_HEARTBEAT_STALE,
                stepDetectorRegistered = true,
                serviceRunning = true,
            ),
            eventAt,
        )
        assertEquals(TrackingStatus.TRACKING, result.trackingStatus)
        assertEquals(eventAt, result.lastSensorEventAt)
        assertTrue(result.serviceRunning)
    }

    @Test fun detector_doesNotRecoverWhenTrackingWasStoppedOrDetectorMissing() {
        val base = StepTrackingState(
            trackingRequested = true,
            trackingStatus = TrackingStatus.SENSOR_DATA_STALE,
            serviceRunning = true,
        )
        assertEquals(TrackingStatus.SENSOR_DATA_STALE, recoverStatusOnDetectorEvent(base, Instant.EPOCH).trackingStatus)
        assertEquals(TrackingStatus.SENSOR_DATA_STALE, recoverStatusOnDetectorEvent(base.copy(stepDetectorRegistered = true, trackingRequested = false), Instant.EPOCH).trackingStatus)
    }

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
