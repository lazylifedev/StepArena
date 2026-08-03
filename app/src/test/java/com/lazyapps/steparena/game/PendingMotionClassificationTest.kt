package com.lazyapps.steparena.game

import kotlin.random.Random
import org.junit.Assert.assertEquals
import org.junit.Test

class PendingMotionClassificationTest {
    private val base = CompetitiveAllocation(100, 0, 0, CompetitiveIntegrityAssessment.TRUSTED, emptySet(), 3)
    private val confirmed = ResolvedMotionAllocation("a", 30, MotionEvidenceAssessment.SHAKE_CONFIRMED)
    private val suspected = ResolvedMotionAllocation("b", 20, MotionEvidenceAssessment.SHAKE_SUSPECTED)

    @Test fun multipleWindowsAreCumulativeAndOrderIndependent() {
        val first = finalizePendingMotionClassification(100, base, listOf(confirmed, suspected))
        val second = finalizePendingMotionClassification(100, base, listOf(suspected, confirmed))
        assertEquals(first, second)
        assertEquals(listOf(50L, 20L, 30L), listOf(first.eligibleSteps, first.restrictedSteps, first.excludedSteps))
        assertEquals(CompetitiveIntegrityAssessment.REVIEW, first.assessment)
    }

    @Test fun walkLikeAndUnknownDoNotEraseConfirmedOrBaseRestrictions() {
        val classified = finalizePendingMotionClassification(100,
            CompetitiveAllocation(80, 20, 0, CompetitiveIntegrityAssessment.LIMITED,
                setOf(CompetitiveIntegrityReason.COUNTER_BURST), 3),
            listOf(confirmed, ResolvedMotionAllocation("walk", 40, MotionEvidenceAssessment.WALK_LIKE),
                ResolvedMotionAllocation("unknown", 10, MotionEvidenceAssessment.UNKNOWN)))
        assertEquals(30L, classified.excludedSteps)
        assertEquals(20L, classified.restrictedSteps)
        assertEquals(50L, classified.eligibleSteps)
        assertEquals(setOf(CompetitiveIntegrityReason.COUNTER_BURST,
            CompetitiveIntegrityReason.DEVICE_SHAKE_CONFIRMED), classified.reasons)
    }

    @Test fun fullConfirmedIsExcludedAndPartialConfirmedIsReview() {
        assertEquals(CompetitiveIntegrityAssessment.REVIEW,
            finalizePendingMotionClassification(100, base, listOf(confirmed)).assessment)
        assertEquals(CompetitiveIntegrityAssessment.EXCLUDED,
            finalizePendingMotionClassification(100, base,
                listOf(confirmed.copy(assignedSteps = 100))).assessment)
    }

    @Test fun randomizedWindowOrderHasStableDigest() {
        val windows = listOf(confirmed, suspected,
            ResolvedMotionAllocation("c", 50, MotionEvidenceAssessment.UNKNOWN))
        val expected = finalizePendingMotionClassification(100, base, windows)
        repeat(1_000) { seed ->
            assertEquals(expected, finalizePendingMotionClassification(100, base, windows.shuffled(Random(seed))))
        }
    }
}
