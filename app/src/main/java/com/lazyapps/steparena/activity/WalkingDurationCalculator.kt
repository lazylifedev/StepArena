package com.lazyapps.steparena.activity

import com.lazyapps.steparena.core.database.model.DataQuality
import java.time.Duration
import java.time.Instant
import kotlin.math.roundToLong

data class WalkingDurationResult(
    val totalSeconds: Long,
    val measuredSeconds: Long,
    val estimatedSeconds: Long,
    val quality: DataQuality,
    val detectorCoverage: Double,
    val cadenceStepsPerMinute: Double?,
)

class WalkingDurationCalculator(
    private val activeGapThresholdSeconds: Long = 60,
    private val defaultCadenceStepsPerMinute: Double = DEFAULT_CADENCE,
) {
    fun calculate(
        deltaSteps: Long,
        detectorEvents: List<Instant>,
        previousCounterAt: Instant?,
        currentCounterAt: Instant,
        recovered: Boolean = false,
        learnedCadenceStepsPerMinute: Double? = null,
    ): WalkingDurationResult {
        if (deltaSteps <= 0) return emptyResult()
        val detectorCount = detectorEvents.size.coerceAtMost(deltaSteps.toIntSafe()).toLong()
        val coverage = detectorCount.toDouble() / deltaSteps
        val detectorSeconds = fromDetectorEvents(detectorEvents)
        val cadence = learnedCadenceStepsPerMinute
            ?.takeIf { it in MIN_CADENCE..MAX_CADENCE } ?: defaultCadenceStepsPerMinute

        if (!recovered && detectorCount >= MIN_DETECTOR_EVENTS_FOR_TIMING &&
            coverage >= HIGH_COVERAGE && detectorSeconds > 0
        ) {
            val checked = validateDuration(deltaSteps, detectorSeconds, cadence)
            val measured = if (checked == detectorSeconds) checked else 0
            return result(deltaSteps, checked, measured, checked - measured, coverage,
                if (measured > 0) DataQuality.MEASURED else DataQuality.ESTIMATED)
        }

        if (!recovered && detectorCount >= MIN_DETECTOR_EVENTS_FOR_TIMING && detectorSeconds > 0) {
            val missing = (deltaSteps - detectorCount).coerceAtLeast(0)
            val estimated = estimateSeconds(missing, cadence)
            val combined = validateDuration(deltaSteps, detectorSeconds + estimated, cadence)
            val measured = detectorSeconds.coerceAtMost(combined)
            return result(deltaSteps, combined, measured, combined - measured, coverage, DataQuality.MIXED)
        }

        val counterSeconds = fromCounterEvents(previousCounterAt, currentCounterAt)
        val candidate = counterSeconds.takeIf { it > 0 } ?: estimateSeconds(deltaSteps, cadence)
        val total = validateDuration(deltaSteps, candidate, cadence)
        return result(deltaSteps, total, 0, total, coverage,
            if (recovered) DataQuality.RECOVERED else DataQuality.ESTIMATED)
    }

    fun fromDetectorEvents(events: List<Instant>): Long =
        (events.sorted().zipWithNext().sumOf { (previous, current) ->
            safeGapMillis(previous, current)
        } / 1_000.0).roundToLong()

    fun fromCounterEvents(previous: Instant?, current: Instant): Long =
        if (previous == null) 0 else safeGap(previous, current)

    fun estimateSeconds(steps: Long, cadence: Double = defaultCadenceStepsPerMinute): Long =
        (steps.coerceAtLeast(0) * 60.0 / cadence.coerceIn(MIN_CADENCE, MAX_CADENCE)).roundToLong()

    private fun validateDuration(steps: Long, seconds: Long, fallbackCadence: Double): Long {
        if (steps < MIN_STEPS_FOR_CADENCE_CHECK || seconds <= 0) return seconds.coerceAtLeast(0)
        val cadence = steps * 60.0 / seconds
        return when {
            cadence > MAX_CADENCE -> estimateSeconds(steps, MAX_CADENCE)
            cadence < MIN_CADENCE -> estimateSeconds(steps, MIN_CADENCE)
            else -> seconds
        }.takeIf { it > 0 } ?: estimateSeconds(steps, fallbackCadence)
    }

    private fun result(steps: Long, total: Long, measured: Long, estimated: Long, coverage: Double,
        quality: DataQuality) = WalkingDurationResult(total, measured, estimated, quality,
        coverage.coerceIn(0.0, 1.0), if (total > 0) steps * 60.0 / total else null)

    private fun emptyResult() = WalkingDurationResult(0, 0, 0, DataQuality.UNKNOWN, 0.0, null)

    private fun safeGap(previous: Instant, current: Instant): Long {
        val seconds = runCatching { Duration.between(previous, current).seconds }.getOrDefault(0)
        return seconds.takeIf { it in 0..activeGapThresholdSeconds } ?: 0
    }

    private fun safeGapMillis(previous: Instant, current: Instant): Long {
        val millis = runCatching { Duration.between(previous, current).toMillis() }.getOrDefault(0)
        return millis.takeIf { it in 0..activeGapThresholdSeconds * 1_000 } ?: 0
    }

    private fun Long.toIntSafe() = coerceAtMost(Int.MAX_VALUE.toLong()).toInt()

    companion object {
        const val HIGH_COVERAGE = 0.8
        const val MIN_DETECTOR_EVENTS_FOR_TIMING = 2L
        const val DEFAULT_CADENCE = 100.0
        const val MIN_CADENCE = 60.0
        const val MAX_CADENCE = 180.0
        const val MIN_STEPS_FOR_CADENCE_CHECK = 20L
    }
}
