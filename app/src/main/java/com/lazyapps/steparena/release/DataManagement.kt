package com.lazyapps.steparena.release

import android.content.Context
import android.net.Uri
import android.app.NotificationManager
import android.content.Intent
import androidx.work.WorkManager
import androidx.room.withTransaction
import com.lazyapps.steparena.BuildConfig
import com.lazyapps.steparena.core.database.StepArenaDatabase
import com.lazyapps.steparena.tracking.StepTrackingState
import com.lazyapps.steparena.tracking.TrackingStateRepository
import com.lazyapps.steparena.service.tracking.StepTrackingService
import com.lazyapps.steparena.activity.UserProfileRepository
import com.lazyapps.steparena.activity.DailyStepGoalRepository
import com.lazyapps.steparena.game.PlayerIdentityRepository
import com.lazyapps.steparena.recovery.RecoverySettingsRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.OutputStreamWriter
import java.time.Instant
import java.time.ZoneId
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

const val ONBOARDING_VERSION = 2
const val PRIVACY_POLICY_VERSION = 1

data class DataUsage(
    val hourly: Int,
    val daily: Int,
    val sessions: Int,
    val gaps: Int,
    val processedExternal: Int,
    val matches: Int,
    val leagues: Int,
    val seasons: Int,
    val achievements: Int,
    val notificationEvents: Int,
    val databaseBytes: Long?,
    val dataStoreBytes: Long?,
    val oldestDate: String?,
    val newestDate: String?,
)

data class ExportResult(val entryCount: Int, val exportedAt: Instant)

