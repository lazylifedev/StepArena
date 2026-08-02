package com.lazyapps.steparena.service.tracking

import com.lazyapps.steparena.tracking.StepTrackingState
import com.lazyapps.steparena.tracking.TrackingStatus
import java.time.Instant
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TrackingServiceReconcilerTest {
    @Test fun stalePersistedTrueFlagsDoNotSuppressStart() {
        val stale = StepTrackingState(
            trackingRequested = true,
            onboardingComplete = true,
            trackingStatus = TrackingStatus.TRACKING,
            serviceRunning = true,
            stepCounterRegistered = true,
            lastHeartbeatAt = Instant.now().minusSeconds(203 * 60L),
        )
        assertTrue(shouldRequestTrackingServiceStart(stale, permissionGranted = true))
    }

    @Test fun stoppedOrDeniedOrOnboardingDoesNotStart() {
        assertFalse(shouldRequestTrackingServiceStart(StepTrackingState(), true))
        val requested = StepTrackingState(trackingRequested = true, onboardingComplete = true)
        assertFalse(shouldRequestTrackingServiceStart(requested, false))
        assertFalse(shouldRequestTrackingServiceStart(requested.copy(onboardingComplete = false), true))
    }
}
