package com.lazyapps.steparena.backup

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import com.google.firebase.firestore.FieldValue

class BackupFailureDiagnosticTest {
    @Test fun permissionFailurePreservesSafeStructuredDiagnostic() {
        val uid = "sensitive-user-uid-123456"
        val documentId = "sensitive-document-id-123456"
        val token = "eyJhbGciOiJIUzI1NiJ9.sensitive.payload"
        val payloadValue = "private-payload-value"
        val diagnostic = BackupFailureDiagnostic(
            stage = BackupWriteStage.DAILY,
            operation = FirestoreOperation.COMMIT,
            pathTemplate = "userBackups/{uid}/versions/v2/daily/{documentId}",
            inTransaction = true,
            firestoreCode = "PERMISSION_DENIED",
            sanitizedMessage = sanitizeFirestoreMessage(
                "PERMISSION_DENIED: Missing or insufficient permissions. uid=$uid doc=$documentId token=$token",
            ),
            requestFieldTypes = safeFieldTypes(mapOf("steps" to 123L, "note" to payloadValue)),
            existingFieldTypes = mapOf("steps" to "Long"),
            requestFieldCount = 2,
            existingFieldCount = 1,
            writeDisposition = "UPDATE",
            generationDirection = "INCREASE",
            backupStatus = null,
        )
        val error = DiagnosticBackupException(
            diagnostic,
            SecurityException("permission denied"),
        )

        val failure = error.toBackupFailure()

        assertEquals(BackupErrorCategory.PERMISSION, failure.category)
        assertEquals(BackupWriteStage.DAILY, failure.diagnostic?.stage)
        assertEquals(FirestoreOperation.COMMIT, failure.diagnostic?.operation)
        val rendered = failure.toString()
        assertTrue(rendered.contains("{uid}"))
        assertTrue(rendered.contains("{documentId}"))
        assertFalse(rendered.contains(uid))
        assertFalse(rendered.contains(documentId))
        assertFalse(rendered.contains(token))
        assertFalse(rendered.contains(payloadValue))
        assertFalse(rendered.contains("123"))
    }

    @Test fun sanitizerKeepsOnlyAllowlistedGeneralReason() {
        assertEquals(
            "Missing or insufficient permissions.",
            sanitizeFirestoreMessage("rpc error: Missing or insufficient permissions. https://example.test/private"),
        )
        assertEquals("PERMISSION_DENIED", sanitizeFirestoreMessage("permission_denied for private/resource"))
        assertNull(sanitizeFirestoreMessage("unexpected private detail"))
    }

    @Test fun fieldTypesContainNamesAndTypesButNeverValues() {
        val types = safeFieldTypes(mapOf(
            "schemaVersion" to 2, "backupStatus" to "complete", "missing" to null,
            "startedAt" to FieldValue.serverTimestamp(), "completedAt" to FieldValue.delete(),
        ))
        assertEquals(mapOf(
            "backupStatus" to "String", "completedAt" to "DeleteSentinel", "missing" to "Null",
            "schemaVersion" to "Long", "startedAt" to "ServerTimestamp",
        ), types)
        val rendered = types.toString()
        assertFalse(rendered.contains("=complete"))
        assertFalse(rendered.contains("=2"))
    }
}
