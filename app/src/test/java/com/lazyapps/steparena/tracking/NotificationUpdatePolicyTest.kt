package com.lazyapps.steparena.tracking

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Duration
import java.time.Instant

class NotificationUpdatePolicyTest {
    private val policy = NotificationUpdatePolicy()
    private val now = Instant.parse("2026-07-29T10:00:20Z")

    @Test fun singleStepBeforeInterval_isThrottled() {
        assertFalse(policy.shouldUpdate(101, 100, now, now.minusMillis(500)))
    }

    @Test fun oneStepAfterOneSecond_updates() {
        assertTrue(policy.shouldUpdate(101, 100, now, now.minusSeconds(1)))
    }

    @Test fun unchangedValueDoesNotUpdateOnlyBecauseTimeElapsed() {
        assertFalse(policy.shouldUpdate(100, 100, now, now.minusSeconds(15)))
    }

    @Test fun heartbeatCanForceUpdate() {
        assertTrue(policy.shouldUpdate(100, 100, now, now, force = true))
    }
}
