package com.lazyapps.steparena.feature.home

import com.lazyapps.steparena.activity.DailyStepGoal
import com.lazyapps.steparena.core.model.ActivityMetrics
import com.lazyapps.steparena.core.model.DailyMatch
import com.lazyapps.steparena.core.model.DataReliability
import com.lazyapps.steparena.core.model.HomeSnapshot
import com.lazyapps.steparena.core.model.LeagueStatus
import com.lazyapps.steparena.core.model.MatchOutcome
import com.lazyapps.steparena.core.model.RankStatus
import com.lazyapps.steparena.core.model.RankTier
import com.lazyapps.steparena.core.model.TrackingStatus
import java.time.Instant

interface HomeRepository {
    suspend fun loadHome(): HomeSnapshot?
}

class DemoHomeRepository : HomeRepository {
    override suspend fun loadHome(): HomeSnapshot = HomeSnapshot(
        rank = RankStatus(RankTier.GOLD, division = 2, points = 1_840, pointsToNextRank = 660),
        metrics = ActivityMetrics(
            steps = 7_420,
            goalSteps = DailyStepGoal.DEFAULT,
            distanceMeters = 5_630.0,
            durationSeconds = 4_980,
            caloriesKcal = 286.0,
            averageSpeedMetersPerSecond = 1.13,
        ),
        trackingStatus = TrackingStatus.ACTIVE,
        lastHealthyAt = Instant.parse("2026-07-29T09:21:00Z"),
        match = DailyMatch(
            opponentName = "Haruka",
            selfProgress = 0.74f,
            opponentProgress = 0.68f,
            stepsToLead = 0,
            outcome = MatchOutcome.IN_PROGRESS,
        ),
        winStreak = 3,
        league = LeagueStatus(position = 7, memberCount = 30, pointsToPromotion = 420),
        reliability = DataReliability.PARTLY_ESTIMATED,
        isOffline = false,
    )
}
