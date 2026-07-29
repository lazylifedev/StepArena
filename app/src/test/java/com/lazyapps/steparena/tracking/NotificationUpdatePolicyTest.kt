package com.lazyapps.steparena.tracking

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Duration
import java.time.Instant

class NotificationUpdatePolicyTest {
    private val policy = NotificationUpdatePolicy(10, Duration.ofSeconds(15))
    private val now = Instant.parse("2026-07-29T10:00:20Z")

    @Test fun singleStepBeforeInterval_isThrottled() {
        assertFalse(policy.shouldUpdate(101, 100, now, now.minusSeconds(5)))
    }

    @Test fun tenSteps_updates() {
        assertTrue(policy.shouldUpdate(110, 100, now, now.minusSeconds(5)))
    }

    @Test fun elapsedInterval_updates() {
        assertTrue(policy.shouldUpdate(101, 100, now, now.minusSeconds(15)))
    }

    @Test fun heartbeatCanForceUpdate() {
        assertTrue(policy.shouldUpdate(100, 100, now, now, force = true))
    }
}
