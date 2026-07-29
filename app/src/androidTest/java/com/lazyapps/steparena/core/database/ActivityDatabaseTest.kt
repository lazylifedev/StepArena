package com.lazyapps.steparena.core.database

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.lazyapps.steparena.core.database.entity.HourlyActivityRecordEntity
import com.lazyapps.steparena.core.database.model.DataQuality
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ActivityDatabaseTest {
    private lateinit var database: StepArenaDatabase

    @Before fun create() {
        database = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext<Context>(),
            StepArenaDatabase::class.java,
        ).build()
    }

    @After fun close() = database.close()

    @Test fun hourlyUpsertIsIdempotent() = runBlocking {
        val record = hour(12)
        database.hourly().upsert(record)
        database.hourly().upsert(record.copy(steps = 20))
        assertEquals(20L, database.hourly().byId(record.id)?.steps)
        assertEquals(1, database.hourly().forDate("2026-07-29", "Asia/Tokyo").size)
    }

    private fun hour(steps: Long) = HourlyActivityRecordEntity(
        id = "2026-07-29|12|Asia/Tokyo|32400",
        localDate = "2026-07-29", hourOfDay = 12, zoneId = "Asia/Tokyo",
        utcOffsetSeconds = 32400, periodStartEpochMillis = 0, periodEndEpochMillis = 1,
        steps = steps, distanceMeters = 8.4, walkingDurationSeconds = 60,
        estimatedCaloriesKcal = 1.0, averageWalkingSpeedKmh = 0.5,
        stepsQuality = DataQuality.MEASURED, distanceQuality = DataQuality.ESTIMATED,
        durationQuality = DataQuality.ESTIMATED, caloriesQuality = DataQuality.ESTIMATED,
        speedQuality = DataQuality.ESTIMATED, firstActivityAtEpochMillis = 0,
        lastActivityAtEpochMillis = 1, sensorEventCount = 1, recoveredSteps = 0,
        estimatedSteps = 0, createdAtEpochMillis = 0, updatedAtEpochMillis = 1,
    )
}
