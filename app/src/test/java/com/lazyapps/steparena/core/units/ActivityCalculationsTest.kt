package com.lazyapps.steparena.core.units

import org.junit.Assert.assertEquals
import org.junit.Test
import java.util.Locale

class ActivityCalculationsTest {
    @Test
    fun goalProgress_isClampedAndHandlesZeroGoal() {
        assertEquals(0.5f, ActivityCalculations.goalProgress(5_000, 10_000), 0.001f)
        assertEquals(1f, ActivityCalculations.goalProgress(12_000, 10_000), 0.001f)
        assertEquals(0f, ActivityCalculations.goalProgress(100, 0), 0.001f)
    }

    @Test
    fun remainingSteps_neverReturnsNegative() {
        assertEquals(2_500, ActivityCalculations.remainingSteps(7_500, 10_000))
        assertEquals(0, ActivityCalculations.remainingSteps(12_000, 10_000))
    }

    @Test
    fun rankProgress_usesCurrentAndRemainingPoints() {
        assertEquals(0.75f, ActivityCalculations.rankProgress(750, 250), 0.001f)
    }

    @Test
    fun negativeInputs_areNormalized() {
        assertEquals(0, ActivityCalculations.normalize(-10))
        assertEquals(0.0, ActivityCalculations.normalize(-2.5), 0.0)
        assertEquals(0, ActivityCalculations.remainingSteps(-100, -50))
    }

    @Test
    fun unitConversions_areAccurate() {
        assertEquals(1.0, ActivityCalculations.metersToMiles(1_609.344), 0.0001)
        assertEquals(2.2046, ActivityCalculations.kilogramsToPounds(1.0), 0.0001)
    }

    @Test
    fun distanceFormatter_supportsKilometersAndMiles() {
        assertEquals("1.61 km", ActivityFormatter.distance(1_609.344, DistanceUnit.KILOMETER, Locale.US))
        assertEquals("1.00 mi", ActivityFormatter.distance(1_609.344, DistanceUnit.MILE, Locale.US))
    }

    @Test
    fun speedFormatter_supportsMetricAndImperial() {
        assertEquals(
            "3.6 km/h",
            ActivityFormatter.speed(1.0, SpeedUnit.KILOMETERS_PER_HOUR, Locale.US),
        )
        assertEquals(
            "2.2 mph",
            ActivityFormatter.speed(1.0, SpeedUnit.MILES_PER_HOUR, Locale.US),
        )
    }

    @Test
    fun durationFormatter_usesHoursAndMinutes() {
        assertEquals("01:23", ActivityFormatter.duration(4_980))
        assertEquals("00:00", ActivityFormatter.duration(-1))
    }

    @Test
    fun estimatedValue_canBeRenderedWithResourceLevelLabel() {
        assertEquals("286 kcal", ActivityFormatter.calories(286.2, Locale.US))
    }
}
