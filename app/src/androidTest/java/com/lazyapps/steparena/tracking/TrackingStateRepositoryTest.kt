package com.lazyapps.steparena.tracking

import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

@RunWith(AndroidJUnit4::class)
class TrackingStateRepositoryTest {
    @Test fun reload_continuesWithoutDoubleAddition() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val repository = TrackingStateRepository(context)
        val base = StepTrackingState(
            trackingRequested = true,
            bootSessionId = "instrumentation-boot",
            sensorBaseline = 10_000,
            lastSensorValue = 10_050,
            accumulatedTodaySteps = 50,
            currentLocalDate = LocalDate.of(2026, 7, 29),
            currentZoneId = "Asia/Tokyo",
        )
        repository.update { base }
        val reloaded = TrackingStateRepository(context).current()
        val result = StepCounter().accept(
            10_075f,
            reloaded,
            Instant.parse("2026-07-29T12:00:00Z"),
            ZoneId.of("Asia/Tokyo"),
            "instrumentation-boot",
        )
        assertEquals(75, result.state.accumulatedTodaySteps)
    }
}
