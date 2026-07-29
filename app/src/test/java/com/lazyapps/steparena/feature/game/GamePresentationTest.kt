package com.lazyapps.steparena.feature.game

import com.lazyapps.steparena.game.MatchOutcome
import com.lazyapps.steparena.game.SeasonStatus
import org.junit.Assert.assertEquals
import org.junit.Test

class GamePresentationTest {
    @Test fun outcomesUseWalkingChallengeLanguage() {
        assertEquals("目標達成", MatchOutcome.WIN.displayName())
        assertEquals("あと一歩", MatchOutcome.LOSS.displayName())
        assertEquals("同じ歩数", MatchOutcome.DRAW.displayName())
        assertEquals("判定対象外", MatchOutcome.NO_CONTEST.displayName())
    }

    @Test fun seasonStatusDoesNotExposeInternalEnum() {
        assertEquals("集計中", SeasonStatus.ACTIVE.displayName())
        assertEquals("集計済み", SeasonStatus.FINALIZED.displayName())
    }
}
