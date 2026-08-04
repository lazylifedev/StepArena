package com.lazyapps.steparena.backup

import androidx.room.withTransaction
import com.google.firebase.FirebaseNetworkException
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.Source
import com.lazyapps.steparena.activity.DailyStepGoal
import com.lazyapps.steparena.activity.DailyStepGoalRepository
import com.lazyapps.steparena.activity.UserBodyProfile
import com.lazyapps.steparena.activity.UserProfileRepository
import com.lazyapps.steparena.core.database.StepArenaDatabase
import com.lazyapps.steparena.core.database.entity.AchievementUnlockEntity
import com.lazyapps.steparena.core.database.entity.*
import com.lazyapps.steparena.core.database.model.*
import com.lazyapps.steparena.game.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.time.Clock
import java.time.Instant

class CloudRestoreRepository(
    private val identityProvider: BackupIdentityProvider,
    private val firestore: FirebaseFirestore,
    private val database: StepArenaDatabase,
    private val profileRepository: UserProfileRepository,
    private val goalRepository: DailyStepGoalRepository,
    private val clock: Clock,
    private val operationGate: BackupOperationGate,
) {
    private val mutableState = MutableStateFlow(RestoreState())
    val state: StateFlow<RestoreState> = mutableState
    private var staged: RestoreSnapshot? = null
    private var stagedUid: String? = null

    fun clearForAccountChange() {
        staged = null
        stagedUid = null
        mutableState.value = RestoreState()
    }

    suspend fun check(): RestoreState {
        val uid = identityProvider.googleLinkedUid() ?: return fail(RestoreErrorCategory.AUTHENTICATION)
        if (!operationGate.tryEnter()) return mutableState.value
        mutableState.value = RestoreState(RestoreStatus.CHECKING)
        return try {
            val snapshot = downloadConsistent(uid)
            staged = snapshot
            stagedUid = uid
            val unsupported = if (snapshot.metadata.schemaVersion == 1)
                snapshot.metadata.counts.filterKeys { it !in setOf("achievements", "settings") }.values.sum() else 0
            val today = java.time.LocalDate.now(clock).toString()
            val restorable = mapOf("daily" to snapshot.daily.count { it.localDate < today },
                "hourly" to snapshot.hourly.count { it.localDate < today },
                "sessions" to snapshot.sessions.count { it.localDate < today && it.status in setOf(WalkingSessionStatus.COMPLETED, WalkingSessionStatus.RECOVERED) },
                "challengeResults" to snapshot.matches.count { it.localDate < today && it.status == MatchStatus.FINALIZED },
                "leagueHistory" to snapshot.leagues.count { it.weekEndLocalDate < today },
                "seasonHistory" to snapshot.seasons.count { it.endedAtEpochMillis < clock.instant().toEpochMilli() },
                "integritySegments" to snapshot.integrity.count { it.localDate < today }, "achievements" to snapshot.achievements.size)
            val excluded = mapOf("daily" to snapshot.daily.size - restorable.getValue("daily"),
                "hourly" to snapshot.hourly.size - restorable.getValue("hourly"),
                "sessions" to snapshot.sessions.size - restorable.getValue("sessions"),
                "challengeResults" to snapshot.matches.size - restorable.getValue("challengeResults"),
                "integritySegments" to snapshot.integrity.size - restorable.getValue("integritySegments"))
            RestoreState(
                RestoreStatus.AVAILABLE,
                RestorePreview(snapshot.metadata, snapshot.achievements.size, snapshot.settings != null, unsupported, restorable, excluded),
            ).also { mutableState.value = it }
        } catch (error: Throwable) {
            if (error is CancellationException) throw error
            fail(error.restoreCategory(), (error as? RestoreDiagnosticException)?.diagnostic)
        } finally { operationGate.leave() }
    }

    suspend fun restoreConfirmed(): RestoreResult {
        val uid = identityProvider.googleLinkedUid() ?: return RestoreResult.Failure(RestoreErrorCategory.AUTHENTICATION)
        if (stagedUid != uid) clearForAccountChange()
        if (!operationGate.tryEnter()) return RestoreResult.Busy
        mutableState.value = mutableState.value.copy(status = RestoreStatus.RESTORING, error = null)
        return try {
            val snapshot = downloadConsistent(uid)
            applySnapshot(snapshot)
        } catch (error: Throwable) {
            if (error is CancellationException) throw error
            val category = error.restoreCategory()
            fail(category, (error as? RestoreDiagnosticException)?.diagnostic)
            RestoreResult.Failure(category)
        } finally { operationGate.leave() }
    }

    internal suspend fun applySnapshot(snapshot: RestoreSnapshot): RestoreResult {
        val todayBefore = java.time.LocalDate.now(clock)
            var added = 0
            var conflicts = 0
            database.withTransaction {
                check(java.time.LocalDate.now(clock) == todayBefore) { "local_day_changed" }
                val today = todayBefore.toString()
                snapshot.daily.filter { it.localDate < today }.forEach { value ->
                    if (database.daily().get(value.localDate, value.zoneId) == null) { database.daily().upsert(value); added++ } else conflicts++
                }
                val hourlyKeys = database.hourly().all().map { listOf(it.localDate, it.hourOfDay, it.zoneId, it.utcOffsetSeconds) }.toSet()
                snapshot.hourly.filter { it.localDate < today }.forEach { value ->
                    if (listOf(value.localDate, value.hourOfDay, value.zoneId, value.utcOffsetSeconds) !in hourlyKeys) { database.hourly().upsert(value); added++ } else conflicts++
                }
                snapshot.sessions.filter { it.localDate < today && it.status in setOf(WalkingSessionStatus.COMPLETED, WalkingSessionStatus.RECOVERED) }.forEach { value ->
                    if (database.sessions().get(value.id) == null) { database.sessions().upsert(value); added++ } else conflicts++
                }
                snapshot.matches.filter { it.localDate < today && it.status == MatchStatus.FINALIZED }.forEach { value ->
                    if (database.dailyMatches().getForDate(value.localDate, value.zoneId) == null) { database.dailyMatches().insert(value); added++ } else conflicts++
                }
                val acceptedLeagueIds = snapshot.leagues.filter { it.weekEndLocalDate < today }.map { it.id }.toSet()
                snapshot.leagues.filter { it.id in acceptedLeagueIds }.forEach { value -> if (database.weeklyLeagues().get(value.id) == null) { database.weeklyLeagues().upsert(value); added++ } else conflicts++ }
                snapshot.leagueParticipants.filter { it.leagueId in acceptedLeagueIds }.groupBy { it.leagueId }.forEach { (leagueId, cloud) ->
                    val existingIds = database.weeklyLeagueParticipants().getForLeague(leagueId).map { it.participantId }.toSet()
                    val missing = cloud.filter { it.participantId !in existingIds }
                    if (missing.isNotEmpty()) { database.weeklyLeagueParticipants().upsertAll(missing); added += missing.size }
                    conflicts += cloud.size - missing.size
                }
                snapshot.seasons.filter { it.endedAtEpochMillis < clock.instant().toEpochMilli() }.forEach { value -> if (database.gameSeasons().get(value.id) == null) { database.gameSeasons().upsert(value); added++ } else conflicts++ }
                snapshot.achievements.forEach { value ->
                    if (database.achievementUnlocks().get(value.key) == null) {
                        database.achievementUnlocks().insert(AchievementUnlockEntity(value.key, value.unlockedAtEpochMillis, value.progressValue, value.seasonId, value.acknowledged))
                        added++
                    } else conflicts++
                }
                snapshot.integrity.filter { it.localDate < today }.forEach { value ->
                    if (database.competitiveIntegritySegments().byId(value.id) == null) { database.competitiveIntegritySegments().upsert(value); added++ } else conflicts++
                }
            }
            var settingsChanged = 0
            snapshot.settings?.let { settings ->
                try {
                    profileRepository.save(UserBodyProfile(settings.heightCm, settings.weightKg, settings.manualStepLengthMeters, settings.useAutomaticStepLength))
                    goalRepository.save(settings.dailyStepGoal)
                    settingsChanged = 5
                } catch (_: Throwable) {
                    mutableState.value = RestoreState(RestoreStatus.FAILED, error = RestoreErrorCategory.SETTINGS, addedAchievements = added, conflicts = conflicts)
                    return RestoreResult.Failure(RestoreErrorCategory.SETTINGS)
                }
            }
            val status = if (added == 0 && settingsChanged == 0) RestoreStatus.NO_CHANGES else RestoreStatus.SUCCESS
            mutableState.value = RestoreState(status, addedAchievements = added, conflicts = conflicts, settingsChanged = settingsChanged)
            return RestoreResult.Success(added, conflicts, settingsChanged)
    }

    private suspend fun downloadConsistent(uid: String): RestoreSnapshot {
        val diagnostic = RestoreDiagnosticTracker()
        try {
        val legacyRoot = firestore.collection("userBackups").document(uid)
        val versionedRoot = legacyRoot.collection("versions").document("v2")
        diagnostic.read(RestorePreviewStage.READ_V2_ROOT_INITIAL, ROOT_PATH)
        val versioned = versionedRoot.get(Source.SERVER).await()
        // An existing but incomplete/invalid v2 must fail validation; it never falls back to v1.
        val root = if (versioned.exists()) versionedRoot else legacyRoot
        val before = if (versioned.exists()) versioned else legacyRoot.get(Source.SERVER).await()
        diagnostic.stage = RestorePreviewStage.VALIDATE_V2_ROOT_INITIAL
        diagnostic.captureRoot(before)
        val metadata = before.restoreMetadata()
        diagnostic.metadata = metadata
        diagnostic.read(RestorePreviewStage.READ_ACHIEVEMENTS, path("achievements"))
        val achievementDocs = if (metadata.schemaVersion == 2) {
            diagnostic.readCurrent(root, "achievements", RestorePreviewStage.READ_ACHIEVEMENTS)
        } else {
            root.collection("achievements").get(Source.SERVER).await().documents
        }
        val achievements = diagnostic.parse("achievements", achievementDocs) { it.toAchievement(metadata.schemaVersion) }
        diagnostic.read(RestorePreviewStage.READ_SETTINGS, "userBackups/{uid}/versions/v2/settings/current")
        val settingsDoc = root.collection("settings").document("current").get(Source.SERVER).await()
        if (metadata.schemaVersion == 2) diagnostic.capturePhysical("settings", listOfNotNull(settingsDoc.takeIf { it.exists() }))
        val settingsCurrent = settingsDoc.takeIf {
            it.exists() && (metadata.schemaVersion == 1 || it.getLong("backupGeneration") == metadata.generation)
        }
        val settings = settingsCurrent?.toSettings(metadata.schemaVersion)
        val daily = if (metadata.schemaVersion == 2) diagnostic.readParse(root, "daily", RestorePreviewStage.READ_DAILY) { it.toDaily() } else emptyList()
        val hourly = if (metadata.schemaVersion == 2) diagnostic.readParse(root, "hourly", RestorePreviewStage.READ_HOURLY) { it.toHourly() } else emptyList()
        val sessions = if (metadata.schemaVersion == 2) diagnostic.readParse(root, "sessions", RestorePreviewStage.READ_SESSIONS) { it.toSession() } else emptyList()
        val matches = if (metadata.schemaVersion == 2) diagnostic.readParse(root, "challengeResults", RestorePreviewStage.READ_CHALLENGE_RESULTS) { it.toMatch() } else emptyList()
        val leagues = if (metadata.schemaVersion == 2) diagnostic.readParse(root, "leagueHistory", RestorePreviewStage.READ_LEAGUE_HISTORY) { it.toLeague() } else emptyList()
        val leagueParticipants = if (metadata.schemaVersion == 2) diagnostic.readParse(root, "leagueParticipants", RestorePreviewStage.READ_LEAGUE_PARTICIPANTS) { it.toLeagueParticipant(leagues) } else emptyList()
        val seasons = if (metadata.schemaVersion == 2) diagnostic.readParse(root, "seasonHistory", RestorePreviewStage.READ_SEASON_HISTORY) { it.toSeason() } else emptyList()
        val integrity = if (metadata.schemaVersion == 2) diagnostic.readParse(root, "integritySegments", RestorePreviewStage.READ_INTEGRITY_SEGMENTS) { it.toIntegrity() } else emptyList()
        diagnostic.read(RestorePreviewStage.READ_V2_ROOT_FINAL, ROOT_PATH)
        val after = root.get(Source.SERVER).await().restoreMetadata()
        diagnostic.stage = RestorePreviewStage.VERIFY_ROOT_UNCHANGED
        diagnostic.rootChanged = metadata != after
        require(metadata == after) { "generation_changed" }
        diagnostic.stage = RestorePreviewStage.VALIDATE_DOCUMENT_COUNTS
        diagnostic.validateCount("achievements", achievements.size)
        if ((metadata.counts["settings"] ?: 0) == 1 && settingsCurrent == null) {
            throw IllegalArgumentException("settings_generation_mismatch")
        }
        diagnostic.validateCount("settings", if (settings != null) 1 else 0)
        if (metadata.schemaVersion == 2) {
            mapOf("daily" to daily.size, "hourly" to hourly.size, "sessions" to sessions.size,
                "challengeResults" to matches.size, "leagueHistory" to leagues.size, "leagueParticipants" to leagueParticipants.size, "seasonHistory" to seasons.size,
                "integritySegments" to integrity.size).forEach { (key, count) -> diagnostic.validateCount(key, count) }
        }
        diagnostic.stage = RestorePreviewStage.BUILD_RESTORE_PLAN
        return RestoreSnapshot(metadata, achievements, settings, daily, hourly, sessions, matches, leagues, leagueParticipants, seasons, integrity)
        } catch (error: Throwable) {
            if (error is RestoreDiagnosticException) throw error
            throw RestoreDiagnosticException(diagnostic.build(error), error)
        }
    }

    private fun fail(category: RestoreErrorCategory, diagnostic: RestoreFailureDiagnostic? = null) =
        RestoreState(RestoreStatus.FAILED, error = category, diagnostic = diagnostic).also { mutableState.value = it }

    private companion object { const val ROOT_PATH = "userBackups/{uid}/versions/v2" }
}

