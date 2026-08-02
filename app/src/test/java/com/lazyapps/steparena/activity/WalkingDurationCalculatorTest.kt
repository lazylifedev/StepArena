package com.lazyapps.steparena.activity

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import com.lazyapps.steparena.core.database.model.DataQuality
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

    @Test fun lowDetectorCoverageDoesNotRepresentAll1593StepsWith51Seconds() {
        val detectors = (0..50).map { start.plusSeconds(it.toLong()) }
        val result = calculator.calculate(1_593, detectors, start, start.plusSeconds(60))
        assertEquals(DataQuality.MIXED, result.quality)
        assertEquals(51.0 / 1_593.0, result.detectorCoverage, 0.0001)
        assertTrue(result.totalSeconds > 500)
        assertTrue(result.cadenceStepsPerMinute!! in 60.0..180.0)
        assertTrue(result.estimatedSeconds > 0)
    }

    @Test fun highDetectorCoverageUsesMeasuredTime() {
        val detectors = (0 until 98).map { start.plusMillis(it * 600L) }
        val result = calculator.calculate(100, detectors, start, start.plusSeconds(60))
        assertEquals(DataQuality.MEASURED, result.quality)
        assertTrue(result.totalSeconds in 57..60)
        assertEquals(0, result.estimatedSeconds)
    }

    @Test fun counterOnlyDurationIsEstimated() {
        val result = calculator.calculate(100, emptyList(), start, start.plusSeconds(60))
        assertEquals(DataQuality.ESTIMATED, result.quality)
        assertEquals(60, result.totalSeconds)
    }

    @Test fun longGapIsNeverMeasured() {
        val result = calculator.calculate(
            1_500, emptyList(), start, start.plusSeconds(7_200), recovered = true,
        )
        assertEquals(DataQuality.RECOVERED, result.quality)
        assertTrue(result.cadenceStepsPerMinute!! in 60.0..180.0)
    }
}
