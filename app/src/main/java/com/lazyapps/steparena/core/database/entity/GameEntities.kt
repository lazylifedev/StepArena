package com.lazyapps.steparena.core.database.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.lazyapps.steparena.game.*

@Entity(tableName = "game_player_profile")
data class GamePlayerProfileEntity(
    @PrimaryKey val id: String = "local_player",
    val displayName: String? = null,
    val rating: Int = 1_000,
    val rankTier: RankTier = RankTier.BRONZE,
    val rankDivision: Int? = 3,
    val totalMatches: Int = 0,
    val wins: Int = 0,
    val losses: Int = 0,
    val draws: Int = 0,
    val noContests: Int = 0,
    val currentWinStreak: Int = 0,
    val bestWinStreak: Int = 0,
    val currentLossStreak: Int = 0,
    val beginnerMatchesRemaining: Int = 5,
    val lastOutcome: MatchOutcome? = null,
    val createdAtEpochMillis: Long,
    val updatedAtEpochMillis: Long,
)

@Entity(
    tableName = "daily_matches",
    indices = [Index(value = ["localDate", "zoneId", "matchType"], unique = true)],
)
data class DailyMatchEntity(
    @PrimaryKey val id: String,
    val localDate: String,
    val zoneId: String,
    val seasonId: String,
    val matchType: MatchType,
    val status: MatchStatus,
    val outcome: MatchOutcome?,
    val opponentId: String,
    val opponentName: String,
    val opponentAvatarKey: String,
    val opponentRankTier: RankTier,
    val opponentRankDivision: Int?,
    val opponentPersonality: OpponentPersonality,
    val opponentTargetSteps: Long,
    val totalUserSteps: Long,
    val eligibleUserSteps: Long,
    val restrictedUserSteps: Long,
    val excludedUserSteps: Long,
    val restrictionReasons: String,
    val competitiveQuality: CompetitiveStepQuality,
    val ratingBefore: Int,
    val ratingDelta: Int?,
    val ratingAfter: Int?,
    val ratingBreakdown: String?,
    val finalizedAtEpochMillis: Long?,
    val createdAtEpochMillis: Long,
    val updatedAtEpochMillis: Long,
)

@Entity(tableName = "weekly_leagues")
data class WeeklyLeagueEntity(
    @PrimaryKey val id: String,
    val weekStartLocalDate: String,
    val weekEndLocalDate: String,
    val zoneId: String,
    val status: LeagueStatus,
    val userPoints: Int,
    val userRank: Int?,
    val participantsJson: String,
    val finalizedAtEpochMillis: Long?,
    val createdAtEpochMillis: Long,
    val updatedAtEpochMillis: Long,
)

@Entity(
    tableName = "weekly_league_participants",
    primaryKeys = ["leagueId", "participantId"],
    indices = [Index("leagueId")],
)
data class WeeklyLeagueParticipantEntity(
    val leagueId: String,
    val participantId: String,
    val displayName: String,
    val avatarKey: String,
    val points: Int,
    val eligibleSteps: Long,
    val rank: Int,
    val isLocalPlayer: Boolean,
    val generatedLocally: Boolean,
    val createdAtEpochMillis: Long,
    val updatedAtEpochMillis: Long,
)

@Entity(tableName = "game_seasons")
data class GameSeasonEntity(
    @PrimaryKey val id: String,
    val startedAtEpochMillis: Long,
    val endedAtEpochMillis: Long,
    val startRating: Int,
    val endRating: Int?,
    val highestRankTier: RankTier,
    val highestRankDivision: Int?,
    val wins: Int,
    val losses: Int,
    val draws: Int,
    val totalEligibleSteps: Long,
    val bestWinStreak: Int,
    val status: SeasonStatus,
    val rewardClaimed: Boolean,
    val createdAtEpochMillis: Long,
    val updatedAtEpochMillis: Long,
)

@Entity(tableName = "achievement_unlocks")
data class AchievementUnlockEntity(
    @PrimaryKey val achievementId: String,
    val unlockedAtEpochMillis: Long,
    val progressValue: Long,
    val seasonId: String?,
    val acknowledged: Boolean,
)

@Entity(
    tableName = "game_notification_events",
    indices = [Index(value = ["deduplicationKey"], unique = true)],
)
data class GameNotificationEventEntity(
    @PrimaryKey val id: String,
    val type: GameNotificationType,
    val sourceId: String,
    val deduplicationKey: String,
    val title: String,
    val message: String,
    val destinationRoute: String,
    val createdAtEpochMillis: Long,
    val notBeforeEpochMillis: Long,
    val deliveredAtEpochMillis: Long?,
    val acknowledged: Boolean,
)