internal class RestoreDiagnosticException(
    val diagnostic: RestoreFailureDiagnostic,
    cause: Throwable,
) : RuntimeException("Restore preview failed at ${diagnostic.stage}", cause)

internal class RestoreDiagnosticTracker {
    var stage = RestorePreviewStage.AUTH_CHECK
    var operation: FirestoreOperation? = null
    var pathTemplate: String? = null
    var metadata: RestoreMetadata? = null
    var rootChanged: Boolean? = null
    private var currentFieldTypes: Map<String, String> = emptyMap()
    private var reason: String? = null
    private val collections = linkedMapOf<String, RestoreCollectionDiagnostic>()

    fun read(next: RestorePreviewStage, path: String) {
        stage = next; operation = FirestoreOperation.GET; pathTemplate = path
        currentFieldTypes = emptyMap(); reason = null
    }
    fun captureRoot(root: DocumentSnapshot) { currentFieldTypes = safeFieldTypes(root.data.orEmpty()) }
    fun capturePhysical(name: String, docs: List<DocumentSnapshot>) {
        val expected = metadata?.generation
        val legacy = docs.count { !it.data.orEmpty().containsKey("backupGeneration") }
        val typed = docs.mapNotNull { it.data.orEmpty()["backupGeneration"] as? Long }
        val invalid = docs.count { it.data.orEmpty().containsKey("backupGeneration") && it.data.orEmpty()["backupGeneration"] !is Long }
        if (invalid > 0) { reason = "CHILD_GENERATION_TYPE_INVALID"; throw IllegalArgumentException("child_generation_type_invalid") }
        val previous = collections[name]
        collections[name] = RestoreCollectionDiagnostic(
            metadata?.counts?.get(name), previous?.actualCount ?: 0, previous?.parsedCount ?: 0,
            docs.map { it.data.orEmpty()["backupGeneration"]?.toString() ?: "ABSENT" }.toSet(),
            previous?.fieldTypes ?: docs.firstOrNull()?.data?.let(::safeFieldTypes).orEmpty(),
            legacy, typed.count { expected != null && it < expected }, typed.count { it == expected },
            typed.count { expected != null && it > expected }, previous?.parseFailureCount ?: 0,
        )
    }
    fun capture(name: String, docs: List<DocumentSnapshot>, parsed: Int = 0) {
        val generations = docs.map { it.getLong("backupGeneration")?.toString() ?: "ABSENT" }.toSet()
        currentFieldTypes = docs.firstOrNull()?.data?.let(::safeFieldTypes).orEmpty()
        val previous = collections[name]
        collections[name] = RestoreCollectionDiagnostic(metadata?.counts?.get(name), docs.size, parsed, generations, currentFieldTypes,
            previous?.legacyUntaggedCount ?: 0, previous?.olderGenerationCount ?: 0, docs.size,
            previous?.newerGenerationCount ?: 0, previous?.parseFailureCount ?: 0)
    }
    inline fun <T> parse(name: String, docs: List<DocumentSnapshot>, parser: (DocumentSnapshot) -> T): List<T> {
        capture(name, docs)
        val result = ArrayList<T>(docs.size)
        docs.forEach { doc ->
            currentFieldTypes = safeFieldTypes(doc.data.orEmpty())
            try {
                result += parser(doc)
            } catch (error: Throwable) {
                collections[name] = collections.getValue(name).copy(
                    parsedCount = result.size,
                    parseFailureCount = collections.getValue(name).parseFailureCount + 1,
                    fieldTypes = currentFieldTypes,
                )
                throw error
            }
            collections[name] = collections.getValue(name).copy(parsedCount = result.size, fieldTypes = currentFieldTypes)
        }
        return result
    }
    suspend inline fun <T> readParse(
        root: com.google.firebase.firestore.DocumentReference,
        name: String,
        next: RestorePreviewStage,
        parser: (DocumentSnapshot) -> T,
    ): List<T> {
        return parse(name, readCurrent(root, name, next), parser)
    }
    suspend fun readCurrent(root: com.google.firebase.firestore.DocumentReference, name: String, next: RestorePreviewStage): List<DocumentSnapshot> {
        read(next, path(name))
        val collection = root.collection(name)
        val all = collection.get(Source.SERVER).await().documents
        capturePhysical(name, all)
        val generation = metadata?.generation ?: error("metadata_invalid")
        return collection.whereEqualTo("backupGeneration", generation).get(Source.SERVER).await().documents
    }
    fun validateCount(name: String, actual: Int) {
        val expected = metadata?.counts?.get(name)
        val previous = collections[name] ?: RestoreCollectionDiagnostic(expected, actual, actual, emptySet(), emptyMap())
        collections[name] = previous.copy(expectedCount = expected, actualCount = actual)
        if (expected != actual) {
            reason = "CURRENT_GENERATION_COUNT_MISMATCH"
            throw IllegalArgumentException("count_mismatch")
        }
    }
    fun build(error: Throwable): RestoreFailureDiagnostic {
        val firestore = error as? FirebaseFirestoreException
        val rawReason = reason ?: when (error.message) {
            "generation_changed" -> "ROOT_CHANGED_DURING_READ"
            "legacy_v2_child_generation_missing" -> "LEGACY_V2_CHILD_GENERATION_MISSING"
            "settings_generation_mismatch" -> "SETTINGS_GENERATION_MISMATCH"
            "backup_updating" -> "ROOT_NOT_COMPLETE"
            "unsupported_schema" -> "UNSUPPORTED_SCHEMA"
            "metadata_invalid" -> "INVALID_ROOT_METADATA"
            else -> error.message?.takeIf { it.matches(Regex("[A-Za-z0-9_]+")) }?.uppercase() ?: error::class.java.simpleName.uppercase()
        }
        val missing = error.message?.takeIf { it.endsWith("_missing") }?.removeSuffix("_missing")
        return RestoreFailureDiagnostic(
            stage, operation, pathTemplate, firestore?.code?.name, sanitizeFirestoreMessage(firestore?.message),
            metadata?.schemaVersion?.toLong(), metadata?.generation, rawReason, missing,
            if (missing != null) "RequiredField" else null,
            if (missing != null) currentFieldTypes[missing] ?: "Absent" else null,
            rootChanged, collections.toMap(),
        )
    }
}

