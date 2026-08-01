package com.lazyapps.steparena.tracking

import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.datastore.preferences.preferencesDataStoreFile
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.util.UUID

@RunWith(AndroidJUnit4::class)
class TrackingStateRepositoryTest {
    @Test fun reload_continuesWithoutDoubleAddition() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val fileName = "step_tracking_instrumentation_${UUID.randomUUID()}"
        val file = context.preferencesDataStoreFile(fileName)
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        val dataStore = PreferenceDataStoreFactory.create(scope = scope) { file }
        val repository = TrackingStateRepository(dataStore)
        val base = StepTrackingState(
            trackingRequested = true,
            bootSessionId = "instrumentation-boot",
            sensorBaseline = 10_000,
            lastSensorValue = 10_050,
            accumulatedTodaySteps = 50,
            currentLocalDate = LocalDate.of(2026, 7, 29),
            currentZoneId = "Asia/Tokyo",
        )
        try {
            repository.update { base }
            val reloaded = TrackingStateRepository(dataStore).current()
            val result = StepCounter().accept(
                10_075f,
                reloaded,
                Instant.parse("2026-07-29T12:00:00Z"),
                ZoneId.of("Asia/Tokyo"),
                "instrumentation-boot",
            )
            assertEquals(75, result.state.accumulatedTodaySteps)
        } finally {
            scope.cancel()
            file.delete()
        }
    }
}
