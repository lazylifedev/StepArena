package com.lazyapps.steparena.backup

import android.content.Context
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import java.time.Instant

private val Context.cloudBackupStateStore by preferencesDataStore("cloud_backup_state")

class BackupStateStore(private val context: Context) {
    val state: Flow<BackupState> = context.cloudBackupStateStore.data.map { values ->
        BackupState(
            status = values[STATUS]?.let { runCatching { BackupStatus.valueOf(it) }.getOrNull() } ?: BackupStatus.NEVER_RUN,
            lastSuccessfulBackupAt = values[LAST_SUCCESS]?.let(Instant::ofEpochMilli),
            lastAttemptAt = values[LAST_ATTEMPT]?.let(Instant::ofEpochMilli),
            lastErrorCategory = values[LAST_ERROR]?.let { runCatching { BackupErrorCategory.valueOf(it) }.getOrNull() },
            completedGeneration = values[GENERATION] ?: 0,
            initialBackupCompleted = values[INITIAL_COMPLETE] ?: false,
            lastDocumentCount = values[DOCUMENT_COUNT] ?: 0,
        )
    }

    suspend fun current(): BackupState = state.first()

    suspend fun markRunning(now: Instant) = context.cloudBackupStateStore.edit {
        it[STATUS] = BackupStatus.RUNNING.name
        it[LAST_ATTEMPT] = now.toEpochMilli()
        it.remove(LAST_ERROR)
    }

    suspend fun markComplete(now: Instant, generation: Long, count: Int) = context.cloudBackupStateStore.edit {
        it[STATUS] = BackupStatus.COMPLETE.name
        it[LAST_SUCCESS] = now.toEpochMilli()
        it[GENERATION] = generation
        it[INITIAL_COMPLETE] = true
        it[DOCUMENT_COUNT] = count
        it.remove(LAST_ERROR)
    }

    suspend fun markFailed(category: BackupErrorCategory) = context.cloudBackupStateStore.edit {
        it[STATUS] = BackupStatus.FAILED.name
        it[LAST_ERROR] = category.name
    }

    private companion object {
        val STATUS = stringPreferencesKey("last_backup_status")
        val LAST_SUCCESS = longPreferencesKey("last_successful_backup_at")
        val LAST_ATTEMPT = longPreferencesKey("last_attempt_at")
        val LAST_ERROR = stringPreferencesKey("last_error_category")
        val GENERATION = longPreferencesKey("completed_generation")
        val INITIAL_COMPLETE = booleanPreferencesKey("initial_backup_completed")
        val DOCUMENT_COUNT = intPreferencesKey("last_document_count")
    }
}
