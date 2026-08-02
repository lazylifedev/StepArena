package com.lazyapps.steparena.recovery

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.lazyapps.steparena.activity.ActivityRepository
import com.lazyapps.steparena.activity.UserProfileRepository
import com.lazyapps.steparena.core.database.StepArenaDatabase
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.time.Instant
import java.time.ZoneId

@RunWith(AndroidJUnit4::class)
class GapRecoveryInstrumentationTest {
    private lateinit var database: StepArenaDatabase
    private val context get() = ApplicationProvider.getApplicationContext<android.content.Context>()

    @Before fun setUp() {
        database = Room.inMemoryDatabaseBuilder(context, StepArenaDatabase::class.java)
            .allowMainThreadQueries()
            .build()
    }

    @After fun tearDown() {
        runBlocking { RecoverySettingsRepository(context).reset() }
        database.close()
    }

    @Test fun fakeSourceAppliesOneHundredStepsOnlyOnce() = runBlocking {
        val start = Instant.parse("2026-07-29T01:00:00Z")
        val end = start.plusSeconds(7_200)
        val fake = FakeExternalActivityDataSource(
            permissions = setOf("steps"),
            records = listOf(
                ExternalStepSegment(
                    start = start,
                    end = end,
                    steps = 100,
                    dataOriginPackage = "fitness.app",
                    recordId = "record-100",
                    lastModifiedAt = end,
                    recordingMethod = ExternalRecordingMethod.AUTOMATIC,
                ),
            ),
        )
        val activity = ActivityRepository(database, UserProfileRepository(context))
        val settings = RecoverySettingsRepository(context)
        settings.update(RecoverySettings(healthConnectEnabled = true))
        val repository = GapRecoveryRepository(
            database,
            fake,
            context.packageName,
            activity,
            settings,
        )
        val gap = repository.detectHeartbeatGap(
            start,
            end,
            ZoneId.of("UTC"),
            explicitUserStop = false,
        )!!
        val first = repository.recover(gap.id)!!
        val second = repository.recover(gap.id)!!

        assertEquals(100L, first.recoveredSteps)
        assertEquals(100L, second.recoveredSteps)
        assertEquals(0L, database.daily().get("2026-07-29", "UTC")?.steps)
        assertEquals(100L, database.daily().get("2026-07-29", "UTC")?.unclassifiedSteps)
        assertEquals(100L, database.daily().get("2026-07-29", "UTC")?.externalRecoveredSteps)
        assertEquals(0L, database.daily().get("2026-07-29", "UTC")?.unallocatedMeasuredSteps)
    }

    @Test fun realAvailabilityCheckNeverCrashes() = runBlocking {
        val availability = HealthConnectActivityDataSource(context).availability()
        assertNotEquals(null, availability)
    }
}
