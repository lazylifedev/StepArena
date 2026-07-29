package com.lazyapps.steparena.tracking

import android.os.SystemClock
import java.time.Instant

fun interface SensorEventClock {
    fun toInstant(eventTimestampNanos: Long): Instant
}

class RealtimeSensorEventClock(
    private val instantNow: () -> Instant = Instant::now,
    private val elapsedRealtimeNanos: () -> Long = SystemClock::elapsedRealtimeNanos,
) : SensorEventClock {
    override fun toInstant(eventTimestampNanos: Long): Instant {
        val receivedAt = instantNow()
        val elapsed = elapsedRealtimeNanos()
        if (eventTimestampNanos < 0L || elapsed < 0L || eventTimestampNanos > elapsed) {
            return receivedAt
        }
        val ageNanos = elapsed - eventTimestampNanos
        return runCatching { receivedAt.minusNanos(ageNanos) }
            .getOrNull()
            ?.takeUnless { it.isAfter(receivedAt) }
            ?: receivedAt
    }
}
