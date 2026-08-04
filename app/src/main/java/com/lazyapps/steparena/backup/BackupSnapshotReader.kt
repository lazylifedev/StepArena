package com.lazyapps.steparena.backup

import com.lazyapps.steparena.activity.DailyStepGoalRepository
import com.lazyapps.steparena.activity.UserProfileRepository
import com.lazyapps.steparena.core.database.StepArenaDatabase
import com.lazyapps.steparena.core.database.entity.*
import com.lazyapps.steparena.core.database.model.WalkingSessionStatus
import com.lazyapps.steparena.core.database.model.WalkingSessionType
import com.lazyapps.steparena.core.database.model.DataQuality
import java.security.MessageDigest
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.util.Locale

class BackupSnapshotReader(
    private val database: StepArenaDatabase,
    private val profileRepository: UserProfileRepository,
    private val goalRepository: DailyStepGoalRepository,
    private val clock: Clock,
) {
    suspend fun read(state: BackupState): BackupSnapshot {
        val zone = clock.zone
        val today = LocalDate.now(clock)
        val overlapStart = (state.lastSuccessfulBackupAt ?: Instant.EPOCH).minusSeconds(24 * 60 * 60L).toEpochMilli()
        // A v2 generation must rewrite every restorable row. A previous successful v1
        // generation may have left documents that do not contain the Room-required columns.
        val full = true
        val integrity = database.competitiveIntegritySegments().allForBackup().groupBy { it.localDate to it.zoneId }
        val allDaily = database.daily().all()
        val allHourly = database.hourly().all()
        val allSessions = database.sessions().completedForBackup()
        val allMatches = database.dailyMatches().finalizedForBackup()
        val allLeagues = database.weeklyLeagues().finalizedForBackup()
        val finalizedLeagueIds = allLeagues.map { it.id }.toSet()
        val allLeagueParticipants = database.weeklyLeagueParticipants().allForBackup().filter { it.leagueId in finalizedLeagueIds }
        val allSeasons = database.gameSeasons().finalizedForBackup()
        val allAchievements = database.achievementUnlocks().allForBackup()
        val daily = allDaily.filter { full || LocalDate.parse(it.localDate) >= today.minusDays(2) }
        val hourly = allHourly.filter { full || LocalDate.parse(it.localDate) >= today.minusDays(2) }
        val sessions = allSessions.filter { full || (it.endedAtEpochMillis ?: 0) >= overlapStart }
        val matches = allMatches.filter { full || (it.finalizedAtEpochMillis ?: 0) >= overlapStart }
        val leagues = allLeagues.filter { full || (it.finalizedAtEpochMillis ?: 0) >= overlapStart }
        val seasons = allSeasons.filter { full || it.endedAtEpochMillis >= overlapStart }
        val achievements = allAchievements.filter { full || it.unlockedAtEpochMillis >= overlapStart }
        val documents = buildList {
            daily.forEach { add(it.toBackup()) }
            hourly.forEach { add(it.toBackup()) }
            sessions.forEach { add(it.toBackup()) }
            matches.forEach { add(it.toBackup()) }
            leagues.forEach { add(it.toBackup()) }
            val leagueStarts = allLeagues.associate { it.id to it.weekStartLocalDate }
            allLeagueParticipants.forEach { add(it.toBackup(leagueStarts.getValue(it.leagueId))) }
            seasons.forEach { add(it.toBackup()) }
            integrity.values.flatten().forEach { add(it.toBackup()) }
            achievements.forEach { add(it.toBackup()) }
            val profile = profileRepository.current()
            add(BackupDocument("settings", "current", mapOf(
                "schemaVersion" to BACKUP_SCHEMA_VERSION,
                "heightCm" to profile.heightCm?.finiteOrNull(),
                "weightKg" to profile.weightKg?.finiteOrNull(),
                "manualStepLengthMeters" to profile.manualStepLengthMeters?.finiteOrNull(),
                "useAutomaticStepLength" to profile.useAutomaticStepLength,
                "dailyStepGoal" to goalRepository.current(),
            )))
        }
        require(documents.map { it.collection to it.id }.distinct().size == documents.size) { "duplicate_document_id" }
        documents.forEach(BackupDocumentValidator::validate)
        return BackupSnapshot(
            documents = documents,
            localTimeZone = zone.id,
            newestLocalDate = daily.maxOfOrNull { it.localDate },
            counts = mapOf(
                "daily" to allDaily.size, "hourly" to allHourly.size, "sessions" to allSessions.size,
                "challengeResults" to allMatches.size, "leagueHistory" to allLeagues.size,
                "leagueParticipants" to allLeagueParticipants.size,
                "seasonHistory" to allSeasons.size, "integritySegments" to integrity.values.sumOf { it.size },
                "achievements" to allAchievements.size, "settings" to 1,
            ),
        )
    }
}

