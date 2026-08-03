package com.lazyapps.steparena.backup

import com.lazyapps.steparena.activity.DailyStepGoalRepository
import com.lazyapps.steparena.activity.UserProfileRepository
import com.lazyapps.steparena.core.database.StepArenaDatabase
import com.lazyapps.steparena.core.database.entity.*
import com.lazyapps.steparena.core.database.model.WalkingSessionStatus
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
        val full = !state.initialBackupCompleted
        val integrity = database.competitiveIntegritySegments().allForBackup().groupBy { it.localDate to it.zoneId }
        val allDaily = database.daily().all()
        val allHourly = database.hourly().all()
        val allSessions = database.sessions().completedForBackup()
        val allMatches = database.dailyMatches().finalizedForBackup()
        val allLeagues = database.weeklyLeagues().finalizedForBackup()
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
            daily.forEach { add(it.toBackup(integrity[it.localDate to it.zoneId].orEmpty())) }
            hourly.forEach { add(it.toBackup()) }
            sessions.forEach { add(it.toBackup()) }
            matches.forEach { add(it.toBackup()) }
            leagues.forEach { add(it.toBackup()) }
            seasons.forEach { add(it.toBackup()) }
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
                "challengeResults" to allMatches.size, "leagueHistory" to (allLeagues.size + allSeasons.size),
                "achievements" to allAchievements.size, "settings" to 1,
            ),
        )
    }
}

private fun DailyActivityRecordEntity.toBackup(segments: List<CompetitiveIntegritySegmentEntity>): BackupDocument {
    val total = segments.sumOf { it.totalSteps }
    val eligible = segments.sumOf { it.eligibleSteps }
    val restricted = segments.sumOf { it.restrictedSteps }
    val excluded = segments.sumOf { it.excludedSteps }
    return BackupDocument("daily", localDate, mapOf(
        "schemaVersion" to BACKUP_SCHEMA_VERSION, "localDate" to localDate, "zoneId" to zoneId,
        "steps" to steps, "externalRecoveredSteps" to externalRecoveredSteps,
        "unallocatedMeasuredSteps" to unallocatedMeasuredSteps, "distanceMeters" to distanceMeters?.finiteOrNull(),
        "walkingDurationSeconds" to walkingDurationSeconds, "caloriesKcal" to estimatedCaloriesKcal?.finiteOrNull(),
        "stepsQuality" to stepsQuality.name, "finalized" to finalized,
        "finalizedAtEpochMillis" to finalizedAtEpochMillis, "updatedAtEpochMillis" to updatedAtEpochMillis,
        "integrityTotal" to total, "integrityEligible" to eligible, "integrityRestricted" to restricted,
        "integrityExcluded" to excluded, "integrityReasons" to segments.flatMap { it.reasons.split(',') }.filter { it.isNotBlank() }.distinct(),
        "classifierVersion" to (segments.maxOfOrNull { it.classifierVersion } ?: 0),
    ))
}

private fun HourlyActivityRecordEntity.toBackup() = BackupDocument("hourly", "%s-%02d".format(Locale.US, localDate, hourOfDay), mapOf(
    "schemaVersion" to BACKUP_SCHEMA_VERSION, "localDate" to localDate, "hourOfDay" to hourOfDay,
    "zoneId" to zoneId, "utcOffsetSeconds" to utcOffsetSeconds, "periodStartEpochMillis" to periodStartEpochMillis,
    "periodEndEpochMillis" to periodEndEpochMillis, "steps" to steps, "distanceMeters" to distanceMeters?.finiteOrNull(),
    "walkingDurationSeconds" to walkingDurationSeconds, "caloriesKcal" to estimatedCaloriesKcal?.finiteOrNull(),
    "stepsQuality" to stepsQuality.name, "updatedAtEpochMillis" to updatedAtEpochMillis,
))

