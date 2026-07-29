package com.lazyapps.steparena.core.database.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import com.lazyapps.steparena.core.database.entity.ActivityProcessingStateEntity
import com.lazyapps.steparena.core.database.entity.DailyActivityRecordEntity
import com.lazyapps.steparena.core.database.entity.HourlyActivityRecordEntity
import com.lazyapps.steparena.core.database.entity.WalkingSessionEntity
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
}

@Dao
interface ActivityProcessingStateDao {
    @Query("SELECT * FROM activity_processing_state WHERE `key` = 'sensor'")
    suspend fun get(): ActivityProcessingStateEntity?
    @Upsert suspend fun upsert(state: ActivityProcessingStateEntity)
}
