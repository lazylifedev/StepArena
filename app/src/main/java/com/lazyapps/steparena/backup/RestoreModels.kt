package com.lazyapps.steparena.backup

import java.time.Instant
import com.lazyapps.steparena.core.database.entity.*

enum class RestoreErrorCategory { NETWORK, AUTHENTICATION, PERMISSION, BACKUP_UPDATING, UNSUPPORTED, INTEGRITY, LOCAL_STORAGE, SETTINGS }
enum class RestoreStatus { IDLE, CHECKING, AVAILABLE, RESTORING, SUCCESS, NO_CHANGES, FAILED }
enum class RestorePreviewStage {
    AUTH_CHECK, READ_V2_ROOT_INITIAL, VALIDATE_V2_ROOT_INITIAL, READ_ACHIEVEMENTS, READ_SETTINGS,
    READ_DAILY, READ_HOURLY, READ_SESSIONS, READ_CHALLENGE_RESULTS, READ_LEAGUE_HISTORY,
    READ_LEAGUE_PARTICIPANTS, READ_SEASON_HISTORY, READ_INTEGRITY_SEGMENTS,
    READ_V2_ROOT_FINAL, VERIFY_ROOT_UNCHANGED, VALIDATE_DOCUMENT_COUNTS, BUILD_RESTORE_PLAN, COMPLETE,
}
data class RestoreCollectionDiagnostic(
    val expectedCount: Int?, val actualCount: Int, val parsedCount: Int,
    val observedGenerations: Set<String>, val fieldTypes: Map<String, String>,
    val legacyUntaggedCount: Int = 0,
    val olderGenerationCount: Int = 0,
    val currentGenerationCount: Int = 0,
    val newerGenerationCount: Int = 0,
    val parseFailureCount: Int = 0,
)
data class RestoreFailureDiagnostic(
    val stage: RestorePreviewStage,
    val operation: FirestoreOperation?,
    val pathTemplate: String?,
    val firestoreCode: String?,
    val sanitizedMessage: String?,
    val schemaVersion: Long?,
    val expectedGeneration: Long?,
    val validationReason: String?,
    val failedField: String?,
    val expectedType: String?,
    val actualType: String?,
    val rootChangedDuringRead: Boolean?,
    val collections: Map<String, RestoreCollectionDiagnostic>,
)

data class RestoreMetadata(
    val generation: Long,
    val completedAt: Instant,
    val schemaVersion: Int,
    val counts: Map<String, Int>,
    val childGenerationVersion: Int? = null,
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
    val diagnostic: RestoreFailureDiagnostic? = null,
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
