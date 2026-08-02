package com.lazyapps.steparena.tracking

import java.time.Duration
import java.time.Instant

class NotificationUpdatePolicy(
    private val stepThreshold: Long = 1,
    private val timeThreshold: Duration = Duration.ofMillis(350),
) {
    fun shouldUpdate(
        currentSteps: Long,
        lastSteps: Long,
        now: Instant,
        lastAt: Instant,
        force: Boolean = false,
    ): Boolean = force || (
        currentSteps - lastSteps >= stepThreshold &&
            Duration.between(lastAt, now) >= timeThreshold
        )

    fun remainingDelayMillis(now: Instant, lastAt: Instant): Long =
        (timeThreshold.toMillis() - Duration.between(lastAt, now).toMillis()).coerceAtLeast(1)
}
