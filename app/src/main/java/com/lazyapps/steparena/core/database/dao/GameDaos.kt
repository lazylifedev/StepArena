package com.lazyapps.steparena.core.database.dao

import androidx.room.*
import com.lazyapps.steparena.core.database.entity.*
import com.lazyapps.steparena.game.MatchStatus
import kotlinx.coroutines.flow.Flow

@Dao
interface GamePlayerProfileDao {
    @Upsert suspend fun upsert(value: GamePlayerProfileEntity)
    @Query("SELECT * FROM game_player_profile WHERE id = 'local_player'") fun observe(): Flow<GamePlayerProfileEntity?>
    @Query("SELECT * FROM game_player_profile WHERE id = 'local_player'") suspend fun get(): GamePlayerProfileEntity?
}

@Dao
interface DailyMatchDao {
    @Insert(onConflict = OnConflictStrategy.IGNORE) suspend fun insert(value: DailyMatchEntity): Long
    @Update suspend fun update(value: DailyMatchEntity)
    @Query("SELECT * FROM daily_matches WHERE localDate = :date AND zoneId = :zone AND matchType = 'DAILY' LIMIT 1")
    fun observe(date: String, zone: String): Flow<DailyMatchEntity?>
    @Query("SELECT * FROM daily_matches ORDER BY localDate DESC LIMIT :limit") fun recent(limit: Int): Flow<List<DailyMatchEntity>>
    @Query("SELECT * FROM daily_matches WHERE status = :status AND localDate < :before ORDER BY localDate")
    suspend fun pending(status: MatchStatus = MatchStatus.ACTIVE, before: String): List<DailyMatchEntity>
    @Query("SELECT * FROM daily_matches WHERE id = :id") suspend fun get(id: String): DailyMatchEntity?
    @Query("SELECT * FROM daily_matches ORDER BY localDate DESC LIMIT :limit") suspend fun recentNow(limit: Int): List<DailyMatchEntity>
}

@Dao interface WeeklyLeagueDao {
    @Upsert suspend fun upsert(value: WeeklyLeagueEntity)
    @Query("SELECT * FROM weekly_leagues ORDER BY weekStartLocalDate DESC LIMIT 1") fun observeCurrent(): Flow<WeeklyLeagueEntity?>
}
@Dao interface GameSeasonDao {
    @Upsert suspend fun upsert(value: GameSeasonEntity)
    @Query("SELECT * FROM game_seasons WHERE id = :id") suspend fun get(id: String): GameSeasonEntity?
    @Query("SELECT * FROM game_seasons ORDER BY id DESC LIMIT 1") fun observeCurrent(): Flow<GameSeasonEntity?>
}
@Dao interface AchievementUnlockDao {
    @Insert(onConflict = OnConflictStrategy.IGNORE) suspend fun insert(value: AchievementUnlockEntity): Long
    @Query("SELECT * FROM achievement_unlocks ORDER BY unlockedAtEpochMillis DESC") fun observeAll(): Flow<List<AchievementUnlockEntity>>
}
