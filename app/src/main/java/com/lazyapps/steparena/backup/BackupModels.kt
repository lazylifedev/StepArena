package com.lazyapps.steparena.backup

import java.time.Instant

const val BACKUP_SCHEMA_VERSION = 2
const val MINIMUM_RESTORE_VERSION = 2
const val CHILD_GENERATION_VERSION = 1

enum class BackupStatus { NEVER_RUN, RUNNING, COMPLETE, FAILED }
enum class BackupErrorCategory { NETWORK, AUTHENTICATION, INTEGRITY, PERMISSION, CONFIGURATION, UNKNOWN }

enum class BackupWriteStage {
    LOAD_LOCAL_DATA, READ_ROOT, ROOT_BEGIN, DAILY, HOURLY, SESSIONS,
    CHALLENGE_RESULTS, LEAGUE_HISTORY, LEAGUE_PARTICIPANTS, SEASON_HISTORY,
    ACHIEVEMENTS, INTEGRITY_SEGMENTS, SETTINGS, ROOT_COMPLETE,
}

enum class FirestoreOperation { GET, CREATE, UPDATE, SET, TRANSACTION_READ, TRANSACTION_WRITE, COMMIT }

data class BackupFailureDiagnostic(
    val stage: BackupWriteStage,
    val operation: FirestoreOperation,
    val pathTemplate: String,
    val inTransaction: Boolean,
    val firestoreCode: String?,
    val sanitizedMessage: String?,
    val requestFieldTypes: Map<String, String>,
    val existingFieldTypes: Map<String, String>?,
    val requestFieldCount: Int,
    val existingFieldCount: Int?,
    val writeDisposition: String?,
    val generationDirection: String?,
    val backupStatus: String?,
)

data class BackupDocument(
    val collection: String,
    val id: String,
    val fields: Map<String, Any?>,
)

data class BackupSnapshot(
    val documents: List<BackupDocument>,
    val localTimeZone: String,
    val newestLocalDate: String?,
    val counts: Map<String, Int>,
)

data class BackupState(
    val status: BackupStatus = BackupStatus.NEVER_RUN,
    val lastSuccessfulBackupAt: Instant? = null,
    val lastAttemptAt: Instant? = null,
    val lastErrorCategory: BackupErrorCategory? = null,
    val completedGeneration: Long = 0,
    val initialBackupCompleted: Boolean = false,
    val lastDocumentCount: Int = 0,
)

sealed interface BackupResult {
    data class Success(val completedAt: Instant, val generation: Long, val documentCount: Int) : BackupResult
    data class Skipped(val reason: Reason) : BackupResult
    data class Failure(
        val category: BackupErrorCategory,
        val diagnostic: BackupFailureDiagnostic? = null,
    ) : BackupResult
    enum class Reason { NOT_GOOGLE_LINKED, ALREADY_RUNNING }
}