private fun path(collection: String) = "userBackups/{uid}/versions/v2/$collection/{documentId}"

private fun DocumentSnapshot.restoreMetadata(): RestoreMetadata {
    require(exists()) { "backup_missing" }
    require(getString("backupStatus") == "complete") { "backup_updating" }
    val schema = getLong("schemaVersion")?.toInt() ?: error("metadata_invalid")
    require(schema in 1..BACKUP_SCHEMA_VERSION) { "unsupported_schema" }
    val generation = getLong("backupGeneration")?.takeIf { it > 0 } ?: error("metadata_invalid")
    val childGenerationVersion = getLong("childGenerationVersion")?.toInt()
    if (schema == 2) require(childGenerationVersion == CHILD_GENERATION_VERSION) { "legacy_v2_child_generation_missing" }
    val completed = getTimestamp("backupCompletedAt")?.toDate()?.toInstant() ?: error("metadata_invalid")
    val counts = mapOf(
        "daily" to count("dailyCount"), "hourly" to count("hourlyCount"), "sessions" to count("sessionCount"),
        "challengeResults" to count("challengeResultCount"), "leagueHistory" to count("leagueHistoryCount"),
        "achievements" to count("achievementCount"), "settings" to if (schema == 1) 1 else count("settingsCount"),
        "seasonHistory" to count("seasonHistoryCount"), "integritySegments" to count("integritySegmentCount"),
        "leagueParticipants" to count("leagueParticipantCount"),
    )
    return RestoreMetadata(generation, completed, schema, counts, childGenerationVersion)
}
private fun DocumentSnapshot.count(key: String): Int = (getLong(key) ?: 0).also { require(it in 0..Int.MAX_VALUE) }.toInt()
private fun DocumentSnapshot.toAchievement(schema: Int): RestoreAchievement {
    require(getLong("schemaVersion")?.toInt() == schema)
    val key = getString("achievementKey")?.takeIf { it.isNotBlank() } ?: error("achievement_key")
    if (schema == 2) {
        verifyV2Stable(stableBackupId("achievement", key))
        getString("seasonId")?.let { require(getString("seasonStableId") == stableBackupId("season", it)) }
    }
    val unlocked = getLong("unlockedAtEpochMillis")?.takeIf { it >= 0 } ?: error("achievement_time")
    val progress = getLong("progressValue")?.takeIf { it >= 0 } ?: error("achievement_progress")
    return RestoreAchievement(key, unlocked, progress, getString("seasonId"), getBoolean("acknowledged") ?: false)
}
private fun DocumentSnapshot.toSettings(schema: Int): RestoreSettings {
    require(getLong("schemaVersion")?.toInt() == schema)
    fun finite(key: String) = getDouble(key)?.also { require(it.isFinite()) }
    val goal = getLong("dailyStepGoal")?.toInt() ?: error("goal_missing")
    require(goal in DailyStepGoal.MINIMUM..DailyStepGoal.MAXIMUM)
    return RestoreSettings(finite("heightCm"), finite("weightKg"), finite("manualStepLengthMeters"), getBoolean("useAutomaticStepLength") ?: true, goal)
}

