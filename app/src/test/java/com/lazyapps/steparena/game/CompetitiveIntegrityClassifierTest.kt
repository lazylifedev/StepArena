package com.lazyapps.steparena.game

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant

class CompetitiveIntegrityClassifierTest {
    private val classifier = CompetitiveIntegrityClassifier()
    private val end = Instant.parse("2026-07-31T01:01:00Z")

    @Test fun normalCounterDelta_isTrusted() {
        val result = classify(100, detectorEvents = 80, detectorAvailable = true)
        assertEquals(CompetitiveIntegrityAssessment.TRUSTED, result.assessment)
        assertEquals(100, result.eligibleSteps)
    }

    @Test fun oneImpossibleCadenceSignalRestrictsOnlyImplausibleExcess() {
        val result = classify(400, detectorEvents = 400, detectorAvailable = true)
        assertEquals(CompetitiveIntegrityAssessment.REVIEW, result.assessment)
        assertEquals(220, result.eligibleSteps)
        assertEquals(180, result.restrictedSteps)
        assertEquals(0, result.excludedSteps)
        assertTrue(CompetitiveIntegrityReason.IMPOSSIBLE_CADENCE in result.reasons)
    }

    @Test fun largeCounterBurst_isReview() {
        val result = classifier.classify(
            CompetitiveIntegrityInput(1_000, end.minusSeconds(300), end, 0, false, false, false),
        )
        assertEquals(CompetitiveIntegrityAssessment.REVIEW, result.assessment)
        assertEquals(1_000, result.eligibleSteps)
        assertEquals(0, result.restrictedSteps)
    }

    @Test fun longGapBatch_isLimitedNotExcluded() {
        val result = classifier.classify(
            CompetitiveIntegrityInput(
                5_000, end.minusSeconds(3 * 3_600), end, 0, false, false, true,
            ),
        )
        assertEquals(CompetitiveIntegrityAssessment.LIMITED, result.assessment)
        assertEquals(0, result.excludedSteps)
        assertTrue(result.eligibleSteps > 0)
        assertTrue(CompetitiveIntegrityReason.LONG_GAP_INCREMENT in result.reasons)
    }

    @Test fun detectorUnsupported_doesNotTriggerCoverageReason() {
        val result = classify(100, detectorEvents = 0, detectorAvailable = false)
        assertFalse(CompetitiveIntegrityReason.LOW_DETECTOR_COVERAGE in result.reasons)
    }

    @Test fun detectorSupportedLowCoverage_isLimited() {
        val result = classify(100, detectorEvents = 5, detectorAvailable = true)
        assertEquals(CompetitiveIntegrityAssessment.LIMITED, result.assessment)
        assertTrue(CompetitiveIntegrityReason.LOW_DETECTOR_COVERAGE in result.reasons)
    }

    @Test fun rebootSignal_isLimitedRatherThanDeletingDailySteps() {
        val result = classifier.classify(
            CompetitiveIntegrityInput(100, end.minusSeconds(60), end, 80, true, true, false),
        )
        assertEquals(CompetitiveIntegrityAssessment.LIMITED, result.assessment)
        assertEquals(100, result.totalSteps)
        assertEquals(100, result.eligibleSteps)
    }

    @Test fun normal1593StepAndroidBatchIsNeverReducedToZero() {
        val noDetector = classifier.classify(
            CompetitiveIntegrityInput(1_593, end.minusSeconds(3_600), end, 0, false, false, true),
        )
        val partialDetector = classifier.classify(
            CompetitiveIntegrityInput(1_593, end.minusSeconds(600), end, 100, true, false, false),
        )
        val detectorUnsupported = classifier.classify(
            CompetitiveIntegrityInput(1_593, end.minusSeconds(600), end, 0, false, true, false),
        )
        assertTrue(noDetector.eligibleSteps > 0)
        assertTrue(partialDetector.eligibleSteps > 0)
        assertTrue(detectorUnsupported.eligibleSteps > 0)
    }

    @Test fun motionAllocationIsExactAndBounded() {
        val mixed = classifier.classify(motionInput(100, suspected = 20, confirmed = 30, evaluated = 100))
        assertEquals(50, mixed.eligibleSteps)
        assertEquals(20, mixed.restrictedSteps)
        assertEquals(30, mixed.excludedSteps)
        assertEquals(100, mixed.eligibleSteps + mixed.restrictedSteps + mixed.excludedSteps)

        val over = classifier.classify(motionInput(100, suspected = 50, confirmed = 120, evaluated = 170))
        assertEquals(0, over.eligibleSteps)
        assertEquals(0, over.restrictedSteps)
        assertEquals(100, over.excludedSteps)
    }

    @Test fun unknownOrMissingMotionSensorDoesNotRestrict() {
        val partial = classifier.classify(motionInput(100, suspected = 0, confirmed = 0, evaluated = 30))
        val missing = classifier.classify(motionInput(100, suspected = 20, confirmed = 30, evaluated = 50, available = false))
        assertEquals(100, partial.eligibleSteps)
        assertEquals(100, missing.eligibleSteps)
    }

    private fun motionInput(
        steps: Long,
        suspected: Int,
        confirmed: Int,
        evaluated: Int,
        available: Boolean = true,
    ) = CompetitiveIntegrityInput(
        steps, end.minusSeconds(60), end, steps.toInt(), true, false, false,
        shakeSuspectedDetectorEvents = suspected,
        shakeConfirmedDetectorEvents = confirmed,
        motionEvaluatedDetectorEvents = evaluated,
        motionSensorAvailable = available,
    )

    private fun classify(
        steps: Long,
        detectorEvents: Int = steps.coerceAtMost(Int.MAX_VALUE.toLong()).toInt(),
        detectorAvailable: Boolean = false,
    ) = classifier.classify(
        CompetitiveIntegrityInput(
            steps, end.minusSeconds(60), end, detectorEvents, detectorAvailable, false, false,
        ),
    )
}
