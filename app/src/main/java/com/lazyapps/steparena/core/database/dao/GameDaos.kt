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
    @Query("DELETE FROM game_player_profile") suspend fun deleteAll()
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
    @Query("SELECT * FROM daily_matches WHERE localDate = :date AND zoneId = :zone AND matchType = 'DAILY' LIMIT 1")
    suspend fun getForDate(date: String, zone: String): DailyMatchEntity?
    @Query("SELECT * FROM daily_matches ORDER BY localDate DESC LIMIT :limit") suspend fun recentNow(limit: Int): List<DailyMatchEntity>
    @Query("SELECT * FROM daily_matches WHERE localDate BETWEEN :start AND :end ORDER BY localDate")
    suspend fun inRange(start: String, end: String): List<DailyMatchEntity>
    @Query("DELETE FROM daily_matches WHERE id LIKE 'debug-%' OR opponentId LIKE 'debug-%'")
    suspend fun deleteDebugMatches()
    @Query("DELETE FROM daily_matches WHERE zoneId = :zone")
    suspend fun deleteForZone(zone: String)
    @Query("SELECT COUNT(*) FROM daily_matches") suspend fun count(): Int
    @Query("DELETE FROM daily_matches") suspend fun deleteAll()
}

@Dao interface WeeklyLeagueDao {
    @Upsert suspend fun upsert(value: WeeklyLeagueEntity)
    @Query("SELECT * FROM weekly_leagues ORDER BY weekStartLocalDate DESC LIMIT 1") fun observeCurrent(): Flow<WeeklyLeagueEntity?>
    @Query("SELECT * FROM weekly_leagues WHERE id = :id") suspend fun get(id: String): WeeklyLeagueEntity?
    @Query("SELECT * FROM weekly_leagues WHERE status = 'ACTIVE' AND weekEndLocalDate < :today")
    suspend fun expired(today: String): List<WeeklyLeagueEntity>
    @Query("SELECT COUNT(*) FROM weekly_leagues") suspend fun count(): Int
    @Query("DELETE FROM weekly_leagues") suspend fun deleteAll()
}
@Dao interface WeeklyLeagueParticipantDao {
    @Upsert suspend fun upsertAll(values: List<WeeklyLeagueParticipantEntity>)
    @Query("SELECT * FROM weekly_league_participants WHERE leagueId = :leagueId ORDER BY rank")
    fun observeForLeague(leagueId: String): Flow<List<WeeklyLeagueParticipantEntity>>
    @Query("SELECT * FROM weekly_league_participants WHERE leagueId = :leagueId ORDER BY rank")
    suspend fun getForLeague(leagueId: String): List<WeeklyLeagueParticipantEntity>
    @Query("DELETE FROM weekly_league_participants WHERE leagueId = :leagueId")
    suspend fun deleteForLeague(leagueId: String)
    @Query("UPDATE weekly_league_participants SET displayName = :displayName, updatedAtEpochMillis = :updatedAt WHERE isLocalPlayer = 1")
    suspend fun updateLocalDisplayName(displayName: String, updatedAt: Long)
    @Query("DELETE FROM weekly_league_participants") suspend fun deleteAll()
}
@Dao interface GameSeasonDao {
    @Upsert suspend fun upsert(value: GameSeasonEntity)
    @Query("SELECT * FROM game_seasons WHERE id = :id") suspend fun get(id: String): GameSeasonEntity?
    @Query("SELECT * FROM game_seasons ORDER BY id DESC LIMIT 1") fun observeCurrent(): Flow<GameSeasonEntity?>
    @Query("SELECT * FROM game_seasons WHERE status = 'ACTIVE' AND endedAtEpochMillis < :now")
    suspend fun expired(now: Long): List<GameSeasonEntity>
    @Query("SELECT * FROM game_seasons ORDER BY id DESC") fun observeAll(): Flow<List<GameSeasonEntity>>
    @Query("SELECT COUNT(*) FROM game_seasons") suspend fun count(): Int
    @Query("DELETE FROM game_seasons") suspend fun deleteAll()
}
@Dao interface AchievementUnlockDao {
    @Insert(onConflict = OnConflictStrategy.IGNORE) suspend fun insert(value: AchievementUnlockEntity): Long
    @Query("SELECT * FROM achievement_unlocks ORDER BY unlockedAtEpochMillis DESC") fun observeAll(): Flow<List<AchievementUnlockEntity>>
    @Query("SELECT * FROM achievement_unlocks WHERE achievementId = :id") suspend fun get(id: String): AchievementUnlockEntity?
    @Query("UPDATE achievement_unlocks SET acknowledged = 1 WHERE achievementId = :id") suspend fun acknowledge(id: String)
    @Query("DELETE FROM achievement_unlocks WHERE achievementId LIKE 'debug-%'") suspend fun deleteDebug()
    @Query("SELECT COUNT(*) FROM achievement_unlocks") suspend fun count(): Int
    @Query("DELETE FROM achievement_unlocks") suspend fun deleteAll()
}

@Dao
interface GameNotificationEventDao {
    @Insert(onConflict = OnConflictStrategy.IGNORE) suspend fun insert(value: GameNotificationEventEntity): Long
    @Query("SELECT * FROM game_notification_events WHERE deliveredAtEpochMillis IS NULL AND notBeforeEpochMillis <= :now ORDER BY createdAtEpochMillis")
    suspend fun pending(now: Long): List<GameNotificationEventEntity>
    @Query("SELECT * FROM game_notification_events ORDER BY createdAtEpochMillis DESC")
    fun observeAll(): Flow<List<GameNotificationEventEntity>>
    @Query("UPDATE game_notification_events SET deliveredAtEpochMillis = :now WHERE id = :id AND deliveredAtEpochMillis IS NULL")
    suspend fun markDelivered(id: String, now: Long): Int
    @Query("UPDATE game_notification_events SET acknowledged = 1 WHERE id = :id")
    suspend fun acknowledge(id: String)
    @Query("SELECT * FROM game_notification_events WHERE deduplicationKey = :key")
    suspend fun byKey(key: String): GameNotificationEventEntity?
    @Query("DELETE FROM game_notification_events WHERE sourceId LIKE 'debug-%'")
    suspend fun deleteDebugEvents()
    @Query("SELECT COUNT(*) FROM game_notification_events") suspend fun count(): Int
    @Query("DELETE FROM game_notification_events") suspend fun deleteAll()
}
