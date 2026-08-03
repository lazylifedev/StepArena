package com.lazyapps.steparena.game

import java.time.Duration
import java.time.Instant
import kotlin.math.sqrt

enum class CompetitiveIntegrityAssessment { TRUSTED, LIMITED, REVIEW, EXCLUDED }

enum class CompetitiveIntegrityReason {
    ABNORMAL_STEPS_PER_MINUTE,
    COUNTER_BURST,
    LOW_DETECTOR_COVERAGE,
    IMPLAUSIBLY_REGULAR_RHYTHM,
    LONG_GAP_INCREMENT,
    REBOOT_OR_RESET,
    IMPOSSIBLE_CADENCE,
    DEVICE_SHAKE_SUSPECTED,
    DEVICE_SHAKE_CONFIRMED,
}

data class CompetitiveIntegrityThresholds(
    val highCadenceStepsPerMinute: Double = 220.0,
    val impossibleCadenceStepsPerMinute: Double = 300.0,
    val counterBurstSteps: Long = 1_000,
    val longGap: Duration = Duration.ofHours(2),
    val minimumDetectorCoverage: Double = 0.20,
    val coverageEvaluationMaxInterval: Duration = Duration.ofMinutes(10),
    val regularRhythmMinimumSamples: Int = 5,
    val regularRhythmMaxDeviation: Double = 1.0,
    val dailyEligibleLimit: Long = 30_000,
    val version: Int = 3,
)

data class CompetitiveIntegrityInput(
    val steps: Long,
    val startedAt: Instant?,
    val endedAt: Instant,
    val detectorEvents: Int,
    val detectorAvailable: Boolean,
    val bootSessionChanged: Boolean,
    val recoveredOrLongGap: Boolean,
    val recentCadences: List<Double> = emptyList(),
    val shakeSuspectedDetectorEvents: Int = 0,
    val shakeConfirmedDetectorEvents: Int = 0,
    val motionEvaluatedDetectorEvents: Int = 0,
    val motionSensorAvailable: Boolean = false,
)

data class CompetitiveIntegrityResult(
    val assessment: CompetitiveIntegrityAssessment,
    val totalSteps: Long,
    val eligibleSteps: Long,
    val restrictedSteps: Long,
    val excludedSteps: Long,
    val reasons: Set<CompetitiveIntegrityReason>,
    val classifierVersion: Int,
)

