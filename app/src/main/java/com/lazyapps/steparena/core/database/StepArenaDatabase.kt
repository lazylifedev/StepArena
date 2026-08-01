package com.lazyapps.steparena.core.database

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.lazyapps.steparena.core.database.converter.ActivityConverters
import com.lazyapps.steparena.core.database.dao.ActivityProcessingStateDao
import com.lazyapps.steparena.core.database.dao.DailyActivityDao
import com.lazyapps.steparena.core.database.dao.HourlyActivityDao
import com.lazyapps.steparena.core.database.dao.WalkingSessionDao
import com.lazyapps.steparena.core.database.dao.TrackingGapDao
import com.lazyapps.steparena.core.database.dao.ProcessedExternalStepRecordDao
import com.lazyapps.steparena.core.database.entity.ActivityProcessingStateEntity
import com.lazyapps.steparena.core.database.entity.DailyActivityRecordEntity
import com.lazyapps.steparena.core.database.entity.HourlyActivityRecordEntity
import com.lazyapps.steparena.core.database.entity.WalkingSessionEntity
import com.lazyapps.steparena.core.database.entity.TrackingGapRecordEntity
import com.lazyapps.steparena.core.database.entity.ProcessedExternalStepRecordEntity
import com.lazyapps.steparena.core.database.entity.*
import com.lazyapps.steparena.core.database.dao.*
import java.time.Instant
import java.time.LocalDate
import java.time.Duration
import java.time.ZoneId
import java.math.BigInteger

@Database(
    entities = [
        HourlyActivityRecordEntity::class,
        DailyActivityRecordEntity::class,
        WalkingSessionEntity::class,
        ActivityProcessingStateEntity::class,
        TrackingGapRecordEntity::class,
        ProcessedExternalStepRecordEntity::class,
        GamePlayerProfileEntity::class,
        DailyMatchEntity::class,
        WeeklyLeagueEntity::class,
        WeeklyLeagueParticipantEntity::class,
        GameSeasonEntity::class,
        AchievementUnlockEntity::class,
        GameNotificationEventEntity::class,
        CompetitiveIntegritySegmentEntity::class,
    ],
    version = 10,
    exportSchema = true,
)
@TypeConverters(ActivityConverters::class)
abstract class StepArenaDatabase : RoomDatabase() {
    abstract fun hourly(): HourlyActivityDao
    abstract fun daily(): DailyActivityDao
    abstract fun sessions(): WalkingSessionDao
    abstract fun processingState(): ActivityProcessingStateDao
    abstract fun trackingGaps(): TrackingGapDao
    abstract fun processedExternalSteps(): ProcessedExternalStepRecordDao
    abstract fun gamePlayerProfile(): GamePlayerProfileDao
    abstract fun dailyMatches(): DailyMatchDao
    abstract fun weeklyLeagues(): WeeklyLeagueDao
    abstract fun weeklyLeagueParticipants(): WeeklyLeagueParticipantDao
    abstract fun gameSeasons(): GameSeasonDao
    abstract fun achievementUnlocks(): AchievementUnlockDao
    abstract fun gameNotificationEvents(): GameNotificationEventDao
    abstract fun competitiveIntegritySegments(): CompetitiveIntegritySegmentDao

