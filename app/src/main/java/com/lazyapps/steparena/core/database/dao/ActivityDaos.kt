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
import com.lazyapps.steparena.core.database.entity.CompetitiveIntegritySegmentEntity
import com.lazyapps.steparena.recovery.TrackingGapStatus
import com.lazyapps.steparena.game.CompetitiveIntegrityAssessment
import kotlinx.coroutines.flow.Flow

@Dao
interface HourlyActivityDao {
    @Upsert suspend fun upsert(record: HourlyActivityRecordEntity)
    @Query("SELECT * FROM hourly_activity_records WHERE id = :id") suspend fun byId(id: String): HourlyActivityRecordEntity?
    @Query("SELECT * FROM hourly_activity_records WHERE localDate = :date AND zoneId = :zone ORDER BY periodStartEpochMillis")
    suspend fun forDate(date: String, zone: String): List<HourlyActivityRecordEntity>
    @Query("SELECT * FROM hourly_activity_records WHERE localDate = :date AND zoneId = :zone ORDER BY periodStartEpochMillis")
    fun observeDate(date: String, zone: String): Flow<List<HourlyActivityRecordEntity>>
    @Query("SELECT COUNT(*) FROM hourly_activity_records") suspend fun count(): Int
    @Query("SELECT * FROM hourly_activity_records ORDER BY periodStartEpochMillis") suspend fun all(): List<HourlyActivityRecordEntity>
    @Query("SELECT * FROM hourly_activity_records WHERE periodStartEpochMillis < :end AND periodEndEpochMillis > :start")
    suspend fun overlapping(start: Long, end: Long): List<HourlyActivityRecordEntity>
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
    @Query("SELECT COUNT(*) FROM daily_activity_records") suspend fun count(): Int
    @Query("SELECT * FROM daily_activity_records ORDER BY localDate") suspend fun all(): List<DailyActivityRecordEntity>
    @Query("SELECT MIN(localDate) FROM daily_activity_records") suspend fun oldestDate(): String?
    @Query("SELECT MAX(localDate) FROM daily_activity_records") suspend fun newestDate(): String?
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
    @Query("SELECT COUNT(*) FROM walking_sessions") suspend fun count(): Int
    @Query("SELECT * FROM walking_sessions ORDER BY startedAtEpochMillis") suspend fun all(): List<WalkingSessionEntity>
    @Query("SELECT * FROM walking_sessions WHERE status IN ('COMPLETED','RECOVERED') ORDER BY startedAtEpochMillis")
    suspend fun completedForBackup(): List<WalkingSessionEntity>
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
    @Query("SELECT COUNT(*) FROM tracking_gap_records") suspend fun count(): Int
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
    @Query("SELECT COUNT(*) FROM processed_external_step_records") suspend fun count(): Int
}

@Dao
interface CompetitiveIntegritySegmentDao {
    @Upsert suspend fun upsert(record: CompetitiveIntegritySegmentEntity)
    @Query("SELECT * FROM competitive_integrity_segments WHERE id = :id LIMIT 1")
    suspend fun byId(id: String): CompetitiveIntegritySegmentEntity?
    @Query("""UPDATE competitive_integrity_segments SET eligibleSteps = :eligible, restrictedSteps = :restricted,
        excludedSteps = :excluded, assessment = :assessment, reasons = :reasons, classifierVersion = :classifierVersion
        WHERE id = :id""")
    suspend fun updateClassification(id: String, eligible: Long, restricted: Long, excluded: Long,
        assessment: CompetitiveIntegrityAssessment, reasons: String, classifierVersion: Int): Int
    @Query("SELECT * FROM competitive_integrity_segments WHERE localDate = :date AND zoneId = :zone ORDER BY startedAtEpochMillis")
    suspend fun forDate(date: String, zone: String): List<CompetitiveIntegritySegmentEntity>
    @Query("SELECT * FROM competitive_integrity_segments WHERE localDate = :date AND zoneId = :zone ORDER BY startedAtEpochMillis")
    fun observeDate(date: String, zone: String): Flow<List<CompetitiveIntegritySegmentEntity>>
    @Query("SELECT COUNT(*) FROM competitive_integrity_segments") suspend fun count(): Int
    @Query("SELECT * FROM competitive_integrity_segments ORDER BY startedAtEpochMillis")
    suspend fun allForBackup(): List<CompetitiveIntegritySegmentEntity>
}