private fun DocumentSnapshot.string(key: String) = getString(key)?.takeIf { it.isNotBlank() } ?: error("${key}_missing")
private fun DocumentSnapshot.long(key: String) = getLong(key) ?: error("${key}_missing")
private fun DocumentSnapshot.int(key: String) = long(key).also { require(it in Int.MIN_VALUE..Int.MAX_VALUE) }.toInt()
private fun DocumentSnapshot.bool(key: String) = getBoolean(key) ?: error("${key}_missing")
private fun DocumentSnapshot.finite(key: String): Double? = getDouble(key)?.also { require(it.isFinite()) }
private inline fun <reified T : Enum<T>> DocumentSnapshot.enum(key: String): T = enumValueOf<T>(string(key))
private fun DocumentSnapshot.verifyV2Stable(expected: String, documentIdMustMatch: Boolean = true) {
    require(getLong("schemaVersion")?.toInt() == 2)
    require(string("stableId") == expected) { "stable_id_mismatch" }
    if (documentIdMustMatch) require(id == expected) { "stable_id_mismatch" }
}

private fun DocumentSnapshot.toDaily(): DailyActivityRecordEntity {
    val date = string("localDate"); val zone = string("zoneId")
    require(id == date); verifyV2Stable(stableBackupId("daily", date, zone), false)
    return DailyActivityRecordEntity(string("roomId"), date, zone, long("steps"), long("unclassifiedSteps"),
        enum("unclassifiedStepsQuality"), long("externalRecoveredSteps"), long("unallocatedMeasuredSteps"),
        finite("distanceMeters"), getLong("walkingDurationSeconds"), finite("caloriesKcal"), finite("averageWalkingSpeedKmh"),
        enum("stepsQuality"), enum("distanceQuality"), enum("durationQuality"), enum("caloriesQuality"), enum("speedQuality"),
        int("activeHourCount"), int("walkingSessionCount"), bool("finalized"), getLong("finalizedAtEpochMillis"),
        long("createdAtEpochMillis"), long("updatedAtEpochMillis"))
}

