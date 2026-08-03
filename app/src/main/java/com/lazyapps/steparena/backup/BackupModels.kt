package com.lazyapps.steparena.backup

import java.time.Instant

const val BACKUP_SCHEMA_VERSION = 1

enum class BackupStatus { NEVER_RUN, RUNNING, COMPLETE, FAILED }
enum class BackupErrorCategory { NETWORK, AUTHENTICATION, INTEGRITY, PERMISSION, CONFIGURATION, UNKNOWN }

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
    data class Failure(val category: BackupErrorCategory) : BackupResult
    enum class Reason { NOT_GOOGLE_LINKED, ALREADY_RUNNING }
}