private fun DailyActivityRecordEntity.toBackup(): BackupDocument {
    val stableId = stableBackupId("daily", localDate, zoneId)
    return BackupDocument("daily", localDate, mapOf(
        "schemaVersion" to BACKUP_SCHEMA_VERSION, "stableId" to stableId, "roomId" to id,
        "localDate" to localDate, "zoneId" to zoneId, "steps" to steps,
        "unclassifiedSteps" to unclassifiedSteps, "unclassifiedStepsQuality" to unclassifiedStepsQuality.name,
        "externalRecoveredSteps" to externalRecoveredSteps,
        "unallocatedMeasuredSteps" to unallocatedMeasuredSteps, "distanceMeters" to distanceMeters?.finiteOrNull(),
        "walkingDurationSeconds" to walkingDurationSeconds, "caloriesKcal" to estimatedCaloriesKcal?.finiteOrNull(),
        "averageWalkingSpeedKmh" to averageWalkingSpeedKmh?.finiteOrNull(), "stepsQuality" to stepsQuality.name,
        "distanceQuality" to distanceQuality.name, "durationQuality" to durationQuality.name,
        "caloriesQuality" to caloriesQuality.name, "speedQuality" to speedQuality.name,
        "activeHourCount" to activeHourCount, "walkingSessionCount" to walkingSessionCount,
        "finalized" to finalized, "finalizedAtEpochMillis" to finalizedAtEpochMillis,
        "createdAtEpochMillis" to createdAtEpochMillis, "updatedAtEpochMillis" to updatedAtEpochMillis,
    ))
}

private fun HourlyActivityRecordEntity.toBackup() = BackupDocument("hourly", "%s-%02d".format(Locale.US, localDate, hourOfDay), mapOf(
    "schemaVersion" to BACKUP_SCHEMA_VERSION,
    "stableId" to stableBackupId("hourly", localDate, hourOfDay.toString(), zoneId, utcOffsetSeconds.toString()),
    "roomId" to id, "localDate" to localDate, "hourOfDay" to hourOfDay,
    "zoneId" to zoneId, "utcOffsetSeconds" to utcOffsetSeconds, "periodStartEpochMillis" to periodStartEpochMillis,
    "periodEndEpochMillis" to periodEndEpochMillis, "steps" to steps, "distanceMeters" to distanceMeters?.finiteOrNull(),
    "walkingDurationSeconds" to walkingDurationSeconds, "caloriesKcal" to estimatedCaloriesKcal?.finiteOrNull(),
    "averageWalkingSpeedKmh" to averageWalkingSpeedKmh?.finiteOrNull(), "stepsQuality" to stepsQuality.name,
    "distanceQuality" to distanceQuality.name, "durationQuality" to durationQuality.name,
    "caloriesQuality" to caloriesQuality.name, "speedQuality" to speedQuality.name,
    "firstActivityAtEpochMillis" to firstActivityAtEpochMillis, "lastActivityAtEpochMillis" to lastActivityAtEpochMillis,
    "sensorEventCount" to sensorEventCount, "recoveredSteps" to recoveredSteps, "estimatedSteps" to estimatedSteps,
    "appliedStepLengthMeters" to appliedStepLengthMeters, "appliedWeightKg" to appliedWeightKg,
    "calorieFormulaVersion" to calorieFormulaVersion, "createdAtEpochMillis" to createdAtEpochMillis,
    "updatedAtEpochMillis" to updatedAtEpochMillis,
))

