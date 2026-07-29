package com.lazyapps.steparena.tracking

import java.time.Duration
import java.time.Instant

class NotificationUpdatePolicy(
    private val stepThreshold: Long = 10,
    private val timeThreshold: Duration = Duration.ofSeconds(15),
) {
    fun shouldUpdate(
        currentSteps: Long,
        lastSteps: Long,
        now: Instant,
        lastAt: Instant,
        force: Boolean = false,
    ): Boolean = force ||
        currentSteps - lastSteps >= stepThreshold ||
        Duration.between(lastAt, now) >= timeThreshold
}
