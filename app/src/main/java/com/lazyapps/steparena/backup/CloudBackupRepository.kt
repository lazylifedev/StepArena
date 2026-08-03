package com.lazyapps.steparena.backup

import com.google.firebase.FirebaseNetworkException
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.GoogleAuthProvider
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.SetOptions
import com.lazyapps.steparena.BuildConfig
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import java.time.Clock
import java.time.Instant
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.suspendCancellableCoroutine

interface BackupIdentityProvider { fun googleLinkedUid(): String? }

class FirebaseBackupIdentityProvider(private val auth: FirebaseAuth) : BackupIdentityProvider {
    override fun googleLinkedUid(): String? = auth.currentUser?.takeIf { user ->
        !user.isAnonymous && user.providerData.any { it.providerId == GoogleAuthProvider.PROVIDER_ID }
    }?.uid
}

class FirestoreBackupDataSource(private val firestore: FirebaseFirestore) {
    suspend fun upload(uid: String, generation: Long, snapshot: BackupSnapshot) {
        val root = firestore.collection("userBackups").document(uid)
        upsertWithServerTimestamps(root, mapOf(
            "schemaVersion" to BACKUP_SCHEMA_VERSION, "appVersionName" to BuildConfig.VERSION_NAME,
            "appVersionCode" to BuildConfig.VERSION_CODE, "databaseVersion" to 10,
            "backupGeneration" to generation, "backupStatus" to "in_progress",
            "backupStartedAt" to FieldValue.serverTimestamp(), "localTimeZone" to snapshot.localTimeZone,
            "backupCompletedAt" to FieldValue.delete(),
            "dailyCount" to (snapshot.counts["daily"] ?: 0), "hourlyCount" to (snapshot.counts["hourly"] ?: 0),
            "sessionCount" to (snapshot.counts["sessions"] ?: 0),
            "challengeResultCount" to (snapshot.counts["challengeResults"] ?: 0),
            "leagueHistoryCount" to (snapshot.counts["leagueHistory"] ?: 0),
            "achievementCount" to (snapshot.counts["achievements"] ?: 0), "newestLocalDate" to snapshot.newestLocalDate,
        ))
        snapshot.documents.chunked(MAX_TRANSACTION_DOCUMENTS).forEach { chunk ->
            firestore.runTransaction { transaction ->
                val references = chunk.map { item -> root.collection(item.collection).document(item.id) }
                val existingDocuments = references.map(transaction::get)
                chunk.indices.forEach { index ->
                    val item = chunk[index]
                    val reference = references[index]
                    val existing = existingDocuments[index]
                    val fields = item.fields.toMutableMap().apply {
                        put("createdAt", existing.get("createdAt") ?: FieldValue.serverTimestamp())
                        put("updatedAt", FieldValue.serverTimestamp())
                    }
                    transaction.set(reference, fields, SetOptions.merge())
                }
            }.await()
        }
        upsertWithServerTimestamps(root, mapOf(
            "schemaVersion" to BACKUP_SCHEMA_VERSION, "appVersionName" to BuildConfig.VERSION_NAME,
            "appVersionCode" to BuildConfig.VERSION_CODE, "databaseVersion" to 10,
            "backupGeneration" to generation, "backupStatus" to "complete",
            "backupCompletedAt" to FieldValue.serverTimestamp(),
            "localTimeZone" to snapshot.localTimeZone, "dailyCount" to (snapshot.counts["daily"] ?: 0),
            "hourlyCount" to (snapshot.counts["hourly"] ?: 0), "sessionCount" to (snapshot.counts["sessions"] ?: 0),
            "challengeResultCount" to (snapshot.counts["challengeResults"] ?: 0),
            "leagueHistoryCount" to (snapshot.counts["leagueHistory"] ?: 0),
            "achievementCount" to (snapshot.counts["achievements"] ?: 0), "newestLocalDate" to snapshot.newestLocalDate,
        ))
    }

    private suspend fun upsertWithServerTimestamps(reference: com.google.firebase.firestore.DocumentReference, values: Map<String, Any?>) {
        firestore.runTransaction { transaction ->
            val existing = transaction.get(reference)
            val fields = values.toMutableMap().apply {
                put("createdAt", existing.get("createdAt") ?: FieldValue.serverTimestamp())
                put("updatedAt", FieldValue.serverTimestamp())
            }
            transaction.set(reference, fields, SetOptions.merge())
        }.await()
    }

    private companion object { const val MAX_TRANSACTION_DOCUMENTS = 400 }
}

class CloudBackupRepository(
    private val identityProvider: BackupIdentityProvider,
    private val snapshotReader: BackupSnapshotReader,
    private val dataSource: FirestoreBackupDataSource,
    private val stateStore: BackupStateStore,
    private val clock: Clock,
) {
    private val running = AtomicBoolean(false)
    val state: Flow<BackupState> = stateStore.state

    suspend fun backupNow(): BackupResult {
        val uid = identityProvider.googleLinkedUid() ?: return BackupResult.Skipped(BackupResult.Reason.NOT_GOOGLE_LINKED)
        if (!running.compareAndSet(false, true)) return BackupResult.Skipped(BackupResult.Reason.ALREADY_RUNNING)
        val started = clock.instant()
        return try {
            stateStore.markRunning(started)
            val previous = stateStore.current()
            val generation = previous.completedGeneration + 1
            val snapshot = snapshotReader.read(previous)
            dataSource.upload(uid, generation, snapshot)
            val completed = clock.instant()
            stateStore.markComplete(completed, generation, snapshot.documents.size)
            BackupResult.Success(completed, generation, snapshot.documents.size)
        } catch (cancelled: CancellationException) {
            stateStore.markFailed(BackupErrorCategory.UNKNOWN)
            throw cancelled
        } catch (error: Throwable) {
            val category = error.toCategory()
            stateStore.markFailed(category)
            BackupResult.Failure(category)
        } finally {
            running.set(false)
        }
    }
}

private fun Throwable.toCategory(): BackupErrorCategory = when (this) {
    is FirebaseNetworkException -> BackupErrorCategory.NETWORK
    is SecurityException -> BackupErrorCategory.PERMISSION
    is IllegalArgumentException, is IllegalStateException -> BackupErrorCategory.INTEGRITY
    is FirebaseFirestoreException -> when (code) {
        FirebaseFirestoreException.Code.UNAUTHENTICATED -> BackupErrorCategory.AUTHENTICATION
        FirebaseFirestoreException.Code.PERMISSION_DENIED -> BackupErrorCategory.PERMISSION
        FirebaseFirestoreException.Code.UNAVAILABLE, FirebaseFirestoreException.Code.DEADLINE_EXCEEDED -> BackupErrorCategory.NETWORK
        else -> BackupErrorCategory.UNKNOWN
    }
    else -> BackupErrorCategory.UNKNOWN
}

private suspend fun <T> com.google.android.gms.tasks.Task<T>.await(): T = suspendCancellableCoroutine { continuation ->
    addOnSuccessListener { if (continuation.isActive) continuation.resume(it) }
    addOnFailureListener { if (continuation.isActive) continuation.resumeWithException(it) }
    addOnCanceledListener { continuation.cancel() }
}