private fun WalkingSessionEntity.toBackup(): BackupDocument {
    require(status == WalkingSessionStatus.COMPLETED || status == WalkingSessionStatus.RECOVERED)
    return BackupDocument("sessions", stableBackupId("session", id, startedAtEpochMillis.toString()), mapOf(
        "schemaVersion" to BACKUP_SCHEMA_VERSION, "localDate" to localDate, "zoneId" to zoneId,
        "startedAtEpochMillis" to startedAtEpochMillis, "endedAtEpochMillis" to endedAtEpochMillis,
        "steps" to steps, "distanceMeters" to distanceMeters?.finiteOrNull(), "activeDurationSeconds" to activeDurationSeconds,
        "elapsedDurationSeconds" to elapsedDurationSeconds, "caloriesKcal" to estimatedCaloriesKcal?.finiteOrNull(),
        "sessionType" to sessionType.name, "status" to status.name, "stepsQuality" to stepsQuality.name,
        "updatedAtEpochMillis" to updatedAtEpochMillis,
    ))
}

private fun DailyMatchEntity.toBackup() = BackupDocument("challengeResults", stableBackupId("match", id, localDate), mapOf(
    "schemaVersion" to BACKUP_SCHEMA_VERSION, "localDate" to localDate, "zoneId" to zoneId,
    "seasonId" to stableBackupId("season", seasonId), "matchType" to matchType.name, "status" to status.name,
    "outcome" to outcome?.name, "opponentTargetSteps" to opponentTargetSteps, "totalSteps" to totalUserSteps,
    "eligibleSteps" to eligibleUserSteps, "restrictedSteps" to restrictedUserSteps, "excludedSteps" to excludedUserSteps,
    "restrictionReasons" to restrictionReasons, "classifierVersion" to 1, "ratingBefore" to ratingBefore,
    "ratingDelta" to ratingDelta, "ratingAfter" to ratingAfter, "finalizedAtEpochMillis" to finalizedAtEpochMillis,
))

private fun WeeklyLeagueEntity.toBackup() = BackupDocument("leagueHistory", stableBackupId("league", id, weekStartLocalDate), mapOf(
    "schemaVersion" to BACKUP_SCHEMA_VERSION, "historyType" to "WEEKLY_LEAGUE", "periodStart" to weekStartLocalDate,
    "periodEnd" to weekEndLocalDate, "zoneId" to zoneId, "status" to status.name, "points" to userPoints,
    "rank" to userRank, "finalizedAtEpochMillis" to finalizedAtEpochMillis,
))

private fun GameSeasonEntity.toBackup() = BackupDocument("leagueHistory", stableBackupId("season", id, startedAtEpochMillis.toString()), mapOf(
    "schemaVersion" to BACKUP_SCHEMA_VERSION, "historyType" to "SEASON", "startedAtEpochMillis" to startedAtEpochMillis,
    "endedAtEpochMillis" to endedAtEpochMillis, "status" to status.name, "startRating" to startRating,
    "endRating" to endRating, "wins" to wins, "losses" to losses, "draws" to draws,
    "totalEligibleSteps" to totalEligibleSteps, "bestWinStreak" to bestWinStreak,
))

private fun AchievementUnlockEntity.toBackup() = BackupDocument("achievements", stableBackupId("achievement", achievementId), mapOf(
    "schemaVersion" to BACKUP_SCHEMA_VERSION, "achievementKey" to achievementId,
    "unlockedAtEpochMillis" to unlockedAtEpochMillis, "progressValue" to progressValue,
    "seasonId" to seasonId?.let { stableBackupId("season", it) }, "acknowledged" to acknowledged,
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
}
}

private fun Double.finiteOrNull(): Double? = takeIf { it.isFinite() }
fun stableBackupId(vararg parts: String): String = MessageDigest.getInstance("SHA-256")
    .digest(parts.joinToString("|").toByteArray()).joinToString("") { "%02x".format(it) }.take(32)
