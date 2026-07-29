package com.lazyapps.steparena.app

import com.lazyapps.steparena.core.designsystem.motion.MotionLevel
import com.lazyapps.steparena.core.model.ActivityMetrics
import com.lazyapps.steparena.core.model.DailyMatch
import com.lazyapps.steparena.core.model.DataReliability
import com.lazyapps.steparena.core.model.HomeSnapshot
import com.lazyapps.steparena.core.model.LeagueStatus
import com.lazyapps.steparena.core.model.MatchOutcome
import com.lazyapps.steparena.core.model.RankStatus
import com.lazyapps.steparena.core.model.RankTier
import com.lazyapps.steparena.core.model.TrackingStatus
import com.lazyapps.steparena.feature.home.HomeContent
import com.lazyapps.steparena.feature.home.HomeUiState
import com.lazyapps.steparena.feature.home.SessionState
import com.lazyapps.steparena.feature.home.ManualSessionUi
import java.time.Instant

enum class DebugHomeScenario(val label: String) {
    NORMAL("通常計測中"),
    BEFORE_TRACKING("計測開始前"),
    TRACKING_WARNING("計測停止疑い"),
    PERMISSION_REQUIRED("権限不足"),
    BATTERY_SETTING_REQUIRED("バッテリー設定不足"),
    NO_DATA("データなし"),
    PARTLY_ESTIMATED("一部推定データ"),
    PARTLY_RECOVERED("一部補完データ"),
    OFFLINE("オフライン"),
    GOAL_COMPLETE("目標達成"),
    MATCH_WON("対戦勝利"),
    MATCH_LOST("対戦敗北"),
    NEAR_PROMOTION("昇格直前"),
    SENSOR_UNSUPPORTED("センサー非対応"),
    NOTIFICATION_DENIED("通知未許可"),
    SERVICE_START_FAILURE("Service開始失敗"),
    HEARTBEAT_STALE("Heartbeat stale"),
    SENSOR_RESET("センサー値リセット"),
    DEVICE_REBOOT("端末再起動相当"),
    DATE_CHANGED("日付変更相当"),
    UNUSUAL_STEP_INCREASE("異常歩数増加"),
    PROCESS_RESTORED("プロセス復元相当"),
    MANUAL_WALK("手動散歩中"),
    ;

    fun uiState(motionLevel: MotionLevel): HomeUiState {
        if (this == NO_DATA) {
            return HomeUiState(content = HomeContent.Empty, motionLevel = motionLevel)
        }

        val snapshot = baseSnapshot().let { base ->
            when (this) {
                NORMAL -> base
                BEFORE_TRACKING -> base.copy(
                    trackingStatus = TrackingStatus.NOT_STARTED,
                    lastHealthyAt = null,
                )
                TRACKING_WARNING -> base.copy(trackingStatus = TrackingStatus.MAY_BE_STOPPED)
                PERMISSION_REQUIRED -> base.copy(
                    trackingStatus = TrackingStatus.PERMISSION_REQUIRED,
                    lastHealthyAt = null,
                )
                BATTERY_SETTING_REQUIRED -> base.copy(
                    trackingStatus = TrackingStatus.BATTERY_SETTING_REQUIRED,
                )
                PARTLY_ESTIMATED -> base.copy(reliability = DataReliability.PARTLY_ESTIMATED)
                PARTLY_RECOVERED -> base.copy(reliability = DataReliability.PARTLY_RECOVERED)
                OFFLINE -> base.copy(isOffline = true)
                GOAL_COMPLETE -> base.copy(
                    metrics = base.metrics.copy(steps = 10_840),
                    match = base.match.copy(selfProgress = 1f),
                )
                MATCH_WON -> base.copy(
                    match = base.match.copy(
                        selfProgress = 1f,
                        opponentProgress = 0.82f,
                        outcome = MatchOutcome.WON,
                    ),
                    winStreak = 4,
                )
                MATCH_LOST -> base.copy(
                    match = base.match.copy(
                        selfProgress = 0.71f,
                        opponentProgress = 1f,
                        outcome = MatchOutcome.LOST,
                    ),
                )
                NEAR_PROMOTION -> base.copy(
                    rank = base.rank.copy(pointsToNextRank = 25),
                    league = base.league.copy(position = 4, pointsToPromotion = 15),
                )
                SENSOR_UNSUPPORTED -> base.copy(trackingStatus = TrackingStatus.NOT_STARTED)
                NOTIFICATION_DENIED -> base.copy(trackingStatus = TrackingStatus.PERMISSION_REQUIRED)
                SERVICE_START_FAILURE -> base.copy(trackingStatus = TrackingStatus.MAY_BE_STOPPED)
                HEARTBEAT_STALE -> base.copy(trackingStatus = TrackingStatus.MAY_BE_STOPPED)
                SENSOR_RESET -> base.copy(reliability = DataReliability.PARTLY_RECOVERED)
                DEVICE_REBOOT -> base.copy(reliability = DataReliability.PARTLY_RECOVERED)
                DATE_CHANGED -> base.copy(metrics = base.metrics.copy(steps = 0))
                UNUSUAL_STEP_INCREASE -> base.copy(
                    metrics = base.metrics.copy(steps = 52_000),
                    reliability = DataReliability.PARTLY_ESTIMATED,
                )
                PROCESS_RESTORED -> base.copy(reliability = DataReliability.PARTLY_RECOVERED)
                MANUAL_WALK -> base
                NO_DATA -> base
            }
        }
        val sessionState = if (this == BEFORE_TRACKING) {
            SessionState.TRACKING_STOPPED
        } else if (this == MANUAL_WALK) {
            SessionState.MANUAL_WALK
        } else {
            SessionState.TRACKING
        }
        return HomeUiState(
            content = HomeContent.Ready(snapshot),
            motionLevel = motionLevel,
            sessionState = sessionState,
            manualSession = if (this == MANUAL_WALK) {
                ManualSessionUi(
                    id = "debug-manual-session",
                    startedAtEpochMillis = Instant.now().minusSeconds(480).toEpochMilli(),
                    steps = 842,
                    distanceMeters = 589.4,
                    elapsedSeconds = 480,
                )
            } else null,
        )
    }
}

private fun baseSnapshot() = HomeSnapshot(
    rank = RankStatus(RankTier.GOLD, division = 2, points = 1_840, pointsToNextRank = 660),
    metrics = ActivityMetrics(
        steps = 7_420,
        goalSteps = 10_000,
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
    reliability = DataReliability.COMPLETE,
    isOffline = false,
)
