package com.lazyapps.steparena.activity

import java.time.Duration
import java.time.Instant

class WalkingDurationCalculator(
    private val activeGapThresholdSeconds: Long = 60,
) {
    fun fromDetectorEvents(events: List<Instant>): Long =
        events.sorted().zipWithNext().sumOf { (previous, current) ->
            safeGap(previous, current)
        }

    fun fromCounterEvents(previous: Instant?, current: Instant): Long =
        if (previous == null) 0 else safeGap(previous, current)

    private fun safeGap(previous: Instant, current: Instant): Long {
        val seconds = runCatching { Duration.between(previous, current).seconds }.getOrDefault(0)
        return seconds.takeIf { it in 0..activeGapThresholdSeconds } ?: 0
    }
}
