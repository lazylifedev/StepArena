package com.lazyapps.steparena.game

import org.junit.Assert.assertEquals
import org.junit.Test

class OfficialStepsTest {
    @Test fun officialStepsClampAllBoundaryValues() {
        val expected = listOf(0L, 1L, 9_999L, 10_000L, 10_001L, 12_500L,
            19_999L, 20_000L, 20_001L, 29_999L, 30_000L, 99_999L, 100_000L, 100_000L)
        val actual = listOf(0L, 1L, 9_999L, 10_000L, 10_001L, 12_500L,
            19_999L, 20_000L, 20_001L, 29_999L, 30_000L, 99_999L, 100_000L, 100_001L)
            .map(OfficialSteps::fromEligible)
        assertEquals(expected, actual)
    }

    @Test fun negativeEligibleStepsBecomeZero() {
        assertEquals(0L, OfficialSteps.fromEligible(-1L))
    }

    @Test fun rewardLimitDoesNotLimitOfficialSteps() {
        assertEquals(38_000L, OfficialSteps.fromEligible(38_000L))
        assertEquals(30_000L, OfficialSteps.REWARD_LIMIT)
    }
}
