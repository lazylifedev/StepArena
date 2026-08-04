package com.lazyapps.steparena.game

import java.time.Instant
import kotlin.math.abs
import kotlin.math.sqrt

data class MotionSample(val timestampNanos: Long, val x: Float, val y: Float, val z: Float)

enum class MotionEvidenceAssessment { UNKNOWN, WALK_LIKE, SHAKE_SUSPECTED, SHAKE_CONFIRMED }

data class DetectorEvidence(
    val at: Instant,
    val assessment: MotionEvidenceAssessment = MotionEvidenceAssessment.UNKNOWN,
    val confidence: Double = 0.0,
    val evidenceWindowId: String? = null,
)

data class MotionEvidence(
    val assessment: MotionEvidenceAssessment,
    val confidence: Double,
    val sampleDurationMillis: Long,
    val accelerometerSamples: Int,
    val gyroscopeSamples: Int,
    val gyroRms: Double?,
    val gyroPeak: Double?,
    val angularReversalRate: Double?,
    val accelerationRms: Double?,
    val accelerationPeak: Double?,
    val dominantFrequencyHz: Double?,
    val gyroCoverage: Double = 0.0,
    val accelerationCoverage: Double = 0.0,
    val periodicityScore: Double = 0.0,
)

data class MotionEvidenceThresholds(
    val minimumDurationMillis: Long = 2_000,
    val expectedSamplingHz: Double = 20.0,
    val minimumCoverage: Double = 0.70,
    val suspectedGyroRms: Double = 2.5,
    val confirmedGyroRms: Double = 5.0,
    val confirmedAccelerationRms: Double = 4.0,
    val confirmedReversalRate: Double = 5.0,
    val minimumShakeFrequencyHz: Double = 3.2,
    val minimumPeriodicityScore: Double = 0.55,
)

/** Pure Kotlin, deterministic and deliberately biased against false positives. */
class MotionEvidenceAnalyzer(private val thresholds: MotionEvidenceThresholds = MotionEvidenceThresholds()) {
    fun analyze(accelerometer: List<MotionSample>, gyroscope: List<MotionSample>): MotionEvidence {
        val accel = sanitize(accelerometer)
        val gyro = sanitize(gyroscope)
        val all = (accel + gyro).sortedBy { it.timestampNanos }
        val durationMillis = all.durationMillis()
        val expected = (durationMillis * thresholds.expectedSamplingHz / 1_000.0).coerceAtLeast(1.0)
        val gyroCoverage = (gyro.size / expected).coerceIn(0.0, 1.0)
        val accelCoverage = (accel.size / expected).coerceIn(0.0, 1.0)
        val gyroMagnitudes = gyro.map(::magnitude)
        val accelMagnitudes = accel.map(::magnitude)
        val gyroRms = gyroMagnitudes.rmsOrNull()
        val accelRms = accelMagnitudes.rmsOrNull()
        val reversalRate = reversalRate(gyro)
        val dominantFrequency = dominantFrequency(gyro)
        val periodicity = periodicityScore(gyro)
        val sufficient = durationMillis >= thresholds.minimumDurationMillis &&
            gyroCoverage >= thresholds.minimumCoverage && accelCoverage >= thresholds.minimumCoverage
        val confirmed = sufficient && gyroRms != null && accelRms != null && reversalRate != null &&
            dominantFrequency != null && gyroRms >= thresholds.confirmedGyroRms &&
            accelRms >= thresholds.confirmedAccelerationRms &&
            reversalRate >= thresholds.confirmedReversalRate &&
            dominantFrequency >= thresholds.minimumShakeFrequencyHz &&
            periodicity >= thresholds.minimumPeriodicityScore
        val suspected = gyroRms != null && gyroRms >= thresholds.suspectedGyroRms
        val assessment = when {
            confirmed -> MotionEvidenceAssessment.SHAKE_CONFIRMED
            durationMillis >= thresholds.minimumDurationMillis && gyroCoverage < thresholds.minimumCoverage ->
                MotionEvidenceAssessment.UNKNOWN
            suspected -> MotionEvidenceAssessment.SHAKE_SUSPECTED
            sufficient -> MotionEvidenceAssessment.WALK_LIKE
            else -> MotionEvidenceAssessment.UNKNOWN
        }
        val confidence = when (assessment) {
            MotionEvidenceAssessment.SHAKE_CONFIRMED -> 0.95
            MotionEvidenceAssessment.SHAKE_SUSPECTED -> 0.60
            MotionEvidenceAssessment.WALK_LIKE -> 0.55
            MotionEvidenceAssessment.UNKNOWN -> 0.0
        }
        return MotionEvidence(
            assessment, confidence, durationMillis, accel.size, gyro.size,
            gyroRms, gyroMagnitudes.maxOrNull(), reversalRate, accelRms,
            accelMagnitudes.maxOrNull(), dominantFrequency, gyroCoverage, accelCoverage, periodicity,
        )
    }

    private fun sanitize(samples: List<MotionSample>): List<MotionSample> = samples.asSequence()
        .filter { it.x.isFinite() && it.y.isFinite() && it.z.isFinite() && it.timestampNanos >= 0 }
        .sortedBy { it.timestampNanos }
        .distinctBy { it.timestampNanos }
        .take(MAX_SAMPLES)
        .toList()

    private fun List<MotionSample>.durationMillis() = if (size < 2) 0L else
        ((last().timestampNanos - first().timestampNanos) / 1_000_000L).coerceAtLeast(0)

    private fun magnitude(s: MotionSample) = sqrt(s.x * s.x + s.y * s.y + s.z * s.z.toDouble())

    private fun List<Double>.rmsOrNull() = takeIf { it.isNotEmpty() }
        ?.let { sqrt(it.sumOf { value -> value * value } / it.size) }

    private fun principal(samples: List<MotionSample>): List<Double> = samples.map { sample ->
        val values = listOf(sample.x.toDouble(), sample.y.toDouble(), sample.z.toDouble())
        values.maxBy { abs(it) }
    }

    private fun reversalRate(samples: List<MotionSample>): Double? {
        if (samples.size < 3) return null
        val signs = principal(samples).zipWithNext().map { (a, b) -> (b - a).compareTo(0.0) }.filter { it != 0 }
        val seconds = samples.durationMillis() / 1_000.0
        if (signs.size < 2 || seconds <= 0) return null
        return signs.zipWithNext().count { (a, b) -> a != b } / seconds
    }

    private fun dominantFrequency(samples: List<MotionSample>): Double? {
        val seconds = samples.durationMillis() / 1_000.0
        if (samples.size < 3 || seconds <= 0) return null
        val values = principal(samples)
        val mean = values.average()
        val crossings = values.zipWithNext().count { (a, b) -> (a - mean) * (b - mean) < 0 }
        return crossings / (2.0 * seconds)
    }

    private fun periodicityScore(samples: List<MotionSample>): Double {
        if (samples.size < 5) return 0.0
        val deltas = principal(samples).zipWithNext().map { (a, b) -> b - a }
        val reversals = deltas.zipWithNext().filter { (a, b) -> a != 0.0 && b != 0.0 }.map { (a, b) -> a * b < 0 }
        return if (reversals.isEmpty()) 0.0 else reversals.count { it }.toDouble() / reversals.size
    }

    companion object { const val MAX_SAMPLES = 512 }
}
