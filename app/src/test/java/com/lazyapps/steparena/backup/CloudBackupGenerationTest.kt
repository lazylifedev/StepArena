package com.lazyapps.steparena.backup

import com.google.firebase.Timestamp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class CloudBackupGenerationTest {
    @Test fun missingCloudRootAlwaysStartsAtOneRegardlessOfLocalCache() {
        listOf(0L, 100L).forEach { ignoredLocalGeneration ->
            assertEquals(1L, CloudRootMetadata(exists = false).nextGeneration())
            assertEquals(ignoredLocalGeneration, ignoredLocalGeneration)
        }
    }

    @Test fun completeCloudRootIsSoleGenerationSource() {
        listOf(0L, 100L).forEach { ignoredLocalGeneration ->
            val root = validRoot(generation = 5L, status = "complete")
            assertEquals(6L, root.nextGeneration())
            assertEquals(ignoredLocalGeneration, ignoredLocalGeneration)
        }
    }

    @Test fun inProgressCloudRootDoesNotAllocateAnotherGeneration() {
        assertThrows(CloudBackupAlreadyInProgressException::class.java) {
            validRoot(generation = 5L, status = "in_progress").nextGeneration()
        }
    }

    @Test fun invalidCloudRootIsNeverGuessedOrRepaired() {
        listOf(
            validRoot().copy(schemaVersion = 1L),
            validRoot().copy(backupGeneration = null),
            validRoot().copy(backupGeneration = -1L),
            validRoot().copy(backupStatus = "unknown"),
            validRoot().copy(hasRequiredFields = false),
        ).forEach { root ->
            assertThrows(InvalidCloudBackupRootException::class.java) { root.nextGeneration() }
        }
    }

    @Test fun maxGenerationFailsWithoutWritingAReplacementValue() {
        assertThrows(InvalidCloudBackupRootException::class.java) {
            validRoot(generation = Long.MAX_VALUE).nextGeneration()
        }
    }

    @Test fun activeLeaseIsNotTakenOver() {
        assertThrows(CloudBackupAlreadyInProgressException::class.java) {
            validRoot(status = "in_progress", leaseUpdatedAt = ts(1_000)).beginDecision(ts(1_000 + 29 * 60))
        }
    }

    @Test fun staleLeaseAllocatesNextGeneration() {
        assertEquals(
            BeginLeaseDecision(6L, true),
            validRoot(status = "in_progress", leaseUpdatedAt = ts(1_000)).beginDecision(ts(1_000 + 30 * 60)),
        )
    }

    @Test fun legacyInProgressUsesValidStartedAtOnly() {
        val legacy = validRoot(status = "in_progress", leaseUpdatedAt = null).copy(
            leaseVersion = null,
            backupOperationId = null,
            backupStartedAt = ts(1_000),
            hasAnyLeaseField = false,
        )
        assertEquals(BeginLeaseDecision(6L, true), legacy.beginDecision(ts(2_800)))
        assertThrows(InvalidCloudBackupRootException::class.java) {
            legacy.copy(backupStartedAt = null).beginDecision(ts(2_800))
        }
    }

    private fun validRoot(
        generation: Long = 5L,
        status: String = "complete",
        leaseUpdatedAt: Timestamp? = ts(1_000),
    ) = CloudRootMetadata(
        exists = true,
        schemaVersion = 2L,
        backupGeneration = generation,
        backupStatus = status,
        leaseVersion = 1L,
        backupOperationId = "11111111-1111-4111-8111-111111111111",
        leaseUpdatedAt = leaseUpdatedAt,
        backupStartedAt = ts(1_000),
        hasAnyLeaseField = true,
        hasRequiredFields = true,
    )

    private fun ts(seconds: Long) = Timestamp(seconds, 0)
}
