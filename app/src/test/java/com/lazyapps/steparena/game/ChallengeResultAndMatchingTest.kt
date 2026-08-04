package com.lazyapps.steparena.game

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ChallengeResultAndMatchingTest {
    @Test fun competitionAndRewardStepsAreSeparate() {
        val result = com.lazyapps.steparena.feature.game.challengeResult(32_000, 38_000)
        assertEquals(32_000, result.myCompetitionSteps)
        assertEquals(38_000, result.opponentCompetitionSteps)
        assertEquals(30_000, result.myRewardSteps)
        assertEquals(30_000, result.opponentRewardSteps)
        assertEquals(MatchOutcome.LOSS, result.winner)
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
