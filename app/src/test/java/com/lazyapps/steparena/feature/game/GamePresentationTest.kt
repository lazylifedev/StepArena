package com.lazyapps.steparena.feature.game

import com.lazyapps.steparena.game.MatchOutcome
import com.lazyapps.steparena.game.SeasonStatus
import com.lazyapps.steparena.R
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Test

class GamePresentationTest {
    @Test fun outcomesUseWalkingChallengeLanguage() {
        assertEquals(R.string.game_outcome_win, MatchOutcome.WIN.displayNameRes())
        assertEquals(R.string.game_outcome_loss, MatchOutcome.LOSS.displayNameRes())
        assertEquals(R.string.game_outcome_draw, MatchOutcome.DRAW.displayNameRes())
        assertEquals(R.string.game_outcome_no_contest, MatchOutcome.NO_CONTEST.displayNameRes())
    }

    @Test fun seasonStatusDoesNotExposeInternalEnum() {
        assertEquals(R.string.game_season_active, SeasonStatus.ACTIVE.displayNameRes())
        assertEquals(R.string.game_season_finalized, SeasonStatus.FINALIZED.displayNameRes())
    }

    @Test fun unknownPartnerNameIsDeterministicAndNeverLeaksInternalName() {
        val first = participantDisplayNameRes("UnknownEnglishPartner")
        assertEquals(first, participantDisplayNameRes("UnknownEnglishPartner"))
        assertFalse(first == 0)
        assertEquals(R.string.game_you, participantDisplayNameRes("You"))
    }

    @Test fun nextRankProgressAndHighestRankAreReported() {
        val next = nextRankProgress(1_100)!!
        assertEquals(100, next.remaining)
        assertEquals(0.5f, next.progress)
        assertNull(nextRankProgress(9_999))
    }
}
