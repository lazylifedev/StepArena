package com.lazyapps.steparena.feature.diagnostics

import com.lazyapps.steparena.tracking.StepTrackingState
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant

class HeartbeatPolicyTest {
    private val now = Instant.parse("2026-07-29T12:00:00Z")

    @Test fun heartbeatStale_onlyWhenRequestedAndOlderThanTenMinutes() {
        assertFalse(isHeartbeatStale(StepTrackingState(), now))
        assertTrue(isHeartbeatStale(StepTrackingState(trackingRequested = true), now))
        assertFalse(
            isHeartbeatStale(
                StepTrackingState(trackingRequested = true, lastHeartbeatAt = now.minusSeconds(600)),
                now,
            ),
        )
        assertTrue(
            isHeartbeatStale(
                StepTrackingState(trackingRequested = true, lastHeartbeatAt = now.minusSeconds(661)),
                now,
            ),
        )
    }
}
