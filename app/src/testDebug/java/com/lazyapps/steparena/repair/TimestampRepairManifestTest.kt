package com.lazyapps.steparena.repair

import java.io.File
import java.security.MessageDigest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TimestampRepairManifestTest {
    private val asset = File("src/debug/assets/phase_7_4_15d_game_timestamp_manifest.json")

    @Test fun manifestIsFixedToAuditedPackageRepairAndFourColumns() {
        val text = asset.readText()
        assertTrue(text.contains("phase-7.4.15d-sov41-game-timestamps"))
        assertTrue(text.contains("\"packageName\": \"com.lazyapps.steparena\""))
        assertEquals(4, Regex("\"column\"").findAll(text).count())
        assertTrue(text.contains("daily_matches"))
        assertTrue(text.contains("weekly_league_participants"))
        assertTrue(text.contains("weekly_leagues"))
        assertFalse(text.contains("daily_activity_records"))
        assertFalse(text.contains("hourly_activity_records"))
        assertFalse(text.contains("walking_sessions"))
    }

    @Test fun manifestShaMatchesCompiledGate() {
        val sha = MessageDigest.getInstance("SHA-256").digest(asset.readBytes())
            .joinToString("") { "%02x".format(it) }
        assertEquals(Phase7415dTimestampRepair.EXPECTED_MANIFEST_SHA, sha)
    }
}
