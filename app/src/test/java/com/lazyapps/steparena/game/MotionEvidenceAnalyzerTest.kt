package com.lazyapps.steparena.game

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MotionEvidenceAnalyzerTest {
    private val analyzer = MotionEvidenceAnalyzer()

    @Test fun insufficientSamplesAreUnknown() {
        val result = analyzer.analyze(listOf(MotionSample(0, 0f, 0f, 0f)), emptyList())
        assertEquals(MotionEvidenceAssessment.UNKNOWN, result.assessment)
    }

    @Test fun strongRepeatedRotationIsConfirmed() {
        val gyro = (0 until 60).map { i ->
            MotionSample(i * 50_000_000L, if (i % 2 == 0) 8f else -8f, 0f, 0f)
        }
        val acceleration = (0 until 60).map { i ->
            MotionSample(i * 50_000_000L, if (i % 2 == 0) 7f else -7f, 0f, 0f)
        }
        val result = analyzer.analyze(acceleration, gyro)
        assertEquals(MotionEvidenceAssessment.SHAKE_CONFIRMED, result.assessment)
        assertTrue(result.confidence > 0.9)
    }

    @Test fun gyroWithoutAccelerationCannotBeConfirmed() {
        val gyro = (0 until 60).map { i ->
            MotionSample(i * 50_000_000L, if (i % 2 == 0) 8f else -8f, 0f, 0f)
        }
        assertEquals(MotionEvidenceAssessment.SHAKE_SUSPECTED, analyzer.analyze(emptyList(), gyro).assessment)
    }

    @Test fun briefJostleCannotBeConfirmed() {
        val samples = (0 until 12).map { i -> MotionSample(i * 50_000_000L, if (i % 2 == 0) 9f else -9f, 0f, 0f) }
        assertTrue(analyzer.analyze(samples, samples).assessment != MotionEvidenceAssessment.SHAKE_CONFIRMED)
    }

    @Test fun sparseDeliveryIsUnknown() {
        val samples = (0 until 10).map { i -> MotionSample(i * 1_000_000_000L, 8f, 0f, 0f) }
        assertEquals(MotionEvidenceAssessment.UNKNOWN, analyzer.analyze(samples, samples).assessment)
    }

    @Test fun nonFiniteAndDuplicateSamplesAreIgnored() {
        val samples = listOf(MotionSample(1, Float.NaN, 0f, 0f), MotionSample(1, 1f, 1f, 1f))
        assertEquals(MotionEvidenceAssessment.UNKNOWN, analyzer.analyze(samples, samples).assessment)
    }

    @Test fun lowMotionIsNotConfirmed() {
        val samples = (0 until 20).map { i -> MotionSample(i * 100_000_000L, .2f, .1f, .1f) }
        val result = analyzer.analyze(emptyList(), samples)
        assertTrue(result.assessment != MotionEvidenceAssessment.SHAKE_CONFIRMED)
    }
}