class CompetitiveIntegrityClassifier(
    private val thresholds: CompetitiveIntegrityThresholds = CompetitiveIntegrityThresholds(),
) {
    fun classify(input: CompetitiveIntegrityInput): CompetitiveIntegrityResult {
        val steps = input.steps.coerceAtLeast(0)
        if (steps == 0L) return result(CompetitiveIntegrityAssessment.TRUSTED, 0, emptySet())
        val duration = input.startedAt?.let { Duration.between(it, input.endedAt) }
            ?.takeIf { !it.isNegative && !it.isZero }
        val cadence = duration?.toMillis()?.takeIf { it > 0 }?.let { steps * 60_000.0 / it }
        val reasons = linkedSetOf<CompetitiveIntegrityReason>()

        // Motion evidence is intentionally opt-in. Missing sensors or unevaluated
        // detectors never restrict an otherwise valid Counter delta.
        if (input.motionSensorAvailable && input.shakeSuspectedDetectorEvents > 0) {
            reasons += CompetitiveIntegrityReason.DEVICE_SHAKE_SUSPECTED
        }
        if (input.motionSensorAvailable && input.shakeConfirmedDetectorEvents > 0) {
            reasons += CompetitiveIntegrityReason.DEVICE_SHAKE_CONFIRMED
        }

        if (cadence != null && cadence > thresholds.impossibleCadenceStepsPerMinute) {
            reasons += CompetitiveIntegrityReason.IMPOSSIBLE_CADENCE
        } else if (cadence != null && cadence > thresholds.highCadenceStepsPerMinute) {
            reasons += CompetitiveIntegrityReason.ABNORMAL_STEPS_PER_MINUTE
        }
        if (steps >= thresholds.counterBurstSteps) reasons += CompetitiveIntegrityReason.COUNTER_BURST
        if (duration != null && duration >= thresholds.longGap || input.recoveredOrLongGap) {
            reasons += CompetitiveIntegrityReason.LONG_GAP_INCREMENT
        }
        if (input.bootSessionChanged) reasons += CompetitiveIntegrityReason.REBOOT_OR_RESET
        if (
            input.detectorAvailable && duration != null && duration <= thresholds.coverageEvaluationMaxInterval &&
            input.detectorEvents.toDouble() / steps < thresholds.minimumDetectorCoverage
        ) reasons += CompetitiveIntegrityReason.LOW_DETECTOR_COVERAGE
        if (isImplausiblyRegular(input.recentCadences + listOfNotNull(cadence))) {
            reasons += CompetitiveIntegrityReason.IMPLAUSIBLY_REGULAR_RHYTHM
        }

        val strongEvidenceCount = listOf(
            CompetitiveIntegrityReason.IMPOSSIBLE_CADENCE,
            CompetitiveIntegrityReason.IMPLAUSIBLY_REGULAR_RHYTHM,
        ).count { it in reasons }
        val assessment = when {
            strongEvidenceCount >= 2 -> CompetitiveIntegrityAssessment.EXCLUDED
            CompetitiveIntegrityReason.IMPOSSIBLE_CADENCE in reasons -> CompetitiveIntegrityAssessment.REVIEW
            CompetitiveIntegrityReason.COUNTER_BURST in reasons &&
                CompetitiveIntegrityReason.LONG_GAP_INCREMENT !in reasons -> CompetitiveIntegrityAssessment.REVIEW
            reasons.isNotEmpty() -> CompetitiveIntegrityAssessment.LIMITED
            else -> CompetitiveIntegrityAssessment.TRUSTED
        }
        val plausibleSteps = duration?.toMillis()?.let {
            (thresholds.highCadenceStepsPerMinute * it / 60_000.0).toLong().coerceAtLeast(1)
        } ?: thresholds.counterBurstSteps
        val base = result(assessment, steps, plausibleSteps, reasons)
        if (!input.motionSensorAvailable) return base
        val motionExcluded = input.shakeConfirmedDetectorEvents.coerceAtLeast(0).toLong().coerceAtMost(steps)
        val afterExcluded = steps - motionExcluded
        val motionRestricted = input.shakeSuspectedDetectorEvents.coerceAtLeast(0).toLong().coerceAtMost(afterExcluded)
        val excluded = maxOf(base.excludedSteps, motionExcluded).coerceAtMost(steps)
        val remaining = steps - excluded
        val restricted = maxOf(base.restrictedSteps.coerceAtMost(remaining), motionRestricted.coerceAtMost(remaining))
        val finalAssessment = when {
            excluded == steps -> CompetitiveIntegrityAssessment.EXCLUDED
            excluded > 0 -> CompetitiveIntegrityAssessment.REVIEW
            restricted > 0 -> CompetitiveIntegrityAssessment.LIMITED
            else -> base.assessment
        }
        return base.copy(
            assessment = finalAssessment,
            eligibleSteps = steps - excluded - restricted,
            restrictedSteps = restricted,
            excludedSteps = excluded,
        )
    }

    private fun isImplausiblyRegular(cadences: List<Double>): Boolean {
        if (cadences.size < thresholds.regularRhythmMinimumSamples) return false
        val recent = cadences.takeLast(thresholds.regularRhythmMinimumSamples)
        if (recent.average() <= thresholds.highCadenceStepsPerMinute) return false
        val mean = recent.average()
        val deviation = sqrt(recent.sumOf { (it - mean) * (it - mean) } / recent.size)
        return deviation <= thresholds.regularRhythmMaxDeviation
    }

    private fun result(
        assessment: CompetitiveIntegrityAssessment,
        steps: Long,
        reasons: Set<CompetitiveIntegrityReason>,
    ): CompetitiveIntegrityResult = result(assessment, steps, steps, reasons)

    private fun result(
        assessment: CompetitiveIntegrityAssessment,
        steps: Long,
        plausibleSteps: Long,
        reasons: Set<CompetitiveIntegrityReason>,
    ): CompetitiveIntegrityResult {
        // This is an abnormal-interval limiter, not a claim of complete hand-swing detection.
        val eligible = when (assessment) {
            CompetitiveIntegrityAssessment.TRUSTED -> steps
            CompetitiveIntegrityAssessment.LIMITED,
            CompetitiveIntegrityAssessment.REVIEW -> steps.coerceAtMost(plausibleSteps.coerceAtLeast(0))
            CompetitiveIntegrityAssessment.EXCLUDED -> 0
        }
        val excluded = if (assessment == CompetitiveIntegrityAssessment.EXCLUDED) steps else 0
        return CompetitiveIntegrityResult(
            assessment, steps, eligible, steps - eligible - excluded, excluded, reasons, thresholds.version,
        )
    }
}
