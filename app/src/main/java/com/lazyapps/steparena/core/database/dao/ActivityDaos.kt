package com.lazyapps.steparena.core.database.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import com.lazyapps.steparena.core.database.entity.ActivityProcessingStateEntity
import com.lazyapps.steparena.core.database.entity.DailyActivityRecordEntity
import com.lazyapps.steparena.core.database.entity.HourlyActivityRecordEntity
import com.lazyapps.steparena.core.database.entity.WalkingSessionEntity
import com.lazyapps.steparena.core.database.entity.TrackingGapRecordEntity
import com.lazyapps.steparena.core.database.entity.ProcessedExternalStepRecordEntity
import com.lazyapps.steparena.recovery.TrackingGapStatus
import kotlinx.coroutines.flow.Flow

@Dao
interface HourlyActivityDao {
    @Upsert suspend fun upsert(record: HourlyActivityRecordEntity)
    @Query("SELECT * FROM hourly_activity_records WHERE id = :id") suspend fun byId(id: String): HourlyActivityRecordEntity?
    @Query("SELECT * FROM hourly_activity_records WHERE localDate = :date AND zoneId = :zone ORDER BY periodStartEpochMillis")
    suspend fun forDate(date: String, zone: String): List<HourlyActivityRecordEntity>
    @Query("SELECT * FROM hourly_activity_records WHERE localDate = :date AND zoneId = :zone ORDER BY periodStartEpochMillis")
    fun observeDate(date: String, zone: String): Flow<List<HourlyActivityRecordEntity>>
}

@Dao
interface DailyActivityDao {
    @Upsert suspend fun upsert(record: DailyActivityRecordEntity)
    @Query("SELECT * FROM daily_activity_records WHERE localDate = :date AND zoneId = :zone LIMIT 1")
    suspend fun get(date: String, zone: String): DailyActivityRecordEntity?
    @Query("SELECT * FROM daily_activity_records WHERE localDate = :date AND zoneId = :zone LIMIT 1")
    fun observeDate(date: String, zone: String): Flow<DailyActivityRecordEntity?>
    @Query("SELECT * FROM daily_activity_records ORDER BY localDate DESC LIMIT :limit")
    fun recent(limit: Int): Flow<List<DailyActivityRecordEntity>>
    @Query("SELECT * FROM daily_activity_records ORDER BY localDate DESC LIMIT :limit")
    suspend fun recentNow(limit: Int): List<DailyActivityRecordEntity>
    @Query("DELETE FROM daily_activity_records WHERE id LIKE 'debug-daily-%'")
    suspend fun deleteDebugRecords()
}

@Dao
interface WalkingSessionDao {
    @Upsert suspend fun upsert(record: WalkingSessionEntity)
    @Query("SELECT * FROM walking_sessions WHERE id = :id") suspend fun get(id: String): WalkingSessionEntity?
    @Query("SELECT * FROM walking_sessions ORDER BY startedAtEpochMillis DESC")
    fun observeAll(): Flow<List<WalkingSessionEntity>>
    @Query("SELECT COUNT(*) FROM walking_sessions WHERE localDate = :date AND status != 'DISCARDED'")
    suspend fun countForDate(date: String): Int
    @Query("SELECT * FROM walking_sessions WHERE status IN ('ACTIVE','PAUSED') AND isManual = :manual ORDER BY startedAtEpochMillis DESC LIMIT 1")
    suspend fun active(manual: Boolean): WalkingSessionEntity?
    @Query("SELECT * FROM walking_sessions WHERE status IN ('ACTIVE','PAUSED') AND isManual = 1 ORDER BY startedAtEpochMillis DESC LIMIT 1")
    fun observeActiveManual(): Flow<WalkingSessionEntity?>
    @Query("SELECT * FROM walking_sessions WHERE status IN ('ACTIVE','PAUSED')")
    suspend fun activeSessions(): List<WalkingSessionEntity>
}

@Dao
interface ActivityProcessingStateDao {
    @Query("SELECT * FROM activity_processing_state WHERE `key` = 'sensor'")
    suspend fun get(): ActivityProcessingStateEntity?
    @Upsert suspend fun upsert(state: ActivityProcessingStateEntity)
}

@Dao
interface TrackingGapDao {
    @Upsert suspend fun upsert(record: TrackingGapRecordEntity)
    @Query("SELECT * FROM tracking_gap_records WHERE id = :id") suspend fun get(id: String): TrackingGapRecordEntity?
    @Query("SELECT * FROM tracking_gap_records WHERE fingerprint = :fingerprint LIMIT 1")
    suspend fun byFingerprint(fingerprint: String): TrackingGapRecordEntity?
    @Query("SELECT * FROM tracking_gap_records ORDER BY startedAtEpochMillis DESC")
    fun observeHistory(): Flow<List<TrackingGapRecordEntity>>
    @Query("SELECT * FROM tracking_gap_records WHERE status IN ('DETECTED','RECOVERY_PENDING','PARTIALLY_RECOVERED','UNRESOLVED','USER_REVIEW_REQUIRED') ORDER BY startedAtEpochMillis")
    suspend fun unresolved(): List<TrackingGapRecordEntity>
    @Query("SELECT COUNT(*) FROM tracking_gap_records WHERE status IN ('DETECTED','RECOVERY_PENDING','PARTIALLY_RECOVERED','UNRESOLVED','USER_REVIEW_REQUIRED')")
    fun observeUnresolvedCount(): Flow<Int>
    @Query("UPDATE tracking_gap_records SET status = :status, reviewedAtEpochMillis = :reviewedAt, updatedAtEpochMillis = :reviewedAt WHERE id = :id")
    suspend fun updateStatus(id: String, status: TrackingGapStatus, reviewedAt: Long)
}

@Dao
interface ProcessedExternalStepRecordDao {
    @Upsert suspend fun upsert(record: ProcessedExternalStepRecordEntity)
    @Query("SELECT * FROM processed_external_step_records WHERE fingerprint = :fingerprint LIMIT 1")
    suspend fun byFingerprint(fingerprint: String): ProcessedExternalStepRecordEntity?
    @Query("SELECT * FROM processed_external_step_records WHERE recordId = :recordId AND dataOriginPackage = :origin LIMIT 1")
    suspend fun byRecordId(recordId: String, origin: String): ProcessedExternalStepRecordEntity?
    @Query("SELECT COALESCE(SUM(appliedSteps), 0) FROM processed_external_step_records WHERE startedAtEpochMillis < :end AND endedAtEpochMillis > :start")
    suspend fun appliedInRange(start: Long, end: Long): Long
}
