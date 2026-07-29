package com.lazyapps.steparena.activity

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.Instant

class WalkingDurationCalculatorTest {
    private val calculator = WalkingDurationCalculator(60)
    private val start = Instant.parse("2026-07-29T00:00:00Z")

    @Test fun firstStepIsZero() = assertEquals(0, calculator.fromDetectorEvents(listOf(start)))
    @Test fun continuousIntervalsAreAccumulated() = assertEquals(
        33,
        calculator.fromDetectorEvents(listOf(start, start.plusSeconds(1), start.plusSeconds(3), start.plusSeconds(33))),
    )
    @Test fun sixtySecondBoundaryCounts() = assertEquals(
        60, calculator.fromDetectorEvents(listOf(start, start.plusSeconds(60))),
    )
    @Test fun sixtyOneSecondGapDoesNotCount() = assertEquals(
        0, calculator.fromDetectorEvents(listOf(start, start.plusSeconds(61))),
    )
    @Test fun tenMinuteGapDoesNotCount() = assertEquals(
        0, calculator.fromCounterEvents(start, start.plusSeconds(600)),
    )
    @Test fun counterOnlyShortGapIsEstimatedDuration() = assertEquals(
        12, calculator.fromCounterEvents(start, start.plusSeconds(12)),
    )
}
