package com.lazyapps.steparena.recovery

import org.junit.Assert.assertEquals
import org.junit.Test

class RecoveryAmountPolicyTest {
    @Test fun measuredFiveThousand_externalFiveThousand_addsNothing() {
        assertEquals(0L, recoverableSteps(5_000, 5_000, 0))
    }

    @Test fun externalHigherWithoutMissingMeasuredInterval_addsOnlyIntervalDifference() {
        assertEquals(0L, recoverableSteps(5_200, 5_200, 0))
    }

    @Test fun emptyMeasuredGap_recoversExternalCount() {
        assertEquals(200L, recoverableSteps(200, 0, 0))
    }

    @Test fun rerunningSameRecovery_isIdempotent() {
        assertEquals(0L, recoverableSteps(200, 0, 200))
    }

    @Test fun partialOverlap_recoversOnlyDifference() {
        assertEquals(80L, recoverableSteps(200, 120, 0))
    }

    @Test fun reportedFailureFixture_doesNotMixRecoveryIntoMeasuredTotal() {
        val measured = 5_864L
        val recovered = 2_219L
        assertEquals(5_864L, measured)
        assertEquals(8_083L, measured + recovered)
    }
}
