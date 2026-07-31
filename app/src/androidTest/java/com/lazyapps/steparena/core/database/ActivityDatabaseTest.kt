package com.lazyapps.steparena.core.database

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.lazyapps.steparena.core.database.entity.HourlyActivityRecordEntity
import com.lazyapps.steparena.core.database.entity.ActivityProcessingStateEntity
import com.lazyapps.steparena.core.database.model.DataQuality
import com.lazyapps.steparena.activity.ActivityRepository
import com.lazyapps.steparena.activity.UserProfileRepository
import com.lazyapps.steparena.core.database.model.WalkingSessionStatus
import java.time.Instant
import java.time.ZoneId
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

    @Test fun manualWalk_hasIndependentUuidReceivesDeltaOnceAndEndsWithoutService() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val repository = ActivityRepository(database, UserProfileRepository(context))
        val at = Instant.parse("2026-07-29T03:00:00Z")
        val first = repository.startManualSession(at, ZoneId.of("Asia/Tokyo"), "service-stable")
        val duplicate = repository.startManualSession(at.plusSeconds(1), ZoneId.of("Asia/Tokyo"), "changed")
        assertEquals(first.id, duplicate.id)
        assertEquals("service-stable", duplicate.trackingServiceSessionId)

        repository.recordCounterDelta(
            sensorValue = 1_010,
            delta = 10,
            at = at.plusSeconds(60),
            zoneId = ZoneId.of("Asia/Tokyo"),
            bootSessionId = "boot",
            trackingServiceSessionId = "service-stable",
            recovered = false,
        )
        val active = database.sessions().active(true)!!
        assertEquals(10L, active.steps)
        assertEquals(null, database.sessions().active(false))
        assertEquals(10L, database.daily().get("2026-07-29", "Asia/Tokyo")?.steps)

        assertEquals(true, repository.endManualSession(first.id, at.plusSeconds(120)))
        assertEquals(false, repository.endManualSession(first.id, at.plusSeconds(121)))
        assertEquals(WalkingSessionStatus.COMPLETED, database.sessions().get(first.id)?.status)
    }

    @Test fun manualWalk_crossingMidnightSplitsIntoTwoSessions() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val repository = ActivityRepository(database, UserProfileRepository(context))
        val zone = ZoneId.of("Asia/Tokyo")
        val start = Instant.parse("2026-07-29T14:59:30Z")
        val first = repository.startManualSession(start, zone, "service-stable")
        repository.recordCounterDelta(
            sensorValue = 500,
            delta = 5,
            at = Instant.parse("2026-07-29T15:00:10Z"),
            zoneId = zone,
            bootSessionId = "boot",
            trackingServiceSessionId = "service-stable",
            recovered = false,
        )
        assertEquals(WalkingSessionStatus.COMPLETED, database.sessions().get(first.id)?.status)
        val next = database.sessions().active(true)!!
        assertEquals("2026-07-30", next.localDate)
        assertEquals(5L, next.steps)
    }

    @Test fun counterDeltaAcrossHoursAllocatesStepsAndDurationWithoutLoss() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val repository = ActivityRepository(database, UserProfileRepository(context))
        val zone = ZoneId.of("Asia/Tokyo")
        val start = Instant.parse("2026-07-29T03:55:00Z")
        database.processingState().upsert(
            ActivityProcessingStateEntity(
                lastCounterValue = 1_000, lastEventEpochMillis = start.toEpochMilli(),
                lastZoneId = zone.id, lastBootSessionId = "boot", activeAutoSessionId = null,
                activeManualSessionId = null, lastDetectorEventEpochMillis = null,
                lastWalkingEventEpochMillis = start.toEpochMilli(),
                updatedAtEpochMillis = start.toEpochMilli(), activityRepairVersion = 1,
            ),
        )
        repository.recordCounterDelta(
            sensorValue = 2_000, delta = 1_000, at = start.plusSeconds(600),
            zoneId = zone, bootSessionId = "boot", trackingServiceSessionId = "service",
            recovered = false,
        )
        val hours = database.hourly().forDate("2026-07-29", zone.id)
        assertEquals(2, hours.size)
        assertEquals(1_000L, hours.sumOf { it.steps })
        assertEquals(600L, hours.sumOf { it.walkingDurationSeconds ?: 0 })
        assertEquals(true, hours.all { (it.walkingDurationSeconds ?: 0) > 0 })
        assertEquals(600L, database.daily().get("2026-07-29", zone.id)?.walkingDurationSeconds)
    }

    @Test fun longGapCounterDeltaRemainsMeasuredButUnallocatedAndIsNotExternal() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val repository = ActivityRepository(database, UserProfileRepository(context))
        val zone = ZoneId.of("Asia/Tokyo")
        val start = Instant.parse("2026-07-29T01:00:00Z")
        database.processingState().upsert(
            ActivityProcessingStateEntity(
                lastCounterValue = 1_000, lastEventEpochMillis = start.toEpochMilli(),
                lastZoneId = zone.id, lastBootSessionId = "boot", activeAutoSessionId = null,
                activeManualSessionId = null, lastDetectorEventEpochMillis = null,
                lastWalkingEventEpochMillis = start.toEpochMilli(),
                updatedAtEpochMillis = start.toEpochMilli(), activityRepairVersion = 1,
            ),
        )
        repository.recordCounterDelta(
            sensorValue = 2_000, delta = 1_000, at = start.plusSeconds(7_200),
            zoneId = zone, bootSessionId = "boot", trackingServiceSessionId = "service",
            recovered = false,
        )

        val daily = database.daily().get("2026-07-29", zone.id)!!
        assertEquals(1_000L, daily.steps)
        assertEquals(1_000L, daily.unallocatedMeasuredSteps)
        assertEquals(0L, daily.externalRecoveredSteps)
        assertEquals(DataQuality.MEASURED, daily.stepsQuality)
    }

    @Test fun counterMeasuredStepsKeepEstimatedDurationIndependent() = runBlocking {
        assertCounterAndDurationQuality(0, DataQuality.ESTIMATED)
    }

    @Test fun counterMeasuredStepsKeepMixedDurationIndependent() = runBlocking {
        assertCounterAndDurationQuality(50, DataQuality.MIXED)
    }

    @Test fun counterMeasuredStepsKeepMeasuredDurationIndependent() = runBlocking {
        assertCounterAndDurationQuality(98, DataQuality.MEASURED)
    }

    private suspend fun assertCounterAndDurationQuality(
        detectorCount: Int,
        expectedDurationQuality: DataQuality,
    ) {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val repository = ActivityRepository(database, UserProfileRepository(context))
        val zone = ZoneId.of("Asia/Tokyo")
        val start = Instant.parse("2026-07-29T03:00:00Z")
        database.processingState().upsert(
            ActivityProcessingStateEntity(
                lastCounterValue = 1_000, lastEventEpochMillis = start.toEpochMilli(),
                lastZoneId = zone.id, lastBootSessionId = "boot", activeAutoSessionId = null,
                activeManualSessionId = null, lastDetectorEventEpochMillis = null,
                lastWalkingEventEpochMillis = start.toEpochMilli(),
                updatedAtEpochMillis = start.toEpochMilli(), activityRepairVersion = 1,
            ),
        )
        repeat(detectorCount) { index ->
            repository.recordDetector(start.plusMillis(index * 600L))
        }
        repository.recordCounterDelta(
            sensorValue = 1_100, delta = 100, at = start.plusSeconds(60),
            zoneId = zone, bootSessionId = "boot", trackingServiceSessionId = "service",
            recovered = false, detectorAvailable = detectorCount > 0,
        )

        val hour = database.hourly().forDate("2026-07-29", zone.id).single()
        val daily = database.daily().get("2026-07-29", zone.id)!!
        val session = database.sessions().active(false)!!
        assertEquals(DataQuality.MEASURED, hour.stepsQuality)
        assertEquals(expectedDurationQuality, hour.durationQuality)
        assertEquals(0L, hour.estimatedSteps)
        assertEquals(0L, hour.recoveredSteps)
        assertEquals(DataQuality.MEASURED, daily.stepsQuality)
        assertEquals(expectedDurationQuality, daily.durationQuality)
        assertEquals(DataQuality.MEASURED, session.stepsQuality)
        assertEquals(expectedDurationQuality, session.durationQuality)
        assertEquals(0L, session.estimatedStepCount)
        assertEquals(0L, session.recoveredStepCount)
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
        estimatedSteps = 0, appliedStepLengthMeters = 0.7, appliedWeightKg = 60.0,
        calorieFormulaVersion = 1, createdAtEpochMillis = 0, updatedAtEpochMillis = 1,
    )
}
