package com.lazyapps.steparena.backup

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class CloudBackupChildGenerationTest {
    @Test fun everyV2ChildPayloadUsesTheEffectiveGeneration() {
        val collections = listOf(
            "daily", "hourly", "sessions", "challengeResults", "leagueHistory",
            "leagueParticipants", "seasonHistory", "achievements", "integritySegments", "settings",
        )
        collections.forEach { collection ->
            val payload = childPayloadForGeneration(mapOf("schemaVersion" to 2, "collection" to collection), 8L)
            assertEquals(8L, payload["backupGeneration"])
        }
    }

    @Test fun effectiveGenerationOverridesAnyStalePayloadValue() {
        assertEquals(8L, childPayloadForGeneration(mapOf("backupGeneration" to 7L), 8L)["backupGeneration"])
    }

    @Test fun nonPositiveGenerationIsRejected() {
        assertThrows(IllegalArgumentException::class.java) { childPayloadForGeneration(emptyMap(), 0L) }
    }
}
