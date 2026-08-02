package com.lazyapps.steparena.service.tracking

import com.lazyapps.steparena.tracking.StepCounter
import com.lazyapps.steparena.tracking.StepEventResult
import com.lazyapps.steparena.tracking.StepTrackingState
import com.lazyapps.steparena.tracking.TrackingStatus
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

class ServiceStateSerializationTest {
    @Test fun counterAndHeartbeatRemainOrdered() = runBlocking {
        val fixture = Fixture()
        for (raw in 6_638..7_106) {
            fixture.counter(raw)
            if (raw % 17 == 0) fixture.heartbeat(raw.toLong())
        }

        assertEquals(469L, fixture.totalDelta)
        assertEquals(7_106L, fixture.state.lastSensorValue)
        assertEquals(7_104L, fixture.state.accumulatedTodaySteps)
        assertTrue(fixture.state.lastHeartbeatAt != null)
    }

    @Test fun notificationOnlyChangesNotificationDiagnostic() = runBlocking {
        val fixture = Fixture()
        val before = fixture.state
        fixture.notification(Instant.parse("2026-08-01T15:00:00Z"))

        assertEquals(before.lastSensorValue, fixture.state.lastSensorValue)
        assertEquals(before.accumulatedTodaySteps, fixture.state.accumulatedTodaySteps)
        assertEquals(Instant.parse("2026-08-01T15:00:00Z"), fixture.state.lastNotificationAt)
    }

    @Test fun counterQueuedDuringRestoreRunsAfterRestoredState() = runBlocking {
        val fixture = Fixture()
        val restoreStarted = CompletableDeferred<Unit>()
        val finishRestore = CompletableDeferred<Unit>()
        val restore = launch {
            fixture.gate.run {
                restoreStarted.complete(Unit)
                finishRestore.await()
                fixture.state = fixture.state.copy(lastHeartbeatAt = Instant.EPOCH)
            }
        }
        restoreStarted.await()
        val counter = launch { fixture.counter(6_638) }
        finishRestore.complete(Unit)
        restore.join()
        counter.join()

        assertEquals(1L, fixture.totalDelta)
        assertEquals(6_638L, fixture.state.lastSensorValue)
        assertEquals(Instant.EPOCH, fixture.state.lastHeartbeatAt)
    }

    @Test fun stopAcceptsEarlierCounterAndRejectsLaterCounter() = runBlocking {
        val fixture = Fixture()
        fixture.counter(6_638)
        fixture.stop()
        fixture.counter(6_639)

        assertEquals(1L, fixture.totalDelta)
        assertFalse(fixture.state.trackingRequested)
        assertNull(fixture.state.lastSensorValue)
    }

    @Test fun staleSessionStopIsIgnoredAndNullRemainsCompatible() = runBlocking {
        val fixture = Fixture()
        assertFalse(fixture.stop("old-session"))
        assertTrue(fixture.state.trackingRequested)
        assertTrue(fixture.stop(null))
        assertFalse(fixture.state.trackingRequested)
    }

    @Test fun destroyRejectsQueuedAndFutureUpdates() = runBlocking {
        val fixture = Fixture()
        fixture.gate.destroy()
        fixture.heartbeat(1)
        fixture.notification(Instant.EPOCH)
        fixture.counter(6_638)

        assertEquals(0L, fixture.totalDelta)
        assertNull(fixture.state.lastHeartbeatAt)
        assertNull(fixture.state.lastNotificationAt)
        assertEquals(6_637L, fixture.state.lastSensorValue)
    }

    @Test fun deterministicHighFrequencySequenceHasNoRollbackOrLargeDelta() = runBlocking {
        val fixture = Fixture(raw = 10_000L, accumulated = 9_998L)
        for (raw in 10_001..20_000) {
            fixture.counter(raw)
            if (raw % 31 == 0) fixture.heartbeat(raw.toLong())
            if (raw % 47 == 0) fixture.notification(Instant.ofEpochSecond(raw.toLong()))
        }

        assertEquals(10_000L, fixture.totalDelta)
        assertEquals(10_000L, fixture.roomDaily)
        assertEquals(10_000L, fixture.roomHourly)
        assertEquals(10_000L, fixture.dataStoreDelta)
        assertEquals(10_000L, fixture.sessionDelta)
        assertEquals(10_000L, fixture.integrityDelta)
        assertEquals(10_000, fixture.segmentCount)
        assertEquals(1L, fixture.maxDelta)
        assertEquals(20_000L, fixture.state.lastSensorValue)
    }

    private class Fixture(raw: Long = 6_637L, accumulated: Long = 6_635L) {
        val gate = ServiceStateUpdateGate()
        private val stepCounter = StepCounter()
        var state = StepTrackingState(
            trackingRequested = true,
            trackingStatus = TrackingStatus.TRACKING,
            serviceRunning = true,
            sessionId = "current-session",
            currentLocalDate = LocalDate.of(2026, 8, 2),
            currentZoneId = "Asia/Tokyo",
            bootSessionId = "boot",
            lastSensorValue = raw,
            accumulatedTodaySteps = accumulated,
        )
        var totalDelta = 0L
        var roomDaily = 0L
        var roomHourly = 0L
        var dataStoreDelta = 0L
        var sessionDelta = 0L
        var integrityDelta = 0L
        var segmentCount = 0
        var maxDelta = 0L

        suspend fun counter(raw: Int) {
            gate.run {
                if (!state.trackingRequested) return@run
                val result = stepCounter.accept(
                    raw.toFloat(), state,
                    Instant.parse("2026-08-01T15:00:00Z").plusSeconds(raw.toLong()),
                    ZoneId.of("Asia/Tokyo"), "boot",
                )
                state = result.state
                val delta = (result as? StepEventResult.Added)?.delta ?: return@run
                totalDelta += delta
                roomDaily += delta
                roomHourly += delta
                dataStoreDelta += delta
                sessionDelta += delta
                integrityDelta += delta
                segmentCount++
                maxDelta = maxOf(maxDelta, delta)
            }
        }

        suspend fun heartbeat(second: Long) {
            gate.run {
                state = heartbeatState(state, Instant.ofEpochSecond(second))
            }
        }

        suspend fun notification(at: Instant) {
            gate.run { state = state.copy(lastNotificationAt = at) }
        }

        suspend fun stop(requestedSession: String? = "current-session"): Boolean {
            var stopped = false
            gate.run {
                if (isCurrentSessionRequest(requestedSession, state.sessionId)) {
                    state = state.copy(
                        trackingRequested = false,
                        trackingStatus = TrackingStatus.STOPPED,
                        serviceRunning = false,
                        sessionId = null,
                        lastSensorValue = null,
                    )
                    stopped = true
                }
            }
            return stopped
        }
    }
}
