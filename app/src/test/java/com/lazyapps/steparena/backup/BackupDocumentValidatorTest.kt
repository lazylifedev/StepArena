package com.lazyapps.steparena.backup

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class BackupDocumentValidatorTest {
    @Test fun stableIdIsDeterministicAndFirestoreSafe() {
        val first = stableBackupId("session", "local/id", "123")
        assertEquals(first, stableBackupId("session", "local/id", "123"))
        assertEquals(32, first.length)
        assertTrue(first.matches(Regex("[0-9a-f]{32}")))
        assertNotEquals(first, stableBackupId("session", "local/id", "124"))
    }

    @Test fun validIntegrityDocumentIsAccepted() {
        BackupDocumentValidator.validate(document())
    }

    @Test(expected = IllegalArgumentException::class)
    fun negativeStepsAreRejected() {
        BackupDocumentValidator.validate(document(mapOf("steps" to -1L)))
    }

    @Test(expected = IllegalArgumentException::class)
    fun inconsistentIntegrityTotalsAreRejected() {
        BackupDocumentValidator.validate(document(mapOf("integrityEligible" to 79L)))
    }

    @Test(expected = IllegalArgumentException::class)
    fun unsupportedSchemaIsRejected() {
        BackupDocumentValidator.validate(document(mapOf("schemaVersion" to 2)))
    }

    @Test(expected = IllegalArgumentException::class)
    fun invalidDocumentIdIsRejected() {
        BackupDocumentValidator.validate(document(id = "bad/id"))
    }

    private fun document(overrides: Map<String, Any?> = emptyMap(), id: String = "2026-08-03") = BackupDocument(
        collection = "daily",
        id = id,
        fields = mapOf(
            "schemaVersion" to 1, "steps" to 100L, "integrityTotal" to 100L,
            "integrityEligible" to 80L, "integrityRestricted" to 10L, "integrityExcluded" to 10L,
        ) + overrides,
    )
}
