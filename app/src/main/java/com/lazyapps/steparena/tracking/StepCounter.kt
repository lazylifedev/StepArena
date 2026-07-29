package com.lazyapps.steparena.tracking

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import kotlin.math.floor

class StepCounter(private val unusualDeltaThreshold: Long = 10_000) {
    fun accept(
        rawValue: Float,
        previous: StepTrackingState,
        now: Instant,
        zoneId: ZoneId,
        bootSessionId: String,
        trackingRequested: Boolean = previous.trackingRequested,
    ): StepEventResult {
        if (!trackingRequested || !rawValue.isFinite() || rawValue < 0f) {
            return StepEventResult.Ignored(previous)
        }
        val value = floor(rawValue.toDouble()).toLong()
        val today = now.atZone(zoneId).toLocalDate()
        var state = previous
        if (previous.currentLocalDate != today || previous.currentZoneId != zoneId.id) {
            state = previous.copy(
                accumulatedTodaySteps = 0,
                currentLocalDate = today,
                currentZoneId = zoneId.id,
            )
        }
        val rebooted = state.bootSessionId.isNotEmpty() && state.bootSessionId != bootSessionId
        val last = state.lastSensorValue
        if (last == null || rebooted) {
            return StepEventResult.Baseline(
                state.copy(
                    bootSessionId = bootSessionId,
                    sensorBaseline = value,
                    lastSensorValue = value,
                    lastSensorEventAt = now,
                    trackingStatus = if (rebooted) TrackingStatus.RESTARTED else TrackingStatus.TRACKING,
                ),
            )
        }
        if (value < last) {
            return StepEventResult.Reset(
                state.copy(
                    bootSessionId = bootSessionId,
                    sensorBaseline = value,
                    lastSensorValue = value,
                    lastSensorEventAt = now,
                    trackingStatus = TrackingStatus.RESTARTED,
                ),
            )
        }
        val delta = value - last
        if (delta == 0L) return StepEventResult.Ignored(state.copy(lastSensorEventAt = now))
        return StepEventResult.Added(
            delta = delta,
            unusuallyLarge = delta > unusualDeltaThreshold,
            state = state.copy(
                bootSessionId = bootSessionId,
                previousSensorValue = last,
                lastSensorValue = value,
                lastStepIncreaseAt = now,
                accumulatedTodaySteps = (state.accumulatedTodaySteps + delta).coerceAtMost(Long.MAX_VALUE),
                lastSensorEventAt = now,
                trackingStatus = TrackingStatus.TRACKING,
                needsReview = state.needsReview || delta > unusualDeltaThreshold,
            ),
        )
    }
}
