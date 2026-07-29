package com.lazyapps.steparena.core.model

import java.time.Instant

enum class RankTier { BRONZE, SILVER, GOLD, PLATINUM, DIAMOND }

data class RankStatus(
    val tier: RankTier,
    val division: Int,
    val points: Int,
    val pointsToNextRank: Int,
)

data class ActivityMetrics(
    val steps: Int,
    val goalSteps: Int,
    val distanceMeters: Double,
    val durationSeconds: Long,
    val caloriesKcal: Double,
    val averageSpeedMetersPerSecond: Double,
)

data class DailyMatch(
    val opponentName: String,
    val selfProgress: Float,
    val opponentProgress: Float,
    val stepsToLead: Int,
    val outcome: MatchOutcome,
)

enum class MatchOutcome { IN_PROGRESS, WON, LOST }

data class LeagueStatus(
    val position: Int,
    val memberCount: Int,
    val pointsToPromotion: Int,
)

data class HomeSnapshot(
    val rank: RankStatus,
    val metrics: ActivityMetrics,
    val trackingStatus: TrackingStatus,
    val lastHealthyAt: Instant?,
    val match: DailyMatch,
    val winStreak: Int,
    val league: LeagueStatus,
    val reliability: DataReliability,
    val isOffline: Boolean,
)

enum class TrackingStatus {
    ACTIVE,
    NOT_STARTED,
    MAY_BE_STOPPED,
    PERMISSION_REQUIRED,
    BATTERY_SETTING_REQUIRED,
}

enum class DataReliability { COMPLETE, PARTLY_ESTIMATED, PARTLY_RECOVERED, NO_DATA }