private fun DocumentSnapshot.toHourly(): HourlyActivityRecordEntity {
    val date = string("localDate"); val hour = int("hourOfDay"); val zone = string("zoneId"); val offset = int("utcOffsetSeconds")
    require(id == "%s-%02d".format(java.util.Locale.US, date, hour)); verifyV2Stable(stableBackupId("hourly", date, hour.toString(), zone, offset.toString()), false)
    return HourlyActivityRecordEntity(string("roomId"), date, hour, zone, offset, long("periodStartEpochMillis"),
        long("periodEndEpochMillis"), long("steps"), finite("distanceMeters"), getLong("walkingDurationSeconds"),
        finite("caloriesKcal"), finite("averageWalkingSpeedKmh"), enum("stepsQuality"), enum("distanceQuality"),
        enum("durationQuality"), enum("caloriesQuality"), enum("speedQuality"), getLong("firstActivityAtEpochMillis"),
        getLong("lastActivityAtEpochMillis"), int("sensorEventCount"), long("recoveredSteps"), long("estimatedSteps"),
        finite("appliedStepLengthMeters") ?: error("appliedStepLengthMeters_missing"),
        finite("appliedWeightKg") ?: error("appliedWeightKg_missing"), int("calorieFormulaVersion"),
        long("createdAtEpochMillis"), long("updatedAtEpochMillis"))
}

