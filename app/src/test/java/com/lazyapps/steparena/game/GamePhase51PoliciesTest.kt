package com.lazyapps.steparena.game

import java.time.Clock
import java.time.Instant
import java.time.LocalTime
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class GamePhase51PoliciesTest {
    @Test fun `league ties use points then eligible steps then stable id`() {
        val ranked = LeagueRanking.rank(
            listOf(
                LeagueParticipant("b", "B", 9, 5_000),
                LeagueParticipant("a", "A", 9, 5_000),
                LeagueParticipant("c", "C", 9, 6_000),
                LeagueParticipant("d", "D", 6, 30_000),
            ),
        )
        assertEquals(listOf("c", "a", "b", "d"), ranked.map { it.id })
    }

    @Test fun `league result bands cover all ten positions`() {
        assertEquals(LeagueResultBand.TOP_THREE, LeagueRanking.resultBand(1))
        assertEquals(LeagueResultBand.TOP_THREE, LeagueRanking.resultBand(3))
        assertEquals(LeagueResultBand.MIDDLE, LeagueRanking.resultBand(4))
        assertEquals(LeagueResultBand.MIDDLE, LeagueRanking.resultBand(7))
        assertEquals(LeagueResultBand.BOTTOM, LeagueRanking.resultBand(8))
        assertEquals(LeagueResultBand.BOTTOM, LeagueRanking.resultBand(10))
    }

    @Test fun `quiet hours include 22 through before 8`() {
        assertFalse(QuietHours.isQuiet(LocalTime.of(21, 59)))
        assertTrue(QuietHours.isQuiet(LocalTime.of(22, 0)))
        assertTrue(QuietHours.isQuiet(LocalTime.of(7, 59)))
        assertFalse(QuietHours.isQuiet(LocalTime.of(8, 0)))
    }

    @Test fun `night notification waits until next morning`() {
        val zone = ZoneId.of("Asia/Tokyo")
        val clock = Clock.fixed(Instant.parse("2026-07-29T14:30:00Z"), zone)
        val allowed = Instant.ofEpochMilli(GameNotificationDispatcher.nextAllowedEpochMillis(clock))
            .atZone(zone)
        assertEquals("2026-07-30", allowed.toLocalDate().toString())
        assertEquals(LocalTime.of(8, 0), allowed.toLocalTime())
    }

    @Test fun `daytime notification can be delivered immediately`() {
        val clock = Clock.fixed(
            Instant.parse("2026-07-29T03:00:00Z"),
            ZoneId.of("Asia/Tokyo"),
        )
        assertEquals(clock.millis(), GameNotificationDispatcher.nextAllowedEpochMillis(clock))
    }
}