private fun WalkingSessionEntity.toBackup(): BackupDocument {
    require(status == WalkingSessionStatus.COMPLETED || status == WalkingSessionStatus.RECOVERED)
    val stableId = stableBackupId("session", id, startedAtEpochMillis.toString())
    return BackupDocument("sessions", stableId, mapOf(
        "schemaVersion" to BACKUP_SCHEMA_VERSION, "stableId" to stableId, "roomId" to id,
        "localDate" to localDate, "zoneId" to zoneId,
        "startedAtEpochMillis" to startedAtEpochMillis, "endedAtEpochMillis" to endedAtEpochMillis,
        "steps" to steps, "distanceMeters" to distanceMeters?.finiteOrNull(), "activeDurationSeconds" to activeDurationSeconds,
        "elapsedDurationSeconds" to elapsedDurationSeconds, "caloriesKcal" to estimatedCaloriesKcal?.finiteOrNull(),
        "pausedDurationSeconds" to pausedDurationSeconds, "averageMovingSpeedKmh" to averageMovingSpeedKmh?.finiteOrNull(),
        "averageElapsedSpeedKmh" to averageElapsedSpeedKmh?.finiteOrNull(), "sessionType" to sessionType.name,
        "status" to status.name, "stepsQuality" to stepsQuality.name, "distanceQuality" to distanceQuality.name,
        "durationQuality" to durationQuality.name, "caloriesQuality" to caloriesQuality.name,
        "speedQuality" to speedQuality.name, "trackingServiceSessionId" to trackingServiceSessionId,
        "lastWalkingEventAtEpochMillis" to lastWalkingEventAtEpochMillis, "pausedSinceEpochMillis" to pausedSinceEpochMillis,
        "isManual" to isManual, "detectorEventCount" to detectorEventCount,
        "estimatedStepCount" to estimatedStepCount, "recoveredStepCount" to recoveredStepCount,
        "createdAtEpochMillis" to createdAtEpochMillis, "updatedAtEpochMillis" to updatedAtEpochMillis,
    ))
}

private fun DailyMatchEntity.toBackup(): BackupDocument {
    val stableId = stableBackupId("match", id, localDate)
    return BackupDocument("challengeResults", stableId, mapOf(
    "schemaVersion" to BACKUP_SCHEMA_VERSION, "stableId" to stableId, "roomId" to id,
    "localDate" to localDate, "zoneId" to zoneId,
    "seasonId" to seasonId, "seasonStableId" to stableBackupId("season", seasonId),
    "matchType" to matchType.name, "status" to status.name,
    "outcome" to outcome?.name, "opponentTargetSteps" to opponentTargetSteps, "totalSteps" to totalUserSteps,
    "eligibleSteps" to eligibleUserSteps, "restrictedSteps" to restrictedUserSteps, "excludedSteps" to excludedUserSteps,
    "opponentId" to opponentId, "opponentName" to opponentName, "opponentAvatarKey" to opponentAvatarKey,
    "opponentRankTier" to opponentRankTier.name, "opponentRankDivision" to opponentRankDivision,
    "opponentPersonality" to opponentPersonality.name, "restrictionReasons" to restrictionReasons,
    "competitiveQuality" to competitiveQuality.name, "ratingBefore" to ratingBefore,
    "ratingDelta" to ratingDelta, "ratingAfter" to ratingAfter, "finalizedAtEpochMillis" to finalizedAtEpochMillis,
    "ratingBreakdown" to ratingBreakdown, "createdAtEpochMillis" to createdAtEpochMillis,
    "updatedAtEpochMillis" to updatedAtEpochMillis,
)) }

private fun WeeklyLeagueEntity.toBackup(): BackupDocument {
    val stableId = stableBackupId("league", id, weekStartLocalDate)
    return BackupDocument("leagueHistory", stableId, mapOf(
    "schemaVersion" to BACKUP_SCHEMA_VERSION, "stableId" to stableId, "roomId" to id,
    "periodStart" to weekStartLocalDate,
    "periodEnd" to weekEndLocalDate, "zoneId" to zoneId, "status" to status.name, "points" to userPoints,
    "rank" to userRank, "participantsJson" to participantsJson, "finalizedAtEpochMillis" to finalizedAtEpochMillis,
    "createdAtEpochMillis" to createdAtEpochMillis, "updatedAtEpochMillis" to updatedAtEpochMillis,
)) }