private fun DocumentSnapshot.toSession(): WalkingSessionEntity {
    val roomId = string("roomId"); val started = long("startedAtEpochMillis")
    verifyV2Stable(stableBackupId("session", roomId, started.toString()))
    val status = enum<WalkingSessionStatus>("status"); require(status in setOf(WalkingSessionStatus.COMPLETED, WalkingSessionStatus.RECOVERED))
    val ended = getLong("endedAtEpochMillis") ?: error("endedAtEpochMillis_missing"); require(ended >= started)
    return WalkingSessionEntity(roomId, string("localDate"), string("zoneId"), started, ended, long("steps"),
        finite("distanceMeters"), long("activeDurationSeconds"), long("elapsedDurationSeconds"), long("pausedDurationSeconds"),
        finite("caloriesKcal"), finite("averageMovingSpeedKmh"), finite("averageElapsedSpeedKmh"), enum("sessionType"), status,
        enum("stepsQuality"), enum("distanceQuality"), enum("durationQuality"), enum("caloriesQuality"), enum("speedQuality"),
        getString("trackingServiceSessionId"), getLong("lastWalkingEventAtEpochMillis"), getLong("pausedSinceEpochMillis"),
        bool("isManual"), int("detectorEventCount"), long("estimatedStepCount"), long("recoveredStepCount"),
        long("createdAtEpochMillis"), long("updatedAtEpochMillis"))
}

