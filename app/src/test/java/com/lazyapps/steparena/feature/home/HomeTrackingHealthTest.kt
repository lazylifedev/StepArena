package com.lazyapps.steparena.feature.home

import com.lazyapps.steparena.tracking.StepTrackingState
import java.time.Instant
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class HomeTrackingHealthTest {
    private val now = Instant.parse("2026-08-02T09:00:00Z")

    @Test fun staleOrMissingHeartbeatIsNotHealthy() {
        val requested = StepTrackingState(trackingRequested = true)
        assertFalse(hasFreshTrackingHeartbeat(requested, now))
        assertFalse(hasFreshTrackingHeartbeat(requested.copy(lastHeartbeatAt = now.minusSeconds(203 * 60L)), now))
    }

    @Test fun freshHeartbeatIsHealthyOnlyWhileRequested() {
        val fresh = StepTrackingState(trackingRequested = true, lastHeartbeatAt = now.minusSeconds(60))
        assertTrue(hasFreshTrackingHeartbeat(fresh, now))
        assertFalse(hasFreshTrackingHeartbeat(fresh.copy(trackingRequested = false), now))
    }
}
