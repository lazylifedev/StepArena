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

@Database(
    entities = [
        HourlyActivityRecordEntity::class,
        DailyActivityRecordEntity::class,
        WalkingSessionEntity::class,
        ActivityProcessingStateEntity::class,
        TrackingGapRecordEntity::class,
        ProcessedExternalStepRecordEntity::class,
    ],
    version = 3,
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

    companion object {
        @Volatile private var instance: StepArenaDatabase? = null
        fun get(context: Context): StepArenaDatabase = instance ?: synchronized(this) {
            instance ?: Room.databaseBuilder(
                context.applicationContext,
                StepArenaDatabase::class.java,
                "step_arena.db",
            ).addMigrations(MIGRATION_1_2, MIGRATION_2_3).build().also { instance = it }
        }

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
    }
}
