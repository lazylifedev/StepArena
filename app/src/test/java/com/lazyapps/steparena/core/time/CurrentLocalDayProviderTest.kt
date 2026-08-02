package com.lazyapps.steparena.core.time

import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import kotlinx.coroutines.async
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CurrentLocalDayProviderTest {
    @Test
    fun localDay_isSameAt2359AndChangesAtMidnight() {
        val zone = ZoneId.of("Asia/Tokyo")
        assertEquals(
            "2026-07-30",
            localDayAt(Instant.parse("2026-07-30T14:59:59Z"), zone).date.toString(),
        )
        assertEquals(
            "2026-07-31",
            localDayAt(Instant.parse("2026-07-30T15:00:00Z"), zone).date.toString(),
        )
    }

    @Test
    fun flowUpdatesWithoutRecreationAndSelectsNextDayValuesAtMidnight() = runBlocking {
        val clock = MutableClock(Instant.parse("2026-07-30T14:59:59Z"))
        val zone = ZoneId.of("Asia/Tokyo")
        val changes = MutableSharedFlow<Unit>()
        val boundaries = Channel<Unit>(Channel.UNLIMITED)
        val firstSeen = CompletableDeferred<Unit>()
        val collected = async(start = CoroutineStart.UNDISPATCHED) {
            currentLocalDayFlow(
                clock = clock,
                zoneId = { zone },
                changes = changes,
                waitForNextBoundary = { boundaries.receive() },
            ).onEach { firstSeen.complete(Unit) }.take(2).toList()
        }
        withTimeout(2_000) { firstSeen.await() }
        clock.now = Instant.parse("2026-07-30T15:00:00Z")
        boundaries.send(Unit)
        val days = withTimeout(2_000) { collected.await() }
        assertEquals("2026-07-30", days[0].date.toString())
        assertEquals("2026-07-31", days[1].date.toString())
        val addedSteps = mapOf("2026-07-30" to 10L, "2026-07-31" to 0L)
        val challenges = mapOf("2026-07-30" to "previous", "2026-07-31" to "next")
        assertEquals(10L, addedSteps.getValue(days[0].date.toString()))
        assertEquals(0L, addedSteps.getValue(days[1].date.toString()))
        assertEquals("previous", challenges.getValue(days[0].date.toString()))
        assertEquals("next", challenges.getValue(days[1].date.toString()))
    }

    @Test
    fun timezoneChangeRecalculatesLocalDate() {
        val instant = Instant.parse("2026-07-30T15:00:00Z")
        assertEquals(
            "2026-07-31",
            localDayAt(instant, ZoneId.of("Asia/Tokyo")).date.toString(),
        )
        assertEquals(
            "2026-07-30",
            localDayAt(instant, ZoneId.of("America/Los_Angeles")).date.toString(),
        )
    }

    @Test
    fun timezoneChangeUpdatesFlowWithoutRecreationForPreviousAndNextDay() = runBlocking {
        val clock = MutableClock(Instant.parse("2026-07-30T15:30:00Z"))
        var zone = ZoneId.of("UTC")
        val changes = MutableSharedFlow<Unit>()
        val seen = Channel<Unit>(Channel.UNLIMITED)
        val collected = async(start = CoroutineStart.UNDISPATCHED) {
            currentLocalDayFlow(
                clock = clock,
                zoneId = { zone },
                changes = changes,
            ).onEach { seen.send(Unit) }.take(3).toList()
        }
        withTimeout(2_000) { seen.receive() }
        zone = ZoneId.of("Asia/Tokyo")
        changes.emit(Unit)
        withTimeout(2_000) { seen.receive() }
        zone = ZoneId.of("America/Los_Angeles")
        changes.emit(Unit)
        withTimeout(2_000) { seen.receive() }

        val days = withTimeout(2_000) { collected.await() }
        assertEquals("2026-07-30", days[0].date.toString())
        assertEquals("2026-07-31", days[1].date.toString())
        assertEquals("2026-07-30", days[2].date.toString())
        assertEquals(ZoneId.of("America/Los_Angeles"), days[2].zoneId)
    }

    @Test
    fun dstBoundariesHavePositiveFiniteDelay() {
        val zone = ZoneId.of("America/Los_Angeles")
        val spring = millisUntilNextLocalDay(
            Instant.parse("2026-03-08T08:00:00Z"),
            zone,
        )
        val autumn = millisUntilNextLocalDay(
            Instant.parse("2026-11-01T07:00:00Z"),
            zone,
        )
        assertEquals(Duration.ofHours(23).toMillis(), spring)
        assertEquals(Duration.ofHours(25).toMillis(), autumn)
        assertTrue(spring > 0 && autumn > 0)
    }

    private class MutableClock(var now: Instant) : Clock() {
        override fun getZone(): ZoneId = ZoneId.of("UTC")
        override fun withZone(zone: ZoneId?): Clock = this
        override fun instant(): Instant = now
    }
}
