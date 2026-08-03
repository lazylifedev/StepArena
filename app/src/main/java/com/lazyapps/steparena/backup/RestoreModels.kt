package com.lazyapps.steparena.backup

import java.time.Instant
import com.lazyapps.steparena.core.database.entity.*

enum class RestoreErrorCategory { NETWORK, AUTHENTICATION, PERMISSION, BACKUP_UPDATING, UNSUPPORTED, INTEGRITY, LOCAL_STORAGE, SETTINGS }
enum class RestoreStatus { IDLE, CHECKING, AVAILABLE, RESTORING, SUCCESS, NO_CHANGES, FAILED }

data class RestoreMetadata(
    val generation: Long,
    val completedAt: Instant,
    val schemaVersion: Int,
    val counts: Map<String, Int>,
)

data class RestorePreview(
    val metadata: RestoreMetadata,
    val achievementCount: Int,
    val hasSettings: Boolean,
    val unavailableRecordCount: Int,
    val restorableCounts: Map<String, Int> = emptyMap(),
    val excludedCounts: Map<String, Int> = emptyMap(),
)

data class RestoreState(
    val status: RestoreStatus = RestoreStatus.IDLE,
    val preview: RestorePreview? = null,
    val addedAchievements: Int = 0,
    val conflicts: Int = 0,
    val settingsChanged: Int = 0,
    val error: RestoreErrorCategory? = null,
)

data class RestoreAchievement(
    val key: String,
    val unlockedAtEpochMillis: Long,
    val progressValue: Long,
    val seasonId: String?,
    val acknowledged: Boolean,
)

data class RestoreSettings(
    val heightCm: Double?,
    val weightKg: Double?,
    val manualStepLengthMeters: Double?,
    val useAutomaticStepLength: Boolean,
    val dailyStepGoal: Int,
)

data class RestoreSnapshot(
    val metadata: RestoreMetadata,
    val achievements: List<RestoreAchievement>,
    val settings: RestoreSettings?,
    val daily: List<DailyActivityRecordEntity> = emptyList(),
    val hourly: List<HourlyActivityRecordEntity> = emptyList(),
    val sessions: List<WalkingSessionEntity> = emptyList(),
    val matches: List<DailyMatchEntity> = emptyList(),
    val leagues: List<WeeklyLeagueEntity> = emptyList(),
    val leagueParticipants: List<WeeklyLeagueParticipantEntity> = emptyList(),
    val seasons: List<GameSeasonEntity> = emptyList(),
    val integrity: List<CompetitiveIntegritySegmentEntity> = emptyList(),
)

sealed interface RestoreResult {
    data class Success(val added: Int, val conflicts: Int, val settingsChanged: Int) : RestoreResult
    data class Failure(val category: RestoreErrorCategory) : RestoreResult
    data object Busy : RestoreResult
}
