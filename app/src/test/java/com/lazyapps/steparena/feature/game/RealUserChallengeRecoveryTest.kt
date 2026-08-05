package com.lazyapps.steparena.feature.game

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class RealUserChallengeRecoveryTest {
    @Test fun storeSavesAndLoadsChallengeIdAndOwnerHash() = runBlocking {
        val store = FakeStore()
        store.save("challenge-a", "uid-a")
        assertEquals(SavedRealUserChallenge("challenge-a", realUserOwnerHash("uid-a")), store.read())
    }

    @Test fun sameAccountIsEligibleAndDifferentAccountIsNot() = runBlocking {
        val store = FakeStore()
        store.save("challenge-a", "uid-a")
        val saved = requireNotNull(store.read())
        assertTrue(store.belongsTo(saved, "uid-a"))
        assertFalse(store.belongsTo(saved, "uid-b"))
    }

    @Test fun permanentRecoveryFailuresAreDistinctFromTransientFailures() {
        assertEquals(RealUserChallengeRecoveryError.PERMANENT, RealUserChallengeRecoveryError.PERMANENT)
        assertEquals(RealUserChallengeRecoveryError.TRANSIENT, RealUserChallengeRecoveryError.TRANSIENT)
    }

    @Test fun missingChallengeAndParticipantCasesHaveNoValidParticipantPair() {
        assertNull(validateRealUserParticipantIds(emptyList<Any?>(), "uid-a"))
        assertNull(validateRealUserParticipantIds(listOf("uid-a"), "uid-a"))
        assertNull(validateRealUserParticipantIds(listOf("uid-a", "uid-a"), "uid-a"))
        assertNull(validateRealUserParticipantIds(listOf("uid-a", 42), "uid-a"))
        assertNull(validateRealUserParticipantIds(listOf("uid-b", "uid-c"), "uid-a"))
    }

    @Test fun validPairHasExactlyOneOpponent() {
        assertEquals("uid-b", validateRealUserParticipantIds(listOf("uid-a", "uid-b"), "uid-a")?.opponentUid)
    }

    @Test fun activeFinalizedAndCompletedStatusMappingIsStable() {
        assertEquals(RealUserChallengeStatus.ACTIVE, "active".toRealUserChallengeStatus())
        assertEquals(RealUserChallengeStatus.FINALIZED, "finalized".toRealUserChallengeStatus())
        assertEquals(RealUserChallengeStatus.FINALIZED, "completed".toRealUserChallengeStatus())
    }

    @Test fun nextChallengeIsShownOnlyForFinalized() {
        assertFalse(realUserShouldShowNextChallenge(RealUserChallengeStatus.ACTIVE))
        assertTrue(realUserShouldShowNextChallenge(RealUserChallengeStatus.FINALIZED))
    }

    @Test fun clearRemovesStoredChallengeForNextChallengeAndAccountSwitch() = runBlocking {
        val store = FakeStore()
        store.save("challenge-a", "uid-a")
        store.clear()
        assertNull(store.read())
    }

    @Test fun storageContractCanBeUsedWithoutDataStore() = runBlocking {
        val store: RealUserChallengeStore = FakeStore()
        store.save("challenge-a", "uid-a")
        assertSame(store, store)
    }

    private class FakeStore : RealUserChallengeStore {
        private var value: SavedRealUserChallenge? = null
        override suspend fun read() = value
        override suspend fun save(challengeId: String, uid: String) {
            value = SavedRealUserChallenge(challengeId, realUserOwnerHash(uid))
        }
        override suspend fun clear() { value = null }
        override fun belongsTo(saved: SavedRealUserChallenge, uid: String) = saved.ownerHash == realUserOwnerHash(uid)
    }
}
