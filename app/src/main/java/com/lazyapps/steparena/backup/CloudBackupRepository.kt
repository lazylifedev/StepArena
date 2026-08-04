package com.lazyapps.steparena.backup

import com.google.firebase.FirebaseNetworkException
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.GoogleAuthProvider
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.SetOptions
import com.google.firebase.firestore.DocumentSnapshot
import com.lazyapps.steparena.BuildConfig
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import java.time.Clock
import java.time.Instant
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.sync.Mutex
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
    suspend fun upload(uid: String, snapshot: BackupSnapshot): Long {
        // Schema v1 remains at userBackups/{uid}; every v2 write is isolated below versions/v2.
        val root = firestore.collection("userBackups").document(uid)
            .collection("versions").document("v2")
        val generation = beginRoot(root, snapshot)
        snapshot.documents.groupBy { it.collection }.forEach { (collection, documents) ->
          documents.chunked(MAX_TRANSACTION_DOCUMENTS).forEach { chunk ->
            writeDocumentChunk(root, collection, chunk, generation)
          }
        }
        completeRoot(root, snapshot, generation)
        return generation
    }

    private suspend fun beginRoot(
        root: com.google.firebase.firestore.DocumentReference,
        snapshot: BackupSnapshot,
    ): Long {
        val context = DiagnosticContext(BackupWriteStage.READ_ROOT, FirestoreOperation.TRANSACTION_READ, ROOT_PATH, generation = 0)
        try {
            return firestore.runTransaction { transaction ->
                val existing = transaction.get(root)
                val generation = existing.nextCloudBackupGeneration()
                context.stage = BackupWriteStage.ROOT_BEGIN
                context.generation = generation
                val fields = mapOf(
            "schemaVersion" to BACKUP_SCHEMA_VERSION, "appVersionName" to BuildConfig.VERSION_NAME,
            "minimumRestoreVersion" to MINIMUM_RESTORE_VERSION,
            "appVersionCode" to BuildConfig.VERSION_CODE, "databaseVersion" to 10,
            "backupGeneration" to generation, "childGenerationVersion" to CHILD_GENERATION_VERSION,
            "backupStatus" to "in_progress",
            "backupStartedAt" to FieldValue.serverTimestamp(), "localTimeZone" to snapshot.localTimeZone,
            "backupCompletedAt" to FieldValue.delete(),
            "dailyCount" to (snapshot.counts["daily"] ?: 0), "hourlyCount" to (snapshot.counts["hourly"] ?: 0),
            "sessionCount" to (snapshot.counts["sessions"] ?: 0),
            "challengeResultCount" to (snapshot.counts["challengeResults"] ?: 0),
            "leagueHistoryCount" to (snapshot.counts["leagueHistory"] ?: 0),
            "leagueParticipantCount" to (snapshot.counts["leagueParticipants"] ?: 0),
            "seasonHistoryCount" to (snapshot.counts["seasonHistory"] ?: 0),
            "integritySegmentCount" to (snapshot.counts["integritySegments"] ?: 0),
            "achievementCount" to (snapshot.counts["achievements"] ?: 0),
            "settingsCount" to (snapshot.counts["settings"] ?: 0), "newestLocalDate" to snapshot.newestLocalDate,
                ).toMutableMap().apply {
                    put("createdAt", existing.get("createdAt") ?: FieldValue.serverTimestamp())
                    put("updatedAt", FieldValue.serverTimestamp())
                }
                context.capture(existing, fields)
                context.operation = FirestoreOperation.TRANSACTION_WRITE
                transaction.set(root, fields, SetOptions.merge())
                context.operation = FirestoreOperation.COMMIT
                generation
            }.await()
        } catch (error: Throwable) {
            if (error is CloudBackupAlreadyInProgressException || error is InvalidCloudBackupRootException) throw error
            throw context.wrap(error)
        }
    }

    private suspend fun writeDocumentChunk(
        root: com.google.firebase.firestore.DocumentReference,
        collection: String,
        chunk: List<BackupDocument>,
        generation: Long,
    ) {
            val stage = collection.toBackupWriteStage()
            val pathTemplate = collection.toPathTemplate()
            val context = DiagnosticContext(stage, FirestoreOperation.TRANSACTION_READ, pathTemplate, generation = generation)
            try {
              firestore.runTransaction { transaction ->
                val references = chunk.map { item -> root.collection(item.collection).document(item.id) }
                val existingDocuments = references.map(transaction::get)
                chunk.indices.forEach { index ->
                    val item = chunk[index]
                    val reference = references[index]
                    val existing = existingDocuments[index]
                    val fields = childPayloadForGeneration(item.fields, generation).toMutableMap().apply {
                        put("createdAt", existing.get("createdAt") ?: FieldValue.serverTimestamp())
                        put("updatedAt", FieldValue.serverTimestamp())
                    }
                    context.capture(existing, fields)
                    context.operation = FirestoreOperation.TRANSACTION_WRITE
                    // Replace one data document so fields left by schema v1 cannot survive
                    // and violate the schema v2 hasOnly contract. createdAt is preserved above.
                    transaction.set(reference, fields)
                }
                context.operation = FirestoreOperation.COMMIT
              }.await()
            } catch (error: Throwable) {
                throw context.wrap(error)
            }
    }

    private suspend fun completeRoot(
        root: com.google.firebase.firestore.DocumentReference,
        snapshot: BackupSnapshot,
        generation: Long,
    ) {
        updateRoot(root, mapOf(
            "schemaVersion" to BACKUP_SCHEMA_VERSION, "appVersionName" to BuildConfig.VERSION_NAME,
            "minimumRestoreVersion" to MINIMUM_RESTORE_VERSION,
            "appVersionCode" to BuildConfig.VERSION_CODE, "databaseVersion" to 10,
            "backupGeneration" to generation, "childGenerationVersion" to CHILD_GENERATION_VERSION,
            "backupStatus" to "complete",
            "backupCompletedAt" to FieldValue.serverTimestamp(),
            "localTimeZone" to snapshot.localTimeZone, "dailyCount" to (snapshot.counts["daily"] ?: 0),
            "hourlyCount" to (snapshot.counts["hourly"] ?: 0), "sessionCount" to (snapshot.counts["sessions"] ?: 0),
            "challengeResultCount" to (snapshot.counts["challengeResults"] ?: 0),
            "leagueHistoryCount" to (snapshot.counts["leagueHistory"] ?: 0),
            "leagueParticipantCount" to (snapshot.counts["leagueParticipants"] ?: 0),
            "seasonHistoryCount" to (snapshot.counts["seasonHistory"] ?: 0),
            "integritySegmentCount" to (snapshot.counts["integritySegments"] ?: 0),
            "achievementCount" to (snapshot.counts["achievements"] ?: 0),
            "settingsCount" to (snapshot.counts["settings"] ?: 0), "newestLocalDate" to snapshot.newestLocalDate,
        ), BackupWriteStage.ROOT_COMPLETE, ROOT_PATH, generation)
    }

    private suspend fun updateRoot(
        reference: com.google.firebase.firestore.DocumentReference,
        values: Map<String, Any?>,
        stage: BackupWriteStage,
        pathTemplate: String,
        generation: Long,
    ) {
        val context = DiagnosticContext(BackupWriteStage.READ_ROOT, FirestoreOperation.TRANSACTION_READ, pathTemplate, generation = generation)
        try {
            firestore.runTransaction { transaction ->
                val existing = transaction.get(reference)
                validateRootForCompletion(existing, generation)
                context.stage = stage
                val fields = values.toMutableMap().apply {
                    put("createdAt", existing.get("createdAt") ?: FieldValue.serverTimestamp())
                    put("updatedAt", FieldValue.serverTimestamp())
                }
                context.capture(existing, fields)
                context.operation = FirestoreOperation.TRANSACTION_WRITE
                transaction.set(reference, fields, SetOptions.merge())
                context.operation = FirestoreOperation.COMMIT
            }.await()
        } catch (error: Throwable) {
            throw context.wrap(error)
        }
    }

    private companion object {
        const val MAX_TRANSACTION_DOCUMENTS = 400
        const val ROOT_PATH = "userBackups/{uid}/versions/v2"
    }
}

