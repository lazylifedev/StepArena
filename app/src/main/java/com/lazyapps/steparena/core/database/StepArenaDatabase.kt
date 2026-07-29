package com.lazyapps.steparena.core.database

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
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
    version = 1,
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
            ).build().also { instance = it }
        }
    }
}
