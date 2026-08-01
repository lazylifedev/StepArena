package com.lazyapps.steparena.service.tracking

import com.lazyapps.steparena.tracking.StepTrackingState
import com.lazyapps.steparena.tracking.StepEventResult
import com.lazyapps.steparena.tracking.StepCounter
import com.lazyapps.steparena.tracking.TrackingStatus
import org.junit.Assert.assertFalse
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant

class ServicePoliciesTest {
    @Test
    fun staleDiagnosticWritesCannotInflateA469StepCounterSequence() {
        val counter = StepCounter()
        val at = Instant.parse("2026-08-01T14:00:00Z")
        var state = StepTrackingState(
            trackingRequested = true,
            currentLocalDate = java.time.LocalDate.of(2026, 8, 1),
            currentZoneId = "Asia/Tokyo",
            bootSessionId = "boot-count-304",
            lastSensorValue = 6_637,
            accumulatedTodaySteps = 6_635,
        )
        val stalePersisted = state
        var totalDelta = 0L

        for (raw in 6_638..7_106) {
            val result = counter.accept(
                raw.toFloat(), state, at, java.time.ZoneId.of("Asia/Tokyo"), "boot-count-304",
            ) as StepEventResult.Added
            totalDelta += result.delta
            state = mergeCounterStateWithPersistedDiagnostics(result.state, stalePersisted)
        }

        assertEquals(469L, totalDelta)
        assertEquals(7_106L, state.lastSensorValue)
        assertEquals(7_104L, state.accumulatedTodaySteps)
    }

    @Test
    fun persistedDiagnosticsCannotRollBackCounterState() {
        val oldDiagnosticTime = Instant.parse("2026-08-01T14:00:00Z")
        val newDiagnosticTime = Instant.parse("2026-08-01T14:01:00Z")
        val counterState = StepTrackingState(
            lastSensorValue = 7_106,
            previousSensorValue = 7_105,
            accumulatedTodaySteps = 7_104,
            lastHeartbeatAt = oldDiagnosticTime,
        )
        val persisted = StepTrackingState(
            lastSensorValue = 7_104,
            accumulatedTodaySteps = 7_102,
            lastNotificationAt = newDiagnosticTime,
        )

        val merged = mergeCounterStateWithPersistedDiagnostics(counterState, persisted)

        assertEquals(7_106L, merged.lastSensorValue)
        assertEquals(7_105L, merged.previousSensorValue)
        assertEquals(7_104L, merged.accumulatedTodaySteps)
        assertEquals(oldDiagnosticTime, merged.lastHeartbeatAt)
        assertEquals(newDiagnosticTime, merged.lastNotificationAt)
    }
    @Test fun counter_recoversOnlyFromStaleStatusesUsingPreviousState() {
        val previous = StepTrackingState(
            trackingRequested = true,
            trackingStatus = TrackingStatus.SENSOR_DATA_STALE,
        )
        val result = StepEventResult.Added(1, false, previous)
        assertEquals(TrackingStatus.TRACKING, recoverStatusOnCounterEvent(previous, result, 501f).trackingStatus)
        val trackingResult = StepEventResult.Added(1, false, previous.copy(trackingStatus = TrackingStatus.TRACKING))
        assertEquals(TrackingStatus.TRACKING, recoverStatusOnCounterEvent(previous.copy(trackingStatus = TrackingStatus.TRACKING), trackingResult, 501f).trackingStatus)
    }

    @Test fun counter_preservesResetResult() {
        val previous = StepTrackingState(trackingRequested = true, trackingStatus = TrackingStatus.TRACKING)
        val result = StepEventResult.Reset(previous.copy(trackingStatus = TrackingStatus.RESTARTED))
        assertEquals(TrackingStatus.RESTARTED, recoverStatusOnCounterEvent(previous, result, 499f).trackingStatus)
    }

    @Test fun counter_doesNotRecoverFromInvalidSamplesOrStoppedState() {
        val previous = StepTrackingState(trackingRequested = true, trackingStatus = TrackingStatus.SENSOR_DATA_STALE)
        val result = StepEventResult.Added(1, false, previous)
        assertEquals(TrackingStatus.TRACKING, recoverStatusOnCounterEvent(previous, result, 1f).trackingStatus)
        assertEquals(TrackingStatus.SENSOR_DATA_STALE, recoverStatusOnCounterEvent(previous, result, Float.NaN).trackingStatus)
        assertEquals(TrackingStatus.SENSOR_DATA_STALE, recoverStatusOnCounterEvent(previous, result, Float.POSITIVE_INFINITY).trackingStatus)
        assertEquals(TrackingStatus.SENSOR_DATA_STALE, recoverStatusOnCounterEvent(previous, result, -1f).trackingStatus)
        val stopped = previous.copy(trackingRequested = false, serviceRunning = false)
        val stoppedResult = StepEventResult.Ignored(stopped)
        assertEquals(TrackingStatus.SENSOR_DATA_STALE, recoverStatusOnCounterEvent(stopped, stoppedResult, 1f).trackingStatus)
        assertFalse(recoverStatusOnCounterEvent(stopped, stoppedResult, 1f).serviceRunning)
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
        assertEquals(base, recoverStatusOnDetectorEvent(base, Instant.EPOCH))
        val stopped = base.copy(serviceRunning = false)
        assertEquals(stopped, recoverStatusOnDetectorEvent(stopped, Instant.EPOCH))
    }

    @Test fun detector_updatesEventTimeButPreservesNonStaleStatusWhenActive() {
        val eventAt = Instant.parse("2026-07-30T00:00:02Z")
        val state = StepTrackingState(
            trackingRequested = true,
            stepDetectorRegistered = true,
            serviceRunning = true,
            trackingStatus = TrackingStatus.PERMISSION_REQUIRED,
        )
        val result = recoverStatusOnDetectorEvent(state, eventAt)
        assertEquals(TrackingStatus.PERMISSION_REQUIRED, result.trackingStatus)
        assertEquals(eventAt, result.lastSensorEventAt)
        assertTrue(result.serviceRunning)
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