private fun WeeklyLeagueParticipantEntity.toBackup(leagueStart: String): BackupDocument {
    val stableId = stableBackupId("league-participant", leagueId, participantId)
    return BackupDocument("leagueParticipants", stableId, mapOf(
        "schemaVersion" to BACKUP_SCHEMA_VERSION, "stableId" to stableId,
        "leagueRoomId" to leagueId, "leagueStableId" to stableBackupId("league", leagueId, leagueStart),
        "participantId" to participantId, "displayName" to displayName, "avatarKey" to avatarKey,
        "points" to points, "eligibleSteps" to eligibleSteps, "rank" to rank,
        "isLocalPlayer" to isLocalPlayer, "generatedLocally" to generatedLocally,
        "createdAtEpochMillis" to createdAtEpochMillis, "updatedAtEpochMillis" to updatedAtEpochMillis,
    ))
}

private fun GameSeasonEntity.toBackup(): BackupDocument {
    val stableId = stableBackupId("season", id, startedAtEpochMillis.toString())
    return BackupDocument("seasonHistory", stableId, mapOf(
    "schemaVersion" to BACKUP_SCHEMA_VERSION, "stableId" to stableId, "roomId" to id,
    "startedAtEpochMillis" to startedAtEpochMillis,
    "endedAtEpochMillis" to endedAtEpochMillis, "status" to status.name, "startRating" to startRating,
    "endRating" to endRating, "wins" to wins, "losses" to losses, "draws" to draws,
    "highestRankTier" to highestRankTier.name, "highestRankDivision" to highestRankDivision,
    "totalEligibleSteps" to totalEligibleSteps, "bestWinStreak" to bestWinStreak,
    "rewardClaimed" to rewardClaimed, "createdAtEpochMillis" to createdAtEpochMillis,
    "updatedAtEpochMillis" to updatedAtEpochMillis,
)) }

private fun CompetitiveIntegritySegmentEntity.toBackup(): BackupDocument {
    val stableId = stableBackupId("integrity", id, startedAtEpochMillis.toString())
    return BackupDocument("integritySegments", stableId, mapOf(
        "schemaVersion" to BACKUP_SCHEMA_VERSION, "stableId" to stableId, "roomId" to id,
        "localDate" to localDate, "zoneId" to zoneId, "startedAtEpochMillis" to startedAtEpochMillis,
        "endedAtEpochMillis" to endedAtEpochMillis, "totalSteps" to totalSteps,
        "eligibleSteps" to eligibleSteps, "restrictedSteps" to restrictedSteps, "excludedSteps" to excludedSteps,
        "assessment" to assessment.name, "reasons" to reasons, "classifierVersion" to classifierVersion,
        "createdAtEpochMillis" to createdAtEpochMillis,
    ))
}

private fun AchievementUnlockEntity.toBackup() = BackupDocument("achievements", stableBackupId("achievement", achievementId), mapOf(
    "schemaVersion" to BACKUP_SCHEMA_VERSION, "stableId" to stableBackupId("achievement", achievementId),
    "achievementKey" to achievementId,
    "unlockedAtEpochMillis" to unlockedAtEpochMillis, "progressValue" to progressValue,
    "seasonId" to seasonId, "seasonStableId" to seasonId?.let { stableBackupId("season", it) },
    "acknowledged" to acknowledged,
))

