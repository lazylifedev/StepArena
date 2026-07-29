package com.lazyapps.steparena.game

import org.junit.Assert.*
import org.junit.Test

class RatingAndMatchTest {
    private val calculator = DefaultRatingCalculator()
    @Test fun outcomesIncludeDrawAndNoContest() {
        assertEquals(MatchOutcome.WIN, outcome(2, 1))
        assertEquals(MatchOutcome.LOSS, outcome(1, 2))
        assertEquals(MatchOutcome.DRAW, outcome(2, 2))
        assertEquals(MatchOutcome.NO_CONTEST, outcome(0, 0))
        assertEquals(MatchOutcome.NO_CONTEST, outcome(3, 2, blocked = true))
    }
    @Test fun winLossBeginnerRankAndStreakRules() {
        assertEquals(25, calculator.calculate(input(MatchOutcome.WIN)).base)
        assertEquals(-20, calculator.calculate(input(MatchOutcome.LOSS)).finalDelta)
        assertEquals(0, calculator.calculate(input(MatchOutcome.LOSS).copy(beginner = true)).finalDelta)
        assertTrue(calculator.calculate(input(MatchOutcome.WIN).copy(opponentRankIndex = 3)).rankDifferenceBonus > 0)
        assertEquals(10, calculator.calculate(input(MatchOutcome.WIN).copy(currentWinStreak = 9)).streakBonus)
    }
    @Test fun integrityCancelsRatingAndRestrictionCapsGain() {
        assertEquals(0, calculator.calculate(input(MatchOutcome.WIN).copy(integrityViolation = true)).finalDelta)
        assertTrue(calculator.calculate(input(MatchOutcome.WIN).copy(restrictedRatio = .8)).finalDelta <= 15)
    }
    @Test fun rankTransitionCannotSkipMultipleDivisions() {
        val after = RankSystem.clampTransition(1_000, 9_999)
        assertEquals(RankSystem.definitions[1], RankSystem.definition(after))
    }
    private fun input(result: MatchOutcome) = RatingCalculationInput(result, 0, 0, 0, false, false, 0.0)
}
