package com.lazyapps.steparena.backup

import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Test

class RestoreFailureDiagnosticTest {
    @Test fun countMismatchPreservesFirstCollectionAndSafePath() {
        val tracker = RestoreDiagnosticTracker().apply {
            stage = RestorePreviewStage.VALIDATE_DOCUMENT_COUNTS
            operation = FirestoreOperation.GET
            pathTemplate = "userBackups/{uid}/versions/v2/daily/{documentId}"
            metadata = RestoreMetadata(6, Instant.EPOCH, 2, mapOf("daily" to 0))
        }
        val error = assertThrows(IllegalArgumentException::class.java) { tracker.validateCount("daily", 1) }
        val diagnostic = tracker.build(error)
        assertEquals("CURRENT_GENERATION_COUNT_MISMATCH", diagnostic.validationReason)
        assertEquals(0, diagnostic.collections.getValue("daily").expectedCount)
        assertEquals(1, diagnostic.collections.getValue("daily").actualCount)
        assertFalse(diagnostic.toString().contains("real-user-id"))
    }

    @Test fun rootChangeAndParseFailureRemainDistinct() {
        val root = RestoreDiagnosticTracker().apply {
            stage = RestorePreviewStage.VERIFY_ROOT_UNCHANGED
            rootChanged = true
        }.build(IllegalArgumentException("generation_changed"))
        val parse = RestoreDiagnosticTracker().apply {
            stage = RestorePreviewStage.READ_SETTINGS
        }.build(IllegalStateException("dailyStepGoal_missing"))
        assertEquals("ROOT_CHANGED_DURING_READ", root.validationReason)
        assertEquals("dailyStepGoal", parse.failedField)
        assertEquals("Absent", parse.actualType)
    }

    @Test fun diagnosticNeverNeedsFirestoreOrPayloadValues() {
        val diagnostic = RestoreFailureDiagnostic(
            RestorePreviewStage.READ_DAILY, FirestoreOperation.GET,
            "userBackups/{uid}/versions/v2/daily/{documentId}", null, null, 2, 6,
            "FIELD_TYPE_MISMATCH", "steps", "Long", "String", false,
            mapOf("daily" to RestoreCollectionDiagnostic(1, 1, 0, setOf("ABSENT"), mapOf("steps" to "String"))),
        )
        val rendered = diagnostic.toString()
        listOf("token-value", "private-document-id", "private-payload").forEach { assertFalse(rendered.contains(it)) }
    }
}
