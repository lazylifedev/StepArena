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
        GameSeasonEntity::class,
        AchievementUnlockEntity::class,
        GameNotificationEventEntity::class,
    ],
    version = 6,
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
    abstract fun gameSeasons(): GameSeasonDao
    abstract fun achievementUnlocks(): AchievementUnlockDao
    abstract fun gameNotificationEvents(): GameNotificationEventDao

    companion object {
        const val PRODUCTION_DATABASE_NAME = "step_arena.db"
        @Volatile private var instance: StepArenaDatabase? = null
        fun get(context: Context): StepArenaDatabase = instance ?: synchronized(this) {
            instance ?: build(context, PRODUCTION_DATABASE_NAME).also { instance = it }
        }

        fun build(context: Context, name: String): StepArenaDatabase =
            Room.databaseBuilder(context.applicationContext, StepArenaDatabase::class.java, name)
                .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5, MIGRATION_5_6)
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
    }
}