object BackupDocumentValidator {
fun validate(document: BackupDocument) {
    require(document.id.isNotBlank() && '/' !in document.id)
    require((document.fields["schemaVersion"] as? Number)?.toInt() == BACKUP_SCHEMA_VERSION)
    listOf("steps", "distanceMeters", "walkingDurationSeconds", "caloriesKcal", "totalSteps", "eligibleSteps",
        "restrictedSteps", "excludedSteps", "integrityTotal", "integrityEligible", "integrityRestricted", "integrityExcluded")
        .forEach { key -> (document.fields[key] as? Number)?.let { require(it.toDouble().isFinite() && it.toDouble() >= 0) } }
    val total = (document.fields["totalSteps"] as? Number)?.toLong()
    if (total != null) require(total == (document.fields["eligibleSteps"] as Number).toLong() +
        (document.fields["restrictedSteps"] as Number).toLong() + (document.fields["excludedSteps"] as Number).toLong())
    val integrityTotal = (document.fields["integrityTotal"] as? Number)?.toLong()
    if (integrityTotal != null) require(integrityTotal == (document.fields["integrityEligible"] as Number).toLong() +
        (document.fields["integrityRestricted"] as Number).toLong() + (document.fields["integrityExcluded"] as Number).toLong())
    val start = (document.fields["startedAtEpochMillis"] as? Number)?.toLong()
    val end = (document.fields["endedAtEpochMillis"] as? Number)?.toLong()
    if (start != null && end != null) require(end >= start)
    document.fields.values.filterIsInstance<Double>().forEach { require(it.isFinite()) }
    val required = when (document.collection) {
        "daily" -> listOf("stableId","roomId","localDate","zoneId","unclassifiedSteps","unclassifiedStepsQuality","distanceQuality","durationQuality","caloriesQuality","speedQuality","activeHourCount","walkingSessionCount","createdAtEpochMillis")
        "hourly" -> listOf("stableId","roomId","utcOffsetSeconds","periodStartEpochMillis","periodEndEpochMillis","distanceQuality","durationQuality","caloriesQuality","speedQuality","sensorEventCount","recoveredSteps","estimatedSteps","appliedStepLengthMeters","appliedWeightKg","calorieFormulaVersion","createdAtEpochMillis")
        "sessions" -> listOf("stableId","roomId","pausedDurationSeconds","distanceQuality","durationQuality","caloriesQuality","speedQuality","isManual","detectorEventCount","estimatedStepCount","recoveredStepCount","createdAtEpochMillis")
        "challengeResults" -> listOf("stableId","roomId","opponentId","opponentName","opponentAvatarKey","opponentRankTier","opponentPersonality","competitiveQuality","createdAtEpochMillis","updatedAtEpochMillis")
        "leagueHistory" -> listOf("stableId","roomId","participantsJson","createdAtEpochMillis","updatedAtEpochMillis")
        "leagueParticipants" -> listOf("stableId","leagueRoomId","leagueStableId","participantId","displayName","avatarKey","points","eligibleSteps","rank","isLocalPlayer","generatedLocally","createdAtEpochMillis","updatedAtEpochMillis")
        "seasonHistory" -> listOf("stableId","roomId","highestRankTier","rewardClaimed","createdAtEpochMillis","updatedAtEpochMillis")
        "integritySegments" -> listOf("stableId","roomId","assessment","reasons","classifierVersion","createdAtEpochMillis")
        "achievements" -> listOf("stableId","achievementKey","unlockedAtEpochMillis","progressValue","acknowledged")
        "settings" -> listOf("useAutomaticStepLength","dailyStepGoal")
        else -> error("unknown_collection")
    }
    require(required.all(document.fields::containsKey)) { "missing_required_field" }
    (document.fields["stableId"] as? String)?.let { stable ->
        require(stable.matches(Regex("[0-9a-f]{32}")))
        if (document.collection !in setOf("daily", "hourly")) require(stable == document.id)
    }
    listOf("stepsQuality","unclassifiedStepsQuality","distanceQuality","durationQuality","caloriesQuality","speedQuality")
        .forEach { key -> (document.fields[key] as? String)?.let { enumValueOf<DataQuality>(it) } }
    if (document.collection == "sessions") {
        require((document.fields["status"] as? String) in setOf("COMPLETED", "RECOVERED"))
        enumValueOf<WalkingSessionType>(document.fields["sessionType"] as String)
    }
}
}

private fun Double.finiteOrNull(): Double? = takeIf { it.isFinite() }
fun stableBackupId(vararg parts: String): String = MessageDigest.getInstance("SHA-256")
    .digest(parts.joinToString("|").toByteArray()).joinToString("") { "%02x".format(it) }.take(32)
