package com.lazyapps.steparena.game

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ChallengeResultAndMatchingTest {
    @Test fun higherCompetitionStepsWinEvenWhenPersonalTargetsWouldSuggestOtherwise() {
        val result = com.lazyapps.steparena.feature.game.challengeResult(6_000, 8_000)
        assertEquals(MatchOutcome.LOSS, result.winner)
        assertEquals(6_000, result.myCompetitionSteps)
        assertEquals(8_000, result.opponentCompetitionSteps)
    }

    @Test fun competitionCapDecidesNinetyNineThousandAgainstOneHundredFiveThousand() {
        val result = com.lazyapps.steparena.feature.game.challengeResult(99_000, 105_000)
        assertEquals(99_000, result.myCompetitionSteps)
        assertEquals(100_000, result.opponentCompetitionSteps)
        assertEquals(MatchOutcome.LOSS, result.winner)
    }

    @Test fun competitionAndRewardStepsAreSeparate() {
        val result = com.lazyapps.steparena.feature.game.challengeResult(32_000, 38_000)
        assertEquals(32_000, result.myCompetitionSteps)
        assertEquals(38_000, result.opponentCompetitionSteps)
        assertEquals(30_000, result.myRewardSteps)
        assertEquals(30_000, result.opponentRewardSteps)
        assertEquals(MatchOutcome.LOSS, result.winner)
    }

    @Test fun rewardStepsAreCappedIndependently() {
        assertEquals(29_999, OfficialSteps.reward(29_999))
        assertEquals(30_000, OfficialSteps.reward(30_000))
        assertEquals(30_000, OfficialSteps.reward(30_001))
        assertEquals(30_000, OfficialSteps.reward(100_000))
    }

    @Test fun competitionClampMakesHundredAndTwentyThousandAStandoff() {
        val result = com.lazyapps.steparena.feature.game.challengeResult(100_000, 120_000)
        assertEquals(100_000, result.myCompetitionSteps)
        assertEquals(100_000, result.opponentCompetitionSteps)
        assertEquals(MatchOutcome.DRAW, result.winner)
    }

    @Test fun matchingUsesCommonDeterministicScoreForBotAndReal() {
        val player = candidate("player", MatchCandidateType.REAL, 5_000)
        val bot = candidate("bot", MatchCandidateType.BOT, 5_100)
        val real = candidate("real", MatchCandidateType.REAL, 50_000)
        assertTrue(MatchCandidateScoring.score(bot, player) < MatchCandidateScoring.score(real, player))
        assertEquals(listOf("bot", "real"), MatchCandidateScoring.rank(listOf(real, bot), player).map { it.id })
    }

    private fun candidate(id: String, type: MatchCandidateType, steps: Long) = MatchCandidate(
        id, type, RankTier.GOLD, 2, steps, 4, "Asia/Tokyo", 1L,
    )
}