internal fun childPayloadForGeneration(fields: Map<String, Any?>, generation: Long): Map<String, Any?> {
    require(generation >= 1L)
    return fields + ("backupGeneration" to generation)
}

class CloudBackupRepository(
    private val identityProvider: BackupIdentityProvider,
    private val snapshotReader: BackupSnapshotReader,
    private val dataSource: FirestoreBackupDataSource,
    private val stateStore: BackupStateStore,
    private val clock: Clock,
    private val operationGate: BackupOperationGate = BackupOperationGate(),
) {
    private val running = AtomicBoolean(false)
    val state: Flow<BackupState> = stateStore.state

    suspend fun backupNow(): BackupResult {
        val uid = identityProvider.googleLinkedUid() ?: return BackupResult.Skipped(BackupResult.Reason.NOT_GOOGLE_LINKED)
        if (!running.compareAndSet(false, true)) return BackupResult.Skipped(BackupResult.Reason.ALREADY_RUNNING)
        if (!operationGate.tryEnter()) {
            running.set(false)
            return BackupResult.Skipped(BackupResult.Reason.ALREADY_RUNNING)
        }
        val started = clock.instant()
        return try {
            stateStore.markRunning(started)
            val previous = stateStore.current()
            val snapshot = try {
                snapshotReader.read(previous)
            } catch (error: Throwable) {
                throw DiagnosticBackupException(
                    BackupFailureDiagnostic(
                        BackupWriteStage.LOAD_LOCAL_DATA, FirestoreOperation.GET, "local/backupSnapshot",
                        false, null, null, emptyMap(), null, 0, null, null, null, null,
                    ), error,
                )
            }
            val generation = dataSource.upload(uid, snapshot)
            val completed = clock.instant()
            stateStore.markComplete(completed, generation, snapshot.documents.size)
            BackupResult.Success(completed, generation, snapshot.documents.size)
        } catch (cancelled: CancellationException) {
            stateStore.markFailed(BackupErrorCategory.UNKNOWN)
            throw cancelled
        } catch (_: CloudBackupAlreadyInProgressException) {
            stateStore.markFailed(BackupErrorCategory.CONFIGURATION)
            BackupResult.Skipped(BackupResult.Reason.ALREADY_RUNNING)
        } catch (error: Throwable) {
            val failure = error.toBackupFailure()
            stateStore.markFailed(failure.category)
            failure
        } finally {
            running.set(false)
            operationGate.leave()
        }
    }
}

