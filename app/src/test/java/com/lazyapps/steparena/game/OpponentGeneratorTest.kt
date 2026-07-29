package com.lazyapps.steparena.game

import java.time.LocalDate
import org.junit.Assert.*
import org.junit.Test

class OpponentGeneratorTest {
    private val generator = LocalOpponentGenerator()
    private val base = OpponentGenerationInput(
        "install-1", "2026-07", LocalDate.of(2026, 7, 29), RankSystem.definitions.first(),
    )

    @Test fun sameSeedProducesSameOpponent() = assertEquals(generator.generate(base), generator.generate(base))
    @Test fun anotherDateChangesOpponent() = assertNotEquals(
        generator.generate(base), generator.generate(base.copy(localDate = base.localDate.plusDays(1))),
    )
    @Test fun targetRespectsMinimumMaximumAndHistoryFallback() {
        assertTrue(generator.generate(base.copy(recentMedian = 0)).targetSteps >= 1_000)
        assertTrue(generator.generate(base.copy(recentMedian = Long.MAX_VALUE)).targetSteps <= 50_000)
        assertTrue(generator.generate(base).targetSteps in 1_000..50_000)
    }
    @Test fun beginnerAndLossRescueDoNotIncreaseDifficulty() {
        val regular = generator.generate(base).targetSteps
        assertTrue(generator.generate(base.copy(beginner = true)).targetSteps <= regular)
        assertTrue(generator.generate(base.copy(currentLossStreak = 5)).targetSteps <= regular)
    }
    @Test fun progressIsBoundedAndPersonalityAware() {
        val opponent = generator.generate(base)
        assertEquals(0, generator.progress(opponent, 0))
        assertEquals(opponent.targetSteps, generator.progress(opponent, 1440))
    }
}
