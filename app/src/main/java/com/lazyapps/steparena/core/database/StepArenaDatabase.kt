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
import com.lazyapps.steparena.core.database.entity.ActivityProcessingStateEntity
import com.lazyapps.steparena.core.database.entity.DailyActivityRecordEntity
import com.lazyapps.steparena.core.database.entity.HourlyActivityRecordEntity
import com.lazyapps.steparena.core.database.entity.WalkingSessionEntity

@Database(
    entities = [
        HourlyActivityRecordEntity::class,
        DailyActivityRecordEntity::class,
        WalkingSessionEntity::class,
        ActivityProcessingStateEntity::class,
    ],
    version = 2,
    exportSchema = true,
)
@TypeConverters(ActivityConverters::class)
abstract class StepArenaDatabase : RoomDatabase() {
    abstract fun hourly(): HourlyActivityDao
    abstract fun daily(): DailyActivityDao
    abstract fun sessions(): WalkingSessionDao
    abstract fun processingState(): ActivityProcessingStateDao

    companion object {
        @Volatile private var instance: StepArenaDatabase? = null
        fun get(context: Context): StepArenaDatabase = instance ?: synchronized(this) {
            instance ?: Room.databaseBuilder(
                context.applicationContext,
                StepArenaDatabase::class.java,
                "step_arena.db",
            ).addMigrations(MIGRATION_1_2).build().also { instance = it }
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
    }
}
