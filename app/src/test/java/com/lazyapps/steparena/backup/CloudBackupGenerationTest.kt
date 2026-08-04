package com.lazyapps.steparena.backup

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

    private fun validRoot(
        generation: Long = 5L,
        status: String = "complete",
    ) = CloudRootMetadata(
        exists = true,
        schemaVersion = 2L,
        backupGeneration = generation,
        backupStatus = status,
        hasRequiredFields = true,
    )
}
