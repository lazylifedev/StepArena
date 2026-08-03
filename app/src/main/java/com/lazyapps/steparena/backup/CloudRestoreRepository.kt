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
            fail(error.restoreCategory())
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
            fail(category)
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
        val legacyRoot = firestore.collection("userBackups").document(uid)
        val versionedRoot = legacyRoot.collection("versions").document("v2")
        val versioned = versionedRoot.get(Source.SERVER).await()
        // An existing but incomplete/invalid v2 must fail validation; it never falls back to v1.
        val root = if (versioned.exists()) versionedRoot else legacyRoot
        val before = if (versioned.exists()) versioned else legacyRoot.get(Source.SERVER).await()
        val metadata = before.restoreMetadata()
        val achievements = root.collection("achievements").get(Source.SERVER).await().documents.map { it.toAchievement(metadata.schemaVersion) }
        val settingsDoc = root.collection("settings").document("current").get(Source.SERVER).await()
        val settings = settingsDoc.takeIf { it.exists() }?.toSettings(metadata.schemaVersion)
        val daily = if (metadata.schemaVersion == 2) root.collection("daily").get(Source.SERVER).await().documents.map { it.toDaily() } else emptyList()
        val hourly = if (metadata.schemaVersion == 2) root.collection("hourly").get(Source.SERVER).await().documents.map { it.toHourly() } else emptyList()
        val sessions = if (metadata.schemaVersion == 2) root.collection("sessions").get(Source.SERVER).await().documents.map { it.toSession() } else emptyList()
        val matches = if (metadata.schemaVersion == 2) root.collection("challengeResults").get(Source.SERVER).await().documents.map { it.toMatch() } else emptyList()
        val leagues = if (metadata.schemaVersion == 2) root.collection("leagueHistory").get(Source.SERVER).await().documents
            .filter { it.getLong("schemaVersion")?.toInt() == 2 }.map { it.toLeague() } else emptyList()
        val leagueParticipants = if (metadata.schemaVersion == 2) root.collection("leagueParticipants").get(Source.SERVER).await().documents.map { it.toLeagueParticipant(leagues) } else emptyList()
        val seasons = if (metadata.schemaVersion == 2) root.collection("seasonHistory").get(Source.SERVER).await().documents.map { it.toSeason() } else emptyList()
        val integrity = if (metadata.schemaVersion == 2) root.collection("integritySegments").get(Source.SERVER).await().documents.map { it.toIntegrity() } else emptyList()
        val after = root.get(Source.SERVER).await().restoreMetadata()
        require(metadata == after) { "generation_changed" }
        require(achievements.size == (metadata.counts["achievements"] ?: 0)) { "count_mismatch" }
        require((settings != null) == ((metadata.counts["settings"] ?: 0) > 0)) { "settings_count_mismatch" }
        if (metadata.schemaVersion == 2) {
            mapOf("daily" to daily.size, "hourly" to hourly.size, "sessions" to sessions.size,
                "challengeResults" to matches.size, "leagueHistory" to leagues.size, "leagueParticipants" to leagueParticipants.size, "seasonHistory" to seasons.size,
                "integritySegments" to integrity.size).forEach { (key, count) -> require(count == metadata.counts[key]) { "count_mismatch" } }
        }
        return RestoreSnapshot(metadata, achievements, settings, daily, hourly, sessions, matches, leagues, leagueParticipants, seasons, integrity)
    }

    private fun fail(category: RestoreErrorCategory) = RestoreState(RestoreStatus.FAILED, error = category).also { mutableState.value = it }
}

private fun DocumentSnapshot.restoreMetadata(): RestoreMetadata {
    require(exists()) { "backup_missing" }
    require(getString("backupStatus") == "complete") { "backup_updating" }
    val schema = getLong("schemaVersion")?.toInt() ?: error("metadata_invalid")
    require(schema in 1..BACKUP_SCHEMA_VERSION) { "unsupported_schema" }
    val generation = getLong("backupGeneration")?.takeIf { it > 0 } ?: error("metadata_invalid")
    val completed = getTimestamp("backupCompletedAt")?.toDate()?.toInstant() ?: error("metadata_invalid")
    val counts = mapOf(
        "daily" to count("dailyCount"), "hourly" to count("hourlyCount"), "sessions" to count("sessionCount"),
        "challengeResults" to count("challengeResultCount"), "leagueHistory" to count("leagueHistoryCount"),
        "achievements" to count("achievementCount"), "settings" to if (schema == 1) 1 else count("settingsCount"),
        "seasonHistory" to count("seasonHistoryCount"), "integritySegments" to count("integritySegmentCount"),
        "leagueParticipants" to count("leagueParticipantCount"),
    )
    return RestoreMetadata(generation, completed, schema, counts)
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