private fun DocumentSnapshot.toMatch(): DailyMatchEntity {
    val roomId = string("roomId"); val date = string("localDate"); verifyV2Stable(stableBackupId("match", roomId, date))
    val seasonId = string("seasonId"); require(string("seasonStableId") == stableBackupId("season", seasonId))
    val total = long("totalSteps"); val eligible = long("eligibleSteps"); val restricted = long("restrictedSteps"); val excluded = long("excludedSteps")
    require(total == eligible + restricted + excluded)
    val status = enum<MatchStatus>("status"); require(status == MatchStatus.FINALIZED)
    return DailyMatchEntity(roomId, date, string("zoneId"), seasonId, enum("matchType"), status,
        getString("outcome")?.let { enumValueOf<MatchOutcome>(it) }, string("opponentId"), string("opponentName"),
        string("opponentAvatarKey"), enum("opponentRankTier"), getLong("opponentRankDivision")?.toInt(),
        enum("opponentPersonality"), long("opponentTargetSteps"), total, eligible, restricted, excluded,
        getString("restrictionReasons") ?: error("restrictionReasons_missing"), enum("competitiveQuality"), int("ratingBefore"), getLong("ratingDelta")?.toInt(),
        getLong("ratingAfter")?.toInt(), getString("ratingBreakdown"), long("finalizedAtEpochMillis"),
        long("createdAtEpochMillis"), long("updatedAtEpochMillis"))
}

