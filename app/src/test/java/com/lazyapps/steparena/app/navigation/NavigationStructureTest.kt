package com.lazyapps.steparena.app.navigation

import com.lazyapps.steparena.feature.game.ArenaPage
import org.junit.Assert.assertEquals
import org.junit.Test

class NavigationStructureTest {
    @Test
    fun `legacy game notification routes map to the canonical arena structure`() {
        assertEquals("challenge", canonicalGameRoute("match"))
        assertEquals("challenge/rank", canonicalGameRoute("rank"))
        assertEquals("challenge/weekly-group", canonicalGameRoute("league"))
        assertEquals("challenge/monthly-record", canonicalGameRoute("season"))
        assertEquals("achievements", canonicalGameRoute("achievements"))
    }

    @Test
    fun `unknown routes cannot select a mismatched bottom destination`() {
        assertEquals(AppDestination.CHALLENGE, topLevelDestinationForRoute("challenge/{page}"))
        assertEquals(AppDestination.ACHIEVEMENTS, topLevelDestinationForRoute("achievements"))
        assertEquals(AppDestination.HOME, topLevelDestinationForRoute("rank"))
        assertEquals(AppDestination.HOME, topLevelDestinationForRoute("home/diagnostics"))
        assertEquals(AppDestination.SETTINGS, topLevelDestinationForRoute("settings/diagnostics"))
    }

    @Test
    fun `arena page parsing excludes achievements and safely defaults`() {
        assertEquals(ArenaPage.RANK, ArenaPage.fromRouteSegment("rank"))
        assertEquals(ArenaPage.WEEKLY_GROUP, ArenaPage.fromRouteSegment("weekly-group"))
        assertEquals(ArenaPage.MONTHLY_RECORD, ArenaPage.fromRouteSegment("monthly-record"))
        assertEquals(ArenaPage.CHALLENGE, ArenaPage.fromRouteSegment("achievements"))
    }
}
