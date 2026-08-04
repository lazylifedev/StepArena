package com.lazyapps.steparena.game

import org.junit.Assert.*
import org.junit.Test

class CompetitiveStepCalculatorTest {
    private val calculator = CompetitiveStepCalculator()

    @Test fun measuredIsFullyEligible() {
        val result = calculator.calculate(CompetitiveStepInput(measured = 10_000))
        assertEquals(10_000, result.eligibleSteps)
        assertEquals(CompetitiveStepQuality.FULL, result.quality)
    }

    @Test fun recoveredEstimatedExternalAndUnknownUsePolicy() {
        val result = calculator.calculate(
            CompetitiveStepInput(recovered = 1_000, externalRecovered = 2_000, estimated = 1_000, unknown = 500),
        )
        assertEquals(500, result.eligibleSteps)
        assertEquals(500, result.excludedSteps)
        assertEquals(3_500, result.restrictedSteps)
        assertFalse(result.reasons.contains(CompetitiveStepRestrictionReason.UNKNOWN_EXCLUDED))
    }

    @Test fun externalRecoveryAndHealthCapAreApplied() {
        val result = calculator.calculate(CompetitiveStepInput(externalRecovered = 20_000))
        assertEquals(0, result.eligibleSteps)
        assertEquals(10_000, result.excludedSteps)
        assertEquals(10_000, result.restrictedSteps)
    }

    @Test fun dailyCompetitiveCapDoesNotDeleteActualSteps() {
        val result = calculator.calculate(CompetitiveStepInput(measured = 40_000))
        assertEquals(40_000, result.totalSteps)
        assertEquals(40_000, result.eligibleSteps)
    }

    @Test fun integrityUnknownNegativeAndOverflowAreSafe() {
        assertEquals(100_000, calculator.calculate(CompetitiveStepInput(measured = 101_000)).eligibleSteps)
        assertEquals(1_000, calculator.calculate(CompetitiveStepInput(unknown = 1_000)).eligibleSteps)
        assertTrue(calculator.calculate(CompetitiveStepInput(measured = -1)).reasons.contains(
            CompetitiveStepRestrictionReason.NEGATIVE_VALUE,
        ))
        val overflow = calculator.calculate(CompetitiveStepInput(measured = Long.MAX_VALUE, recovered = Long.MAX_VALUE))
        assertEquals(Long.MAX_VALUE, overflow.totalSteps)
        assertTrue(overflow.reasons.contains(CompetitiveStepRestrictionReason.OVERFLOW))
    }

    @Test fun officialStepsOnlyCapsEligibleAndPreservesRawTotal() {
        val result = calculator.calculate(CompetitiveStepInput(measured = 120_000))
        assertEquals(120_000, result.totalSteps)
        assertEquals(100_000, OfficialSteps.fromEligible(result.eligibleSteps))
    }
}
