package com.lazyapps.steparena.core.units

import java.util.Locale
import kotlin.math.max

enum class DistanceUnit { KILOMETER, MILE }
enum class SpeedUnit { KILOMETERS_PER_HOUR, MILES_PER_HOUR }
enum class WeightUnit { KILOGRAM, POUND }

object ActivityCalculations {
    fun normalize(value: Int): Int = max(0, value)
    fun normalize(value: Double): Double = max(0.0, value)

    fun goalProgress(steps: Int, goalSteps: Int): Float {
        val safeGoal = normalize(goalSteps)
        if (safeGoal == 0) return 0f
        return (normalize(steps).toFloat() / safeGoal).coerceIn(0f, 1f)
    }

    fun remainingSteps(steps: Int, goalSteps: Int): Int =
        (normalize(goalSteps) - normalize(steps)).coerceAtLeast(0)

    fun rankProgress(points: Int, pointsToNextRank: Int): Float {
        val current = normalize(points)
        val remaining = normalize(pointsToNextRank)
        val span = current + remaining
        return if (span == 0) 0f else (current.toFloat() / span).coerceIn(0f, 1f)
    }

    fun metersToMiles(meters: Double): Double = normalize(meters) / 1_609.344
    fun kilogramsToPounds(kilograms: Double): Double = normalize(kilograms) * 2.2046226218
}

object ActivityFormatter {
    fun distance(meters: Double, unit: DistanceUnit, locale: Locale): String =
        when (unit) {
            DistanceUnit.KILOMETER -> String.format(locale, "%.2f km", ActivityCalculations.normalize(meters) / 1_000)
            DistanceUnit.MILE -> String.format(locale, "%.2f mi", ActivityCalculations.metersToMiles(meters))
        }

    fun speed(metersPerSecond: Double, unit: SpeedUnit, locale: Locale): String {
        val kilometersPerHour = ActivityCalculations.normalize(metersPerSecond) * 3.6
        return when (unit) {
            SpeedUnit.KILOMETERS_PER_HOUR -> String.format(locale, "%.1f km/h", kilometersPerHour)
            SpeedUnit.MILES_PER_HOUR -> String.format(locale, "%.1f mph", kilometersPerHour / 1.609344)
        }
    }

    fun duration(seconds: Long): String {
        val safeSeconds = seconds.coerceAtLeast(0)
        val hours = safeSeconds / 3_600
        val minutes = (safeSeconds % 3_600) / 60
        return "%02d:%02d".format(Locale.ROOT, hours, minutes)
    }

    fun calories(kcal: Double, locale: Locale): String =
        String.format(locale, "%.0f kcal", ActivityCalculations.normalize(kcal))
}