/** Process-wide exclusion shared by backup and restore. */
class BackupOperationGate {
    private val mutex = Mutex()
    fun tryEnter(): Boolean = mutex.tryLock()
    fun leave() { if (mutex.isLocked) mutex.unlock() }
}

private fun Throwable.toCategory(): BackupErrorCategory = when (this) {
    is DiagnosticBackupException -> cause?.toCategory() ?: BackupErrorCategory.UNKNOWN
    is FirebaseNetworkException -> BackupErrorCategory.NETWORK
    is SecurityException -> BackupErrorCategory.PERMISSION
    is InvalidCloudBackupRootException -> BackupErrorCategory.CONFIGURATION
    is IllegalArgumentException, is IllegalStateException -> BackupErrorCategory.INTEGRITY
    is FirebaseFirestoreException -> when (code) {
        FirebaseFirestoreException.Code.UNAUTHENTICATED -> BackupErrorCategory.AUTHENTICATION
        FirebaseFirestoreException.Code.PERMISSION_DENIED -> BackupErrorCategory.PERMISSION
        FirebaseFirestoreException.Code.UNAVAILABLE, FirebaseFirestoreException.Code.DEADLINE_EXCEEDED -> BackupErrorCategory.NETWORK
        else -> BackupErrorCategory.UNKNOWN
    }
    else -> BackupErrorCategory.UNKNOWN
}

