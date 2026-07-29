package com.lazyapps.steparena.core.database.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.lazyapps.steparena.core.database.model.DataQuality
import com.lazyapps.steparena.recovery.RecoverySource
import com.lazyapps.steparena.recovery.TrackingGapReason
import com.lazyapps.steparena.recovery.TrackingGapStatus

@Entity(
    tableName = "tracking_gap_records",
    indices = [
        Index("startedAtEpochMillis"),
        Index("endedAtEpochMillis"),
        Index(value = ["fingerprint"], unique = true),
    ],
)
data class TrackingGapRecordEntity(
    @PrimaryKey val id: String,
    val startedAtEpochMillis: Long,
    val endedAtEpochMillis: Long,
    val zoneId: String,
    val reason: TrackingGapReason,
    val status: TrackingGapStatus,
    val expectedTracking: Boolean,
    val explicitUserStop: Boolean,
    val recoveredSteps: Long,
    val unresolvedSteps: Long,
    val recoverySource: RecoverySource?,
    val quality: DataQuality,
    val externalRecordCount: Int,
    val externalOriginsJson: String?,
    val fingerprint: String,
    val detectedAtEpochMillis: Long,
    val recoveredAtEpochMillis: Long?,
    val reviewedAtEpochMillis: Long?,
    val createdAtEpochMillis: Long,
    val updatedAtEpochMillis: Long,
)

@Entity(
    tableName = "processed_external_step_records",
    indices = [
        Index(value = ["fingerprint"], unique = true),
        Index(value = ["recordId", "dataOriginPackage"], unique = true),
    ],
)
data class ProcessedExternalStepRecordEntity(
    @PrimaryKey val id: String,
    val recordId: String?,
    val dataOriginPackage: String,
    val startedAtEpochMillis: Long,
    val endedAtEpochMillis: Long,
    val steps: Long,
    val lastModifiedAtEpochMillis: Long?,
    val fingerprint: String,
    val processedAtEpochMillis: Long,
    val appliedSteps: Long,
    val gapId: String,
)
