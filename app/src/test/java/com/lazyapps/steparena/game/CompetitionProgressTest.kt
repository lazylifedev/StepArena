package com.lazyapps.steparena.game

import org.junit.Assert.assertEquals
import org.junit.Test

class CompetitionProgressTest {
    @Test
    fun tenThousandStepBandsKeepExactBoundariesFilled() {
        val cases = listOf(
            Case(0, 0, 0f, 0),
            Case(1, 0, 0.0001f, 0),
            Case(9_999, 0, 0.9999f, 0),
            Case(10_000, 0, 1f, 0),
            Case(10_001, 1, 0.0001f, 1),
            Case(15_000, 1, 0.5f, 1),
            Case(19_999, 1, 0.9999f, 1),
            Case(20_000, 1, 1f, 1),
            Case(20_001, 2, 0.0001f, 2),
            Case(29_999, 2, 0.9999f, 2),
            Case(30_000, 2, 1f, 2),
            Case(99_999, 9, 0.9999f, 9),
            Case(100_000, 9, 1f, 9),
            Case(100_001, 9, 1f, 9),
        )

        cases.forEach { expected ->
            val actual = competitionProgress(expected.steps)
            assertEquals(expected.steps.coerceIn(0, 100_000), actual.displaySteps)
            assertEquals(expected.band, actual.currentBand)
            assertEquals(expected.completedBands, actual.completedBands)
            assertEquals(expected.progress, actual.currentBandProgress, 0.00001f)
        }
    }

    private data class Case(
        val steps: Long,
        val band: Int,
        val progress: Float,
        val completedBands: Int,
    )
}