internal class CloudBackupAlreadyInProgressException : IllegalStateException("Cloud backup is already in progress")
internal class InvalidCloudBackupRootException : IllegalStateException("Invalid cloud backup root")

internal data class CloudRootMetadata(
    val exists: Boolean,
    val schemaVersion: Long? = null,
    val backupGeneration: Long? = null,
    val backupStatus: String? = null,
    val hasRequiredFields: Boolean = false,
)

internal fun CloudRootMetadata.nextGeneration(): Long {
    if (!exists) return 1L
    if (!hasRequiredFields || schemaVersion != BACKUP_SCHEMA_VERSION.toLong() ||
        backupGeneration == null || backupGeneration < 1L || backupStatus == null
    ) throw InvalidCloudBackupRootException()
    if (backupStatus == "in_progress") throw CloudBackupAlreadyInProgressException()
    if (backupStatus != "complete") throw InvalidCloudBackupRootException()
    return try {
        Math.addExact(backupGeneration, 1L)
    } catch (_: ArithmeticException) {
        throw InvalidCloudBackupRootException()
    }
}

private fun DocumentSnapshot.rootMetadata(): CloudRootMetadata {
    if (!exists()) return CloudRootMetadata(exists = false)
    val fields = data.orEmpty()
    return CloudRootMetadata(
        exists = true,
        schemaVersion = fields["schemaVersion"] as? Long,
        backupGeneration = fields["backupGeneration"] as? Long,
        backupStatus = fields["backupStatus"] as? String,
        hasRequiredFields = ROOT_REQUIRED_FIELDS.all(fields::containsKey),
    )
}

private fun DocumentSnapshot.nextCloudBackupGeneration(): Long = rootMetadata().nextGeneration()

private fun validateRootForCompletion(snapshot: DocumentSnapshot, generation: Long) {
    val root = snapshot.rootMetadata()
    if (!root.exists || !root.hasRequiredFields || root.schemaVersion != BACKUP_SCHEMA_VERSION.toLong() ||
        root.backupStatus != "in_progress" || root.backupGeneration != generation ||
        snapshot.getLong("childGenerationVersion") != CHILD_GENERATION_VERSION.toLong()
    ) throw InvalidCloudBackupRootException()
}

private val ROOT_REQUIRED_FIELDS = setOf(
    "schemaVersion", "minimumRestoreVersion", "appVersionName", "appVersionCode", "databaseVersion",
    "backupGeneration", "backupStatus", "backupStartedAt", "localTimeZone", "dailyCount", "hourlyCount",
    "sessionCount", "challengeResultCount", "leagueHistoryCount", "leagueParticipantCount",
    "seasonHistoryCount", "integritySegmentCount", "achievementCount", "settingsCount", "createdAt", "updatedAt",
)

internal fun Throwable.toBackupFailure(): BackupResult.Failure = BackupResult.Failure(
    category = toCategory(),
    diagnostic = (this as? DiagnosticBackupException)?.diagnostic,
)

internal class DiagnosticBackupException(
    val diagnostic: BackupFailureDiagnostic,
    cause: Throwable,
) : RuntimeException("Cloud backup failed at ${diagnostic.stage}", cause)

