package com.lazyapps.steparena.feature.game

import com.lazyapps.steparena.core.database.entity.DailyMatchEntity
import com.lazyapps.steparena.game.*
import org.junit.Assert.assertEquals
import org.junit.Test

class CurrentChallengeStepsTest {
    @Test fun activeMatchUsesTrackingSourceOfTruthInsteadOfLegacySnapshot() {
        val result = currentChallengeSteps(match(MatchStatus.ACTIVE, 8_081), 5_864)
        assertEquals(5_864, result.displayedUserSteps)
        assertEquals(5_864, result.eligibleSteps)
        assertEquals(false, result.isFinalized)
    }

    @Test fun finalizedMatchKeepsFinalizedSnapshot() {
        val result = currentChallengeSteps(match(MatchStatus.FINALIZED, 8_120), 9_000)
        assertEquals(8_120, result.displayedUserSteps)
        assertEquals(8_120, result.eligibleSteps)
        assertEquals(true, result.isFinalized)
    }

    private fun match(status: MatchStatus, steps: Long) = DailyMatchEntity(
        id = "today", localDate = "2026-07-30", zoneId = "Asia/Tokyo", seasonId = "2026-07",
        matchType = MatchType.DAILY, status = status, outcome = null, opponentId = "opponent",
        opponentName = "Aoi", opponentAvatarKey = "aoi", opponentRankTier = RankTier.BRONZE,
        opponentRankDivision = 3, opponentPersonality = OpponentPersonality.STEADY,
        opponentTargetSteps = 10_000, totalUserSteps = steps, eligibleUserSteps = steps,
        restrictedUserSteps = 0, excludedUserSteps = 0, restrictionReasons = "",
        competitiveQuality = CompetitiveStepQuality.FULL, ratingBefore = 1_000,
        ratingDelta = null, ratingAfter = null, ratingBreakdown = null,
        finalizedAtEpochMillis = null, createdAtEpochMillis = 0, updatedAtEpochMillis = 0,
    )
}
