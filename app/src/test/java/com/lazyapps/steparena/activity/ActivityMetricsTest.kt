package com.lazyapps.steparena.activity

import com.lazyapps.steparena.core.database.model.DataQuality
import com.lazyapps.steparena.core.database.model.mergeQuality
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ActivityMetricsTest {
    private val stepLength = DefaultStepLengthEstimator()

    @Test
    fun `173 cm automatic stride uses the displayed formula value`() {
        val result = stepLength.estimate(UserBodyProfile(heightCm = 173.0))
        assertEquals(0.71795, result.meters, 0.000001)
        assertEquals("height", result.source)
    }
    private val calories = DistanceCalorieEstimator()

    @Test fun manualStepLengthWins() {
        val result = stepLength.estimate(UserBodyProfile(heightCm = 180.0, manualStepLengthMeters = 0.8))
        assertEquals(0.8, result.meters, 0.0)
        assertEquals("manual", result.source)
    }

    @Test fun heightEstimatesStepLength() {
        assertEquals(
            0.7055,
            stepLength.estimate(UserBodyProfile(heightCm = 170.0)).meters,
            0.0001,
        )
    }

    @Test fun defaultStepLengthIsExplicit() {
        val result = stepLength.estimate(UserBodyProfile())
        assertEquals(DefaultStepLengthEstimator.DEFAULT_STEP_LENGTH_METERS, result.meters, 0.0)
        assertEquals(DataQuality.ESTIMATED, result.quality)
    }

    @Test fun invalidHeightFallsBack() {
        assertEquals("app_default", stepLength.estimate(UserBodyProfile(heightCm = Double.NaN)).source)
    }

    @Test fun caloriesUseWeightAndDistance() {
        assertEquals(35.0, calories.estimate(70.0, 1_000.0, null, null)!!.kcal, 0.0)
    }

    @Test fun caloriesDeclareDefaultWeight() {
        assertTrue(calories.estimate(null, 1_000.0, null, null)!!.usedDefaultWeight)
    }

    @Test fun caloriesRejectNegativeDistance() {
        assertNull(calories.estimate(60.0, -1.0, null, null))
    }

    @Test fun caloriesRejectNaN() {
        assertNull(calories.estimate(60.0, Double.NaN, null, null))
    }

    @Test fun caloriesAreCapped() {
        assertEquals(
            DistanceCalorieEstimator.MAX_KCAL,
            calories.estimate(300.0, 1_000_000.0, null, null)!!.kcal,
            0.0,
        )
    }

    @Test fun speedRequiresOneMinute() {
        assertNull(WalkingSpeedCalculator.movingKmh(100.0, 59))
    }

    @Test fun speedIsCalculatedInKmh() {
        assertEquals(5.0, WalkingSpeedCalculator.movingKmh(5_000.0, 3_600)!!, 0.0)
    }

    @Test fun speedRejectsImpossibleValue() {
        assertNull(WalkingSpeedCalculator.movingKmh(10_000.0, 60))
    }

    @Test fun speedRejectsInfinity() {
        assertNull(WalkingSpeedCalculator.movingKmh(Double.POSITIVE_INFINITY, 60))
    }

    @Test fun qualityMergePreservesSingleQuality() {
        assertEquals(DataQuality.MEASURED, mergeQuality(listOf(DataQuality.MEASURED)))
    }

    @Test fun qualityMergeMarksMixedSources() {
        assertEquals(
            DataQuality.MIXED,
            mergeQuality(listOf(DataQuality.MEASURED, DataQuality.ESTIMATED)),
        )
    }

    @Test fun policyMatchesPhaseThreeDefaults() {
        val policy = WalkingDetectionPolicy()
        assertEquals(60, policy.activeGapThresholdSeconds)
        assertEquals(300, policy.sessionEndGapSeconds)
        assertTrue(policy.minimumSessionSteps > 0)
        assertFalse(policy.minimumSessionDurationSeconds <= 0)
    }
}