private fun DocumentSnapshot.toLeague(): WeeklyLeagueEntity {
    val roomId = string("roomId"); val start = string("periodStart"); verifyV2Stable(stableBackupId("league", roomId, start))
    val status = enum<LeagueStatus>("status"); require(status == LeagueStatus.FINALIZED)
    return WeeklyLeagueEntity(roomId, start, string("periodEnd"), string("zoneId"), status, int("points"),
        getLong("rank")?.toInt(), string("participantsJson"), long("finalizedAtEpochMillis"),
        long("createdAtEpochMillis"), long("updatedAtEpochMillis"))
}

private fun DocumentSnapshot.toLeagueParticipant(leagues: List<WeeklyLeagueEntity>): WeeklyLeagueParticipantEntity {
    val leagueId = string("leagueRoomId"); val participantId = string("participantId")
    verifyV2Stable(stableBackupId("league-participant", leagueId, participantId))
    val league = leagues.singleOrNull { it.id == leagueId } ?: error("league_parent_missing")
    require(string("leagueStableId") == stableBackupId("league", league.id, league.weekStartLocalDate))
    return WeeklyLeagueParticipantEntity(leagueId, participantId, string("displayName"), string("avatarKey"),
        int("points"), long("eligibleSteps"), int("rank"), bool("isLocalPlayer"), bool("generatedLocally"),
        long("createdAtEpochMillis"), long("updatedAtEpochMillis"))
}

private fun DocumentSnapshot.toSeason(): GameSeasonEntity {
    val roomId = string("roomId"); val started = long("startedAtEpochMillis"); verifyV2Stable(stableBackupId("season", roomId, started.toString()))
    val status = enum<SeasonStatus>("status"); require(status == SeasonStatus.FINALIZED)
    return GameSeasonEntity(roomId, started, long("endedAtEpochMillis"), int("startRating"), getLong("endRating")?.toInt(),
        enum("highestRankTier"), getLong("highestRankDivision")?.toInt(), int("wins"), int("losses"), int("draws"),
        long("totalEligibleSteps"), int("bestWinStreak"), status, bool("rewardClaimed"),
        long("createdAtEpochMillis"), long("updatedAtEpochMillis"))
}

private fun DocumentSnapshot.toIntegrity(): CompetitiveIntegritySegmentEntity {
    val roomId = string("roomId"); val started = long("startedAtEpochMillis"); verifyV2Stable(stableBackupId("integrity", roomId, started.toString()))
    val total = long("totalSteps"); val eligible = long("eligibleSteps"); val restricted = long("restrictedSteps"); val excluded = long("excludedSteps")
    require(total == eligible + restricted + excluded)
    return CompetitiveIntegritySegmentEntity(roomId, string("localDate"), string("zoneId"), started,
        long("endedAtEpochMillis"), total, eligible, restricted, excluded, enum("assessment"),
        getString("reasons") ?: error("reasons_missing"),
        int("classifierVersion"), long("createdAtEpochMillis"))
}
private fun Throwable.restoreCategory(): RestoreErrorCategory = when (this) {
    is RestoreDiagnosticException -> cause?.restoreCategory() ?: RestoreErrorCategory.INTEGRITY
    is FirebaseNetworkException -> RestoreErrorCategory.NETWORK
    is FirebaseFirestoreException -> when (code) {
        FirebaseFirestoreException.Code.UNAUTHENTICATED -> RestoreErrorCategory.AUTHENTICATION
        FirebaseFirestoreException.Code.PERMISSION_DENIED -> RestoreErrorCategory.PERMISSION
        FirebaseFirestoreException.Code.UNAVAILABLE, FirebaseFirestoreException.Code.DEADLINE_EXCEEDED -> RestoreErrorCategory.NETWORK
        else -> RestoreErrorCategory.INTEGRITY
    }
    else -> when {
        message == "backup_updating" || message == "generation_changed" -> RestoreErrorCategory.BACKUP_UPDATING
        message == "unsupported_schema" -> RestoreErrorCategory.UNSUPPORTED
        else -> RestoreErrorCategory.INTEGRITY
    }
}