class DataManagementRepository(
    private val context: Context,
    private val database: StepArenaDatabase = StepArenaDatabase.get(context),
) {
    suspend fun usage(): DataUsage = withContext(Dispatchers.IO) {
        DataUsage(
            hourly = database.hourly().count(),
            daily = database.daily().count(),
            sessions = database.sessions().count(),
            gaps = database.trackingGaps().count(),
            processedExternal = database.processedExternalSteps().count(),
            matches = database.dailyMatches().count(),
            leagues = database.weeklyLeagues().count(),
            seasons = database.gameSeasons().count(),
            achievements = database.achievementUnlocks().count(),
            notificationEvents = database.gameNotificationEvents().count(),
            databaseBytes = runCatching {
                context.getDatabasePath(StepArenaDatabase.PRODUCTION_DATABASE_NAME).length()
            }.getOrNull(),
            dataStoreBytes = runCatching {
                context.dataDir.resolve("files/datastore").listFiles()?.sumOf { it.length() } ?: 0L
            }.getOrNull(),
            oldestDate = database.daily().oldestDate(),
            newestDate = database.daily().newestDate(),
        )
    }

    suspend fun resetGame() = database.withTransaction {
        database.gameNotificationEvents().deleteAll()
        database.achievementUnlocks().deleteAll()
        database.gameSeasons().deleteAll()
        database.weeklyLeagueParticipants().deleteAll()
        database.weeklyLeagues().deleteAll()
        database.dailyMatches().deleteAll()
        database.gamePlayerProfile().deleteAll()
    }

    suspend fun deleteAll() = withContext(Dispatchers.IO) {
        context.getSharedPreferences("phase6_deletion", 0).edit()
            .putBoolean("deletion_in_progress", true).commit()
        context.stopService(Intent(context, StepTrackingService::class.java))
        WorkManager.getInstance(context).cancelAllWork().result.get()
        context.getSystemService(NotificationManager::class.java).cancelAll()
        database.clearAllTables()
        TrackingStateRepository(context).clear()
        UserProfileRepository(context).reset()
        DailyStepGoalRepository(context).reset()
        RecoverySettingsRepository(context).reset()
        context.getSharedPreferences("game_notifications", 0).edit().clear().commit()
        context.cacheDir.resolve("exports").deleteRecursively()
        context.getSharedPreferences("phase6_deletion", 0).edit()
            .putBoolean("deletion_in_progress", false).commit()
    }

    suspend fun resetSettings(keepOnboarding: Boolean) {
        UserProfileRepository(context).reset()
        PlayerIdentityRepository(context, database).saveDisplayName("")
        DailyStepGoalRepository(context).reset()
        RecoverySettingsRepository(context).reset()
        context.getSharedPreferences("game_notifications", 0).edit().clear().commit()
        TrackingStateRepository(context).update {
            it.copy(
                batteryGuidanceAcknowledged = false,
                notificationGuidanceAcknowledged = false,
                onboardingComplete = if (keepOnboarding) it.onboardingComplete else false,
                onboardingStep = if (keepOnboarding) it.onboardingStep else 0,
            )
        }
    }

    suspend fun completeInterruptedDeletionIfNeeded() {
        if (context.getSharedPreferences("phase6_deletion", 0)
                .getBoolean("deletion_in_progress", false)
        ) deleteAll()
    }

    suspend fun export(uri: Uri): ExportResult = withContext(Dispatchers.IO) {
        val exportedAt = Instant.now()
        val snapshot = database.withTransaction {
            linkedMapOf(
                "activity_daily.csv" to queryCsv(
                    "SELECT localDate,zoneId,steps,distanceMeters,walkingDurationSeconds,estimatedCaloriesKcal,averageWalkingSpeedKmh,stepsQuality FROM daily_activity_records ORDER BY localDate",
                ),
                "activity_hourly.csv" to queryCsv(
                    "SELECT localDate,hourOfDay,zoneId,utcOffsetSeconds,steps,distanceMeters,walkingDurationSeconds,estimatedCaloriesKcal,averageWalkingSpeedKmh,stepsQuality FROM hourly_activity_records ORDER BY periodStartEpochMillis",
                ),
                "walking_sessions.csv" to queryCsv(
                    "SELECT localDate,zoneId,startedAtEpochMillis,endedAtEpochMillis,steps,status,sessionType,isManual FROM walking_sessions ORDER BY startedAtEpochMillis",
                ),
                "tracking_gaps.csv" to queryCsv(
                    "SELECT startedAtEpochMillis,endedAtEpochMillis,zoneId,reason,status,recoveredSteps,unresolvedSteps,quality FROM tracking_gap_records ORDER BY startedAtEpochMillis",
                ),
                "matches.csv" to queryCsv(
                    "SELECT localDate,zoneId,status,outcome,opponentName,opponentTargetSteps,totalUserSteps,eligibleUserSteps,restrictedUserSteps,competitiveQuality FROM daily_matches ORDER BY localDate",
                ),
                "leagues.csv" to queryCsv(
                    "SELECT weekStartLocalDate,weekEndLocalDate,zoneId,status,userPoints,userRank FROM weekly_leagues ORDER BY weekStartLocalDate",
                ),
                "seasons.csv" to queryCsv(
                    "SELECT startedAtEpochMillis,endedAtEpochMillis,status,wins,losses,draws,totalEligibleSteps,highestRankTier,highestRankDivision FROM game_seasons ORDER BY startedAtEpochMillis",
                ),
                "achievements.csv" to queryCsv(
                    "SELECT achievementId,unlockedAtEpochMillis,progressValue,seasonId,acknowledged FROM achievement_unlocks ORDER BY unlockedAtEpochMillis",
                ),
            )
        }
        val usage = usage()
        val dailyStepGoal = DailyStepGoalRepository(context).current()
        val output = requireNotNull(context.contentResolver.openOutputStream(uri)) {
            "EXPORT-OPEN-01"
        }
        ZipOutputStream(output.buffered()).use { zip ->
            snapshot.forEach { (name, csv) -> write(zip, name, csv) }
            write(
                zip,
                "settings.json",
                """{"dailyStepGoal":$dailyStepGoal,"gameNotifications":false,"healthConnectEnabled":false}""",
            )
            write(zip, "metadata.json", metadata(exportedAt, usage))
            write(zip, "README.txt", "StepArena local data export\nUTF-8 / ISO 8601 / RFC 4180 compatible CSV\nNo account or server sync is used.\n")
        }
        ExportResult(11, exportedAt)
    }

    private fun queryCsv(sql: String): String {
        val cursor = database.openHelper.readableDatabase.query(sql)
        return cursor.use {
            buildString {
                append(it.columnNames.joinToString(",") { name -> csvCell(name) }).append("\r\n")
                while (it.moveToNext()) {
                    append((0 until it.columnCount).joinToString(",") { index ->
                        val value = when (it.getType(index)) {
                            android.database.Cursor.FIELD_TYPE_NULL -> ""
                            android.database.Cursor.FIELD_TYPE_BLOB -> "[binary omitted]"
                            else -> it.getString(index)
                        }
                        csvCell(value)
                    }).append("\r\n")
                }
            }
        }
    }

    private fun metadata(at: Instant, u: DataUsage) =
        """{"exportedAt":"$at","appVersion":"${BuildConfig.VERSION_NAME}","databaseVersion":7,"locale":"${java.util.Locale.getDefault()}","zoneId":"${ZoneId.systemDefault()}","recordCounts":{"hourly":${u.hourly},"daily":${u.daily},"sessions":${u.sessions},"gaps":${u.gaps},"matches":${u.matches}},"dateRange":{"oldest":${json(u.oldestDate)},"newest":${json(u.newestDate)}},"healthConnectEnabled":false,"accountUsed":false,"serverSyncUsed":false}"""

    private fun json(value: String?) = value?.let { "\"${escapeJson(it)}\"" } ?: "null"

    private fun write(zip: ZipOutputStream, name: String, value: String) {
        zip.putNextEntry(ZipEntry(name))
        OutputStreamWriter(zip, Charsets.UTF_8).apply {
            write(value)
            flush()
        }
        zip.closeEntry()
    }
}

fun csvCell(value: String): String =
    if (value.any { it == ',' || it == '"' || it == '\r' || it == '\n' })
        "\"${value.replace("\"", "\"\"")}\"" else value

fun escapeJson(value: String): String = buildString {
    value.forEach {
        when (it) {
            '\\' -> append("\\\\")
            '"' -> append("\\\"")
            '\n' -> append("\\n")
            '\r' -> append("\\r")
            '\t' -> append("\\t")
            else -> append(it)
        }
    }
}

fun safeDiagnosticLines(values: Map<String, String?>, unsetValue: String = "unset"): String =
    values.filterKeys {
        it.lowercase() !in setOf("height", "weight", "steps", "serial", "androidid", "recordid", "path")
    }.entries.joinToString("\n") { "${it.key}: ${it.value ?: unsetValue}" }