private data class DiagnosticContext(
    var stage: BackupWriteStage,
    var operation: FirestoreOperation,
    val pathTemplate: String,
    var generation: Long,
    var requestTypes: Map<String, String> = emptyMap(),
    var existingTypes: Map<String, String>? = null,
    var disposition: String? = null,
    var generationDirection: String? = null,
    var backupStatus: String? = null,
) {
    fun capture(existing: DocumentSnapshot, fields: Map<String, Any?>) {
        requestTypes = safeFieldTypes(fields)
        existingTypes = if (existing.exists()) safeFieldTypes(existing.data.orEmpty()) else emptyMap()
        disposition = if (existing.exists()) "UPDATE" else "CREATE"
        val oldGeneration = existing.getLong("backupGeneration")
        generationDirection = when {
            oldGeneration == null -> "ABSENT_TO_PRESENT"
            generation > oldGeneration -> "INCREASE"
            generation == oldGeneration -> "UNCHANGED"
            else -> "DECREASE"
        }
        backupStatus = fields["backupStatus"]?.let { if (it is String) it else "NON_STRING" }
    }

    fun wrap(error: Throwable): DiagnosticBackupException = if (error is DiagnosticBackupException) error else {
        val firestore = error as? FirebaseFirestoreException
        DiagnosticBackupException(
            BackupFailureDiagnostic(
                stage, operation, pathTemplate, true, firestore?.code?.name,
                sanitizeFirestoreMessage(firestore?.message), requestTypes, existingTypes,
                requestTypes.size, existingTypes?.size, disposition, generationDirection, backupStatus,
            ), error,
        )
    }
}

internal fun safeFieldTypes(fields: Map<String, Any?>): Map<String, String> = fields.toSortedMap().mapValues { (_, value) ->
    when {
        value == null -> "Null"
        value === FieldValue.serverTimestamp() -> "ServerTimestamp"
        value === FieldValue.delete() -> "DeleteSentinel"
        value is FieldValue -> "FieldValue"
        value is String -> "String"
        value is Int || value is Long -> "Long"
        value is Float || value is Double -> "Double"
        value is Boolean -> "Boolean"
        value is List<*> -> "List"
        value is Map<*, *> -> "Map"
        else -> value::class.java.simpleName.take(40).filter { it.isLetterOrDigit() || it == '_' }
    }
}

internal fun sanitizeFirestoreMessage(message: String?): String? {
    if (message == null) return null
    val general = when {
        message.contains("missing or insufficient permissions", ignoreCase = true) -> "Missing or insufficient permissions."
        message.contains("permission_denied", ignoreCase = true) || message.contains("permission denied", ignoreCase = true) -> "PERMISSION_DENIED"
        else -> return null
    }
    return general
}

private fun String.toBackupWriteStage(): BackupWriteStage = when (this) {
    "daily" -> BackupWriteStage.DAILY
    "hourly" -> BackupWriteStage.HOURLY
    "sessions" -> BackupWriteStage.SESSIONS
    "challengeResults" -> BackupWriteStage.CHALLENGE_RESULTS
    "leagueHistory" -> BackupWriteStage.LEAGUE_HISTORY
    "leagueParticipants" -> BackupWriteStage.LEAGUE_PARTICIPANTS
    "seasonHistory" -> BackupWriteStage.SEASON_HISTORY
    "achievements" -> BackupWriteStage.ACHIEVEMENTS
    "integritySegments" -> BackupWriteStage.INTEGRITY_SEGMENTS
    "settings" -> BackupWriteStage.SETTINGS
    else -> throw IllegalArgumentException("Unsupported backup collection")
}

private fun String.toPathTemplate(): String = if (this == "settings") {
    "userBackups/{uid}/versions/v2/settings/current"
} else {
    "userBackups/{uid}/versions/v2/$this/{documentId}"
}

internal suspend fun <T> com.google.android.gms.tasks.Task<T>.await(): T = suspendCancellableCoroutine { continuation ->
    addOnSuccessListener { if (continuation.isActive) continuation.resume(it) }
    addOnFailureListener { if (continuation.isActive) continuation.resumeWithException(it) }
    addOnCanceledListener { continuation.cancel() }
}
