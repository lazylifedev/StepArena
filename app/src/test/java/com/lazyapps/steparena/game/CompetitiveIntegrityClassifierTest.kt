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

    @Test fun impossibleHumanCadence_isExcludedFromCompetitionOnly() {
        val result = classify(400, detectorEvents = 400, detectorAvailable = true)
        assertEquals(CompetitiveIntegrityAssessment.EXCLUDED, result.assessment)
        assertEquals(0, result.eligibleSteps)
        assertEquals(400, result.excludedSteps)
        assertTrue(CompetitiveIntegrityReason.IMPOSSIBLE_CADENCE in result.reasons)
    }

    @Test fun largeCounterBurst_isReview() {
        val result = classifier.classify(
            CompetitiveIntegrityInput(1_000, end.minusSeconds(300), end, 0, false, false, false),
        )
        assertEquals(CompetitiveIntegrityAssessment.REVIEW, result.assessment)
        assertEquals(1_000, result.restrictedSteps)
    }

    @Test fun longGapBatch_isLimitedNotExcluded() {
        val result = classifier.classify(
            CompetitiveIntegrityInput(
                5_000, end.minusSeconds(3 * 3_600), end, 0, false, false, true,
            ),
        )
        assertEquals(CompetitiveIntegrityAssessment.LIMITED, result.assessment)
        assertEquals(0, result.excludedSteps)
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
        assertEquals(100, result.restrictedSteps)
    }

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
