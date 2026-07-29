package com.lazyapps.steparena.activity

import com.lazyapps.steparena.core.database.model.DataQuality

data class UserBodyProfile(
    val heightCm: Double? = null,
    val weightKg: Double? = null,
    val manualStepLengthMeters: Double? = null,
    val useAutomaticStepLength: Boolean = true,
)

data class StepLengthEstimate(
    val meters: Double,
    val source: String,
    val quality: DataQuality = DataQuality.ESTIMATED,
)

fun interface StepLengthEstimator {
    fun estimate(profile: UserBodyProfile): StepLengthEstimate
}

class DefaultStepLengthEstimator : StepLengthEstimator {
    override fun estimate(profile: UserBodyProfile): StepLengthEstimate {
        val manual = profile.manualStepLengthMeters?.valid(0.2, 2.0)
        if (manual != null) return StepLengthEstimate(manual, "manual")
        val height = profile.heightCm?.valid(100.0, 250.0)
        if (profile.useAutomaticStepLength && height != null) {
            return StepLengthEstimate(height * 0.415 / 100.0, "height")
        }
        return StepLengthEstimate(DEFAULT_STEP_LENGTH_METERS, "app_default")
    }

    private fun Double.valid(min: Double, max: Double): Double? =
        takeIf { it.isFinite() && it in min..max }

    companion object { const val DEFAULT_STEP_LENGTH_METERS = 0.70 }
}

data class CalorieEstimate(
    val kcal: Double,
    val usedDefaultWeight: Boolean,
    val quality: DataQuality = DataQuality.ESTIMATED,
)

fun interface CalorieEstimator {
    fun estimate(
        weightKg: Double?,
        distanceMeters: Double?,
        activeDurationSeconds: Long?,
        averageSpeedKmh: Double?,
    ): CalorieEstimate?
}

class DistanceCalorieEstimator : CalorieEstimator {
    override fun estimate(
        weightKg: Double?,
        distanceMeters: Double?,
        activeDurationSeconds: Long?,
        averageSpeedKmh: Double?,
    ): CalorieEstimate? {
        val distanceKm = distanceMeters?.takeIf { it.isFinite() && it >= 0 }?.div(1_000) ?: return null
        val weight = weightKg?.takeIf { it.isFinite() && it in 25.0..300.0 } ?: DEFAULT_WEIGHT_KG
        val kcal = distanceKm * weight * KCAL_PER_KG_KM
        if (!kcal.isFinite() || kcal < 0) return null
        return CalorieEstimate(kcal.coerceAtMost(MAX_KCAL), weightKg == null)
    }

    companion object {
        const val DEFAULT_WEIGHT_KG = 60.0
        const val KCAL_PER_KG_KM = 0.5
        const val MAX_KCAL = 10_000.0
    }
}

object WalkingSpeedCalculator {
    fun movingKmh(distanceMeters: Double?, durationSeconds: Long?): Double? {
        if (distanceMeters == null || !distanceMeters.isFinite() || distanceMeters < 0) return null
        if (durationSeconds == null || durationSeconds < 60) return null
        return (distanceMeters / 1_000.0 / (durationSeconds / 3_600.0))
            .takeIf { it.isFinite() && it in 0.0..25.0 }
    }
}

data class WalkingDetectionPolicy(
    val activeGapThresholdSeconds: Long = 60,
    val sessionEndGapSeconds: Long = 5 * 60,
    val minimumSessionSteps: Long = 10,
    val minimumSessionDurationSeconds: Long = 60,
)
