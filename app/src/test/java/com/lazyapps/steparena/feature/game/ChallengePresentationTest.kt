package com.lazyapps.steparena.feature.game

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ChallengePresentationTest {
    @Test fun historyRatingDeltaUsesCompactSignedForm() {
        assertEquals("+12", signedRating(12))
        assertEquals("-3", signedRating(-3))
        assertEquals("0", signedRating(0))
        assertEquals("—", signedRating(null))
    }
    @Test fun phase721FixtureKeepsTotalEligiblePartnerAndRemainingDistinct() {
        val comparison = challengeComparison(
            current = CurrentChallengeSteps(
                displayedUserSteps = Phase721ChallengeFixture.DEVICE_MEASURED_STEPS,
                eligibleSteps = Phase721ChallengeFixture.CHALLENGE_ELIGIBLE_STEPS,
                isFinalized = false,
            ),
            healthConnectAddedSteps = Phase721ChallengeFixture.HEALTH_CONNECT_ADDED_STEPS,
            partnerTargetSteps = Phase721ChallengeFixture.PARTNER_TARGET_STEPS,
        )

        assertEquals(Phase721ChallengeFixture.HOME_TOTAL_STEPS, comparison.totalSteps)
        assertEquals(
            Phase721ChallengeFixture.CHALLENGE_ELIGIBLE_STEPS,
            comparison.eligibleSteps,
        )
        assertEquals(
            Phase721ChallengeFixture.PARTNER_TARGET_STEPS,
            comparison.partnerTargetSteps,
        )
        assertEquals(Phase721ChallengeFixture.REMAINING_STEPS, comparison.remainingSteps)
        assertTrue(comparison.showsTotalBreakdown)
        assertFalse(comparison.goalAchieved)
    }

    @Test fun finalizedChallengeDoesNotAddCurrentHealthConnectStepsAgain() {
        val comparison = challengeComparison(
            CurrentChallengeSteps(3_619, 3_619, true),
            healthConnectAddedSteps = 20,
            partnerTargetSteps = 5_483,
        )

        assertEquals(3_619, comparison.totalSteps)
        assertFalse(comparison.showsTotalBreakdown)
    }

    @Test fun celebrationPolicyOnlyAllowsOneEventPerDailyMatch() {
        assertTrue(shouldCelebrateChallenge(null, "daily-2026-07-31", 5_483, 5_483))
        assertFalse(
            shouldCelebrateChallenge(
                "daily-2026-07-31",
                "daily-2026-07-31",
                6_000,
                5_483,
            ),
        )
        assertFalse(shouldCelebrateChallenge(null, "daily-2026-07-31", 5_482, 5_483))
    }
}
