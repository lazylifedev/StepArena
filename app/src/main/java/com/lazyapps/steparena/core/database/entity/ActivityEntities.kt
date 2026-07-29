package com.lazyapps.steparena.core.database.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.lazyapps.steparena.core.database.model.DataQuality
import com.lazyapps.steparena.core.database.model.WalkingSessionStatus
import com.lazyapps.steparena.core.database.model.WalkingSessionType

@Entity(
    tableName = "hourly_activity_records",
    indices = [Index(
        value = ["localDate", "hourOfDay", "zoneId", "utcOffsetSeconds"],
        unique = true,
    )],
)
data class HourlyActivityRecordEntity(
    @PrimaryKey val id: String,
    val localDate: String,
    val hourOfDay: Int,
    val zoneId: String,
    val utcOffsetSeconds: Int,
    val periodStartEpochMillis: Long,
    val periodEndEpochMillis: Long,
    val steps: Long,
    val distanceMeters: Double?,
    val walkingDurationSeconds: Long?,
    val estimatedCaloriesKcal: Double?,
    val averageWalkingSpeedKmh: Double?,
    val stepsQuality: DataQuality,
    val distanceQuality: DataQuality,
    val durationQuality: DataQuality,
    val caloriesQuality: DataQuality,
    val speedQuality: DataQuality,
    val firstActivityAtEpochMillis: Long?,
    val lastActivityAtEpochMillis: Long?,
    val sensorEventCount: Int,
    val recoveredSteps: Long,
    val estimatedSteps: Long,
    val createdAtEpochMillis: Long,
    val updatedAtEpochMillis: Long,
)

@Entity(
    tableName = "daily_activity_records",
    indices = [Index(value = ["localDate", "zoneId"], unique = true)],
)
data class DailyActivityRecordEntity(
    @PrimaryKey val id: String,
    val localDate: String,
    val zoneId: String,
    val steps: Long,
    val unclassifiedSteps: Long,
    val distanceMeters: Double?,
    val walkingDurationSeconds: Long?,
    val estimatedCaloriesKcal: Double?,
    val averageWalkingSpeedKmh: Double?,
    val stepsQuality: DataQuality,
    val distanceQuality: DataQuality,
    val durationQuality: DataQuality,
    val caloriesQuality: DataQuality,
    val speedQuality: DataQuality,
    val activeHourCount: Int,
    val walkingSessionCount: Int,
    val finalized: Boolean,
    val finalizedAtEpochMillis: Long?,
    val createdAtEpochMillis: Long,
    val updatedAtEpochMillis: Long,
)

@Entity(
    tableName = "walking_sessions",
    indices = [Index("startedAtEpochMillis"), Index("localDate")],
)
data class WalkingSessionEntity(
    @PrimaryKey val id: String,
    val localDate: String,
    val zoneId: String,
    val startedAtEpochMillis: Long,
    val endedAtEpochMillis: Long?,
    val steps: Long,
    val distanceMeters: Double?,
    val activeDurationSeconds: Long,
    val elapsedDurationSeconds: Long,
    val pausedDurationSeconds: Long,
    val estimatedCaloriesKcal: Double?,
    val averageMovingSpeedKmh: Double?,
    val averageElapsedSpeedKmh: Double?,
    val sessionType: WalkingSessionType,
    val status: WalkingSessionStatus,
    val stepsQuality: DataQuality,
    val distanceQuality: DataQuality,
    val durationQuality: DataQuality,
    val caloriesQuality: DataQuality,
    val speedQuality: DataQuality,
    val trackingServiceSessionId: String?,
    val createdAtEpochMillis: Long,
    val updatedAtEpochMillis: Long,
)

@Entity(tableName = "activity_processing_state")
data class ActivityProcessingStateEntity(
    @PrimaryKey val key: String = "sensor",
    val lastCounterValue: Long?,
    val lastEventEpochMillis: Long?,
    val lastZoneId: String?,
    val lastBootSessionId: String?,
    val updatedAtEpochMillis: Long,
)
