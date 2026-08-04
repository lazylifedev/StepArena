package com.lazyapps.steparena.service.tracking

import com.lazyapps.steparena.game.MotionEvidence
import com.lazyapps.steparena.game.MotionEvidenceAnalyzer
import com.lazyapps.steparena.game.MotionEvidenceAssessment
import com.lazyapps.steparena.game.MotionSample
import java.time.Instant

data class CompletedMotionWindow(val id: String, val detectorTimes: List<Instant>, val evidence: MotionEvidence)

class MotionCaptureController(
    private val analyzer: MotionEvidenceAnalyzer = MotionEvidenceAnalyzer(),
    private val maxSamplesPerSensor: Int = 512,
) {
    private var sequence = 0L
    private var windowId: String? = null
    private val detectors = ArrayDeque<Instant>()
    private val acceleration = ArrayDeque<MotionSample>()
    private val gyroscope = ArrayDeque<MotionSample>()

    @Synchronized fun onDetector(at: Instant): String {
        if (windowId == null) windowId = "motion-${++sequence}"
        detectors.addLast(at)
        return windowId!!
    }

    @Synchronized fun addGyroscope(sample: MotionSample) = addBounded(gyroscope, sample)
    @Synchronized fun addLinearAcceleration(sample: MotionSample) = addBounded(acceleration, sample)
    @Synchronized fun isCapturing(): Boolean = windowId != null
    @Synchronized fun sampleCounts(): Pair<Int, Int> = acceleration.size to gyroscope.size

    @Synchronized fun finish(): CompletedMotionWindow? {
        val id = windowId ?: return null
        val analyzed = analyzer.analyze(acceleration.toList(), gyroscope.toList())
        val evidence = if (
            analyzed.assessment == MotionEvidenceAssessment.SHAKE_CONFIRMED && detectors.size < MIN_CONFIRMATION_DETECTORS
        ) analyzed.copy(assessment = MotionEvidenceAssessment.SHAKE_SUSPECTED, confidence = 0.60) else analyzed
        val result = CompletedMotionWindow(id, detectors.toList(), evidence)
        resetLocked()
        return result
    }

    @Synchronized fun reset() = resetLocked()

    private fun addBounded(target: ArrayDeque<MotionSample>, sample: MotionSample) {
        if (windowId == null) return
        if (target.size == maxSamplesPerSensor) target.removeFirst()
        target.addLast(sample)
    }

    private fun resetLocked() {
        windowId = null
        detectors.clear()
        acceleration.clear()
        gyroscope.clear()
    }

    private companion object { const val MIN_CONFIRMATION_DETECTORS = 3 }
}