    companion object {
        const val PRODUCTION_DATABASE_NAME = "step_arena.db"
        @Volatile private var instance: StepArenaDatabase? = null
        fun get(context: Context): StepArenaDatabase = instance ?: synchronized(this) {
            instance ?: build(context, PRODUCTION_DATABASE_NAME).also { instance = it }
        }

        fun build(context: Context, name: String): StepArenaDatabase =
            Room.databaseBuilder(context.applicationContext, StepArenaDatabase::class.java, name)
                .addMigrations(
                    MIGRATION_1_2,
                    MIGRATION_2_3,
                    MIGRATION_3_4,
                    MIGRATION_4_5,
                    MIGRATION_5_6,
                    MIGRATION_6_7,
                    MIGRATION_7_8,
                    MIGRATION_8_9,
                    MIGRATION_9_10,
                )
                .build()

        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE hourly_activity_records ADD COLUMN appliedStepLengthMeters REAL NOT NULL DEFAULT 0.7")
                db.execSQL("ALTER TABLE hourly_activity_records ADD COLUMN appliedWeightKg REAL NOT NULL DEFAULT 60.0")
                db.execSQL("ALTER TABLE hourly_activity_records ADD COLUMN calorieFormulaVersion INTEGER NOT NULL DEFAULT 1")
                db.execSQL("ALTER TABLE daily_activity_records ADD COLUMN unclassifiedStepsQuality TEXT NOT NULL DEFAULT 'UNKNOWN'")
                db.execSQL("UPDATE daily_activity_records SET unclassifiedStepsQuality = 'RECOVERED' WHERE unclassifiedSteps > 0")
                db.execSQL("ALTER TABLE walking_sessions ADD COLUMN lastWalkingEventAtEpochMillis INTEGER")
                db.execSQL("ALTER TABLE walking_sessions ADD COLUMN pausedSinceEpochMillis INTEGER")
                db.execSQL("ALTER TABLE walking_sessions ADD COLUMN isManual INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE walking_sessions ADD COLUMN detectorEventCount INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE walking_sessions ADD COLUMN estimatedStepCount INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE walking_sessions ADD COLUMN recoveredStepCount INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE activity_processing_state ADD COLUMN activeAutoSessionId TEXT")
                db.execSQL("ALTER TABLE activity_processing_state ADD COLUMN activeManualSessionId TEXT")
                db.execSQL("ALTER TABLE activity_processing_state ADD COLUMN lastDetectorEventEpochMillis INTEGER")
                db.execSQL("ALTER TABLE activity_processing_state ADD COLUMN lastWalkingEventEpochMillis INTEGER")
            }
        }

        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """CREATE TABLE IF NOT EXISTS `tracking_gap_records` (
                        `id` TEXT NOT NULL, `startedAtEpochMillis` INTEGER NOT NULL,
                        `endedAtEpochMillis` INTEGER NOT NULL, `zoneId` TEXT NOT NULL,
                        `reason` TEXT NOT NULL, `status` TEXT NOT NULL,
                        `expectedTracking` INTEGER NOT NULL, `explicitUserStop` INTEGER NOT NULL,
                        `recoveredSteps` INTEGER NOT NULL, `unresolvedSteps` INTEGER NOT NULL,
                        `recoverySource` TEXT, `quality` TEXT NOT NULL,
                        `externalRecordCount` INTEGER NOT NULL, `externalOriginsJson` TEXT,
                        `fingerprint` TEXT NOT NULL, `detectedAtEpochMillis` INTEGER NOT NULL,
                        `recoveredAtEpochMillis` INTEGER, `reviewedAtEpochMillis` INTEGER,
                        `createdAtEpochMillis` INTEGER NOT NULL, `updatedAtEpochMillis` INTEGER NOT NULL,
                        PRIMARY KEY(`id`))""",
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_tracking_gap_records_startedAtEpochMillis` ON `tracking_gap_records` (`startedAtEpochMillis`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_tracking_gap_records_endedAtEpochMillis` ON `tracking_gap_records` (`endedAtEpochMillis`)")
                db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_tracking_gap_records_fingerprint` ON `tracking_gap_records` (`fingerprint`)")
                db.execSQL(
                    """CREATE TABLE IF NOT EXISTS `processed_external_step_records` (
                        `id` TEXT NOT NULL, `recordId` TEXT, `dataOriginPackage` TEXT NOT NULL,
                        `startedAtEpochMillis` INTEGER NOT NULL, `endedAtEpochMillis` INTEGER NOT NULL,
                        `steps` INTEGER NOT NULL, `lastModifiedAtEpochMillis` INTEGER,
                        `fingerprint` TEXT NOT NULL, `processedAtEpochMillis` INTEGER NOT NULL,
                        `appliedSteps` INTEGER NOT NULL, `gapId` TEXT NOT NULL,
                        PRIMARY KEY(`id`))""",
                )
                db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_processed_external_step_records_fingerprint` ON `processed_external_step_records` (`fingerprint`)")
                db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_processed_external_step_records_recordId_dataOriginPackage` ON `processed_external_step_records` (`recordId`, `dataOriginPackage`)")
            }
        }

        val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("""CREATE TABLE IF NOT EXISTS `game_player_profile` (`id` TEXT NOT NULL, `rating` INTEGER NOT NULL, `rankTier` TEXT NOT NULL, `rankDivision` INTEGER, `totalMatches` INTEGER NOT NULL, `wins` INTEGER NOT NULL, `losses` INTEGER NOT NULL, `draws` INTEGER NOT NULL, `noContests` INTEGER NOT NULL, `currentWinStreak` INTEGER NOT NULL, `bestWinStreak` INTEGER NOT NULL, `currentLossStreak` INTEGER NOT NULL, `beginnerMatchesRemaining` INTEGER NOT NULL, `lastOutcome` TEXT, `createdAtEpochMillis` INTEGER NOT NULL, `updatedAtEpochMillis` INTEGER NOT NULL, PRIMARY KEY(`id`))""")
                db.execSQL("""CREATE TABLE IF NOT EXISTS `daily_matches` (`id` TEXT NOT NULL, `localDate` TEXT NOT NULL, `zoneId` TEXT NOT NULL, `seasonId` TEXT NOT NULL, `matchType` TEXT NOT NULL, `status` TEXT NOT NULL, `outcome` TEXT, `opponentId` TEXT NOT NULL, `opponentName` TEXT NOT NULL, `opponentAvatarKey` TEXT NOT NULL, `opponentRankTier` TEXT NOT NULL, `opponentRankDivision` INTEGER, `opponentPersonality` TEXT NOT NULL, `opponentTargetSteps` INTEGER NOT NULL, `totalUserSteps` INTEGER NOT NULL, `eligibleUserSteps` INTEGER NOT NULL, `restrictedUserSteps` INTEGER NOT NULL, `excludedUserSteps` INTEGER NOT NULL, `restrictionReasons` TEXT NOT NULL, `competitiveQuality` TEXT NOT NULL, `ratingBefore` INTEGER NOT NULL, `ratingDelta` INTEGER, `ratingAfter` INTEGER, `ratingBreakdown` TEXT, `finalizedAtEpochMillis` INTEGER, `createdAtEpochMillis` INTEGER NOT NULL, `updatedAtEpochMillis` INTEGER NOT NULL, PRIMARY KEY(`id`))""")
                db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_daily_matches_localDate_zoneId_matchType` ON `daily_matches` (`localDate`, `zoneId`, `matchType`)")
                db.execSQL("""CREATE TABLE IF NOT EXISTS `weekly_leagues` (`id` TEXT NOT NULL, `weekStartLocalDate` TEXT NOT NULL, `weekEndLocalDate` TEXT NOT NULL, `zoneId` TEXT NOT NULL, `status` TEXT NOT NULL, `userPoints` INTEGER NOT NULL, `userRank` INTEGER, `participantsJson` TEXT NOT NULL, `finalizedAtEpochMillis` INTEGER, `createdAtEpochMillis` INTEGER NOT NULL, `updatedAtEpochMillis` INTEGER NOT NULL, PRIMARY KEY(`id`))""")
                db.execSQL("""CREATE TABLE IF NOT EXISTS `game_seasons` (`id` TEXT NOT NULL, `startedAtEpochMillis` INTEGER NOT NULL, `endedAtEpochMillis` INTEGER NOT NULL, `startRating` INTEGER NOT NULL, `endRating` INTEGER, `highestRankTier` TEXT NOT NULL, `highestRankDivision` INTEGER, `wins` INTEGER NOT NULL, `losses` INTEGER NOT NULL, `draws` INTEGER NOT NULL, `totalEligibleSteps` INTEGER NOT NULL, `bestWinStreak` INTEGER NOT NULL, `status` TEXT NOT NULL, `rewardClaimed` INTEGER NOT NULL, `createdAtEpochMillis` INTEGER NOT NULL, `updatedAtEpochMillis` INTEGER NOT NULL, PRIMARY KEY(`id`))""")
                db.execSQL("""CREATE TABLE IF NOT EXISTS `achievement_unlocks` (`achievementId` TEXT NOT NULL, `unlockedAtEpochMillis` INTEGER NOT NULL, `progressValue` INTEGER NOT NULL, `seasonId` TEXT, `acknowledged` INTEGER NOT NULL, PRIMARY KEY(`achievementId`))""")
            }
        }

        val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """CREATE TABLE IF NOT EXISTS `game_notification_events` (
                        `id` TEXT NOT NULL, `type` TEXT NOT NULL, `sourceId` TEXT NOT NULL,
                        `deduplicationKey` TEXT NOT NULL, `title` TEXT NOT NULL,
                        `message` TEXT NOT NULL, `destinationRoute` TEXT NOT NULL,
                        `createdAtEpochMillis` INTEGER NOT NULL, `notBeforeEpochMillis` INTEGER NOT NULL,
                        `deliveredAtEpochMillis` INTEGER, `acknowledged` INTEGER NOT NULL,
                        PRIMARY KEY(`id`))""",
                )
                db.execSQL(
                    "CREATE UNIQUE INDEX IF NOT EXISTS `index_game_notification_events_deduplicationKey` " +
                        "ON `game_notification_events` (`deduplicationKey`)",
                )
            }
        }

        val MIGRATION_5_6 = object : Migration(5, 6) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE activity_processing_state ADD COLUMN activityRepairVersion INTEGER NOT NULL DEFAULT 0")
            }
        }

        val MIGRATION_6_7 = object : Migration(6, 7) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE game_player_profile ADD COLUMN displayName TEXT")
                db.execSQL(
                    """CREATE TABLE IF NOT EXISTS `weekly_league_participants` (
                        `leagueId` TEXT NOT NULL, `participantId` TEXT NOT NULL,
                        `displayName` TEXT NOT NULL, `avatarKey` TEXT NOT NULL,
                        `points` INTEGER NOT NULL, `eligibleSteps` INTEGER NOT NULL,
                        `rank` INTEGER NOT NULL, `isLocalPlayer` INTEGER NOT NULL,
                        `generatedLocally` INTEGER NOT NULL, `createdAtEpochMillis` INTEGER NOT NULL,
                        `updatedAtEpochMillis` INTEGER NOT NULL,
                        PRIMARY KEY(`leagueId`, `participantId`))""",
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS `index_weekly_league_participants_leagueId` " +
                        "ON `weekly_league_participants` (`leagueId`)",
                )
                migrateLegacyLeagueParticipants(db)
            }
        }

        val MIGRATION_7_8 = object : Migration(7, 8) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """CREATE TABLE IF NOT EXISTS `competitive_integrity_segments` (
                        `id` TEXT NOT NULL, `localDate` TEXT NOT NULL, `zoneId` TEXT NOT NULL,
                        `startedAtEpochMillis` INTEGER NOT NULL, `endedAtEpochMillis` INTEGER NOT NULL,
                        `totalSteps` INTEGER NOT NULL, `eligibleSteps` INTEGER NOT NULL,
                        `restrictedSteps` INTEGER NOT NULL, `excludedSteps` INTEGER NOT NULL,
                        `assessment` TEXT NOT NULL, `reasons` TEXT NOT NULL,
                        `classifierVersion` INTEGER NOT NULL, `createdAtEpochMillis` INTEGER NOT NULL,
                        PRIMARY KEY(`id`))""",
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_competitive_integrity_segments_localDate_zoneId` ON `competitive_integrity_segments` (`localDate`, `zoneId`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_competitive_integrity_segments_startedAtEpochMillis` ON `competitive_integrity_segments` (`startedAtEpochMillis`)")
            }
        }

        /**
         * Legacy unclassifiedSteps was documented and presented as external recovery.
         * Existing values therefore migrate to externalRecoveredSteps. Counter long-gap
         * deltas are separated into unallocatedMeasuredSteps for all new writes.
         */
        val MIGRATION_8_9 = object : Migration(8, 9) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE daily_activity_records ADD COLUMN externalRecoveredSteps INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE daily_activity_records ADD COLUMN unallocatedMeasuredSteps INTEGER NOT NULL DEFAULT 0")
                db.execSQL("UPDATE daily_activity_records SET externalRecoveredSteps = unclassifiedSteps")
            }
        }

        /** Reclassify only legacy steps that have an auditable external record. */
        val MIGRATION_9_10 = object : Migration(9, 10) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE activity_processing_state ADD COLUMN legacyOriginRepairVersion INTEGER NOT NULL DEFAULT 0")
                val daily = mutableMapOf<String, Triple<String, Long, Long>>()
                db.query("SELECT localDate, zoneId, unclassifiedSteps, unallocatedMeasuredSteps FROM daily_activity_records").use { cursor ->
                    while (cursor.moveToNext()) {
                        val key = "${cursor.getString(0)}|${cursor.getString(1)}"
                        daily[key] = Triple(cursor.getString(1), cursor.getLong(2).coerceAtLeast(0), cursor.getLong(3).coerceAtLeast(0))
                    }
                }
                val proven = mutableMapOf<String, Long>()
                db.query("""SELECT p.startedAtEpochMillis, p.endedAtEpochMillis, p.appliedSteps,
                    g.startedAtEpochMillis, g.endedAtEpochMillis, g.zoneId
                    FROM processed_external_step_records p
                    JOIN tracking_gap_records g ON g.id = p.gapId
                    WHERE p.gapId IS NOT NULL AND p.gapId != ''""").use { cursor ->
                    while (cursor.moveToNext()) {
                        val start = Instant.ofEpochMilli(cursor.getLong(0))
                        val end = Instant.ofEpochMilli(cursor.getLong(1)).let { if (it.isAfter(start)) it else start.plusMillis(1) }
                        val zone = ZoneId.of(cursor.getString(5))
                        val applied = cursor.getLong(2).coerceAtLeast(0)
                        val first = start.atZone(zone).toLocalDate()
                        val last = end.minusNanos(1).atZone(zone).toLocalDate()
                        val dates = generateSequence(first) { date -> date.takeIf { it.isBefore(last) }?.plusDays(1) }.toList()
                        val durations = dates.map { date ->
                            val dayStart = date.atStartOfDay(zone).toInstant()
                            val dayEnd = date.plusDays(1).atStartOfDay(zone).toInstant()
                            Duration.between(maxOf(start, dayStart), minOf(end, dayEnd)).toMillis().coerceAtLeast(0)
                        }
                        val allocations = allocateMigrationSteps(applied, durations)
                        dates.zip(allocations).forEach { (date, amount) ->
                            val key = "$date|${zone.id}"
                            if (key in daily) proven[key] = safeAdd(proven[key] ?: 0, amount)
                        }
                    }
                }
                daily.forEach { (key, value) ->
                    val legacy = value.second
                    val external = minOf(legacy, proven[key] ?: 0)
                    // v9 already owns this remainder; never add it a second time.
                    val unallocated = maxOf(value.third, legacy - external)
                    val split = key.split('|', limit = 2)
                    db.execSQL("UPDATE daily_activity_records SET externalRecoveredSteps = ?, unallocatedMeasuredSteps = ? WHERE localDate = ? AND zoneId = ?",
                        arrayOf(external, unallocated, split[0], split[1]))
                }
                db.execSQL("UPDATE activity_processing_state SET legacyOriginRepairVersion = 1 WHERE key = 'sensor'")
            }
        }

        private fun safeAdd(first: Long, second: Long): Long =
            if (Long.MAX_VALUE - first < second) Long.MAX_VALUE else first + second

        private fun allocateMigrationSteps(total: Long, weights: List<Long>): List<Long> {
            val denominator = weights.fold(BigInteger.ZERO) { acc, value -> acc + value.toBigInteger() }
            if (denominator == BigInteger.ZERO) return List(weights.size) { 0L }
            val safeTotal = total.coerceAtLeast(0).toBigInteger()
            val floors = weights.map { safeTotal * it.toBigInteger() / denominator }
            var remaining = (safeTotal - floors.fold(BigInteger.ZERO) { acc, value -> acc + value }).toLong()
            val order = weights.indices.sortedWith(compareByDescending<Int> {
                safeTotal * weights[it].toBigInteger() % denominator
            }.thenBy { it })
            val result = floors.map { it.toLong() }.toMutableList()
            for (index in order) {
                if (remaining == 0L) break
                result[index]++
                remaining--
            }
            return result
        }

        private fun migrateLegacyLeagueParticipants(db: SupportSQLiteDatabase) {
            db.query(
                "SELECT id, participantsJson, createdAtEpochMillis, updatedAtEpochMillis FROM weekly_leagues",
            ).use { cursor ->
                while (cursor.moveToNext()) {
                    val leagueId = cursor.getString(0)
                    val createdAt = cursor.getLong(2)
                    val updatedAt = cursor.getLong(3)
                    val participants = runCatching { org.json.JSONArray(cursor.getString(1)) }
                        .getOrNull() ?: continue
                    for (index in 0 until participants.length()) {
                        val participant = participants.optJSONObject(index) ?: continue
                        val participantId = participant.optString("id").takeIf { it.isNotBlank() } ?: continue
                        val local = participantId == "player"
                        val storedName = participant.optString("name").ifBlank { "参加者" }
                        val displayName = if (local && storedName == "You") "あなた" else storedName
                        db.execSQL(
                            """INSERT OR REPLACE INTO weekly_league_participants
                                (leagueId,participantId,displayName,avatarKey,points,eligibleSteps,rank,
                                isLocalPlayer,generatedLocally,createdAtEpochMillis,updatedAtEpochMillis)
                                VALUES (?,?,?,?,?,?,?,?,?,?,?)""",
                            arrayOf(
                                leagueId,
                                participantId,
                                displayName,
                                participant.optString("avatarKey", participantId),
                                participant.optInt("points", 0),
                                participant.optLong("steps", 0L),
                                index + 1,
                                if (local) 1 else 0,
                                1,
                                createdAt,
                                updatedAt,
                            ),
                        )
                    }
                }
            }
        }
    }
}
