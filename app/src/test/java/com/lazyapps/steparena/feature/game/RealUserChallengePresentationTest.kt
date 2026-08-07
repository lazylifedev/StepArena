package com.lazyapps.steparena.feature.game

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RealUserChallengePresentationTest {
    @Test
    fun firestoreParticipantFieldsMapToDtoAndMissingValuesAreSafe() {
        val value = realUserPartnerProgressFromFirestore(mapOf(
            "officialSteps" to 12_345L,
            "competitionSteps" to 10_000L,
            "rewardSteps" to 10_000L,
            "result" to "win",
            "syncState" to "synced",
        ))
        assertEquals(12_345L, value.officialSteps)
        assertEquals(10_000L, value.competitionSteps)
        assertEquals(10_000L, value.rewardSteps)
        assertEquals(RealUserResult.WIN, value.result)
        assertEquals(RealUserSyncState.SYNCED, value.syncState)
        assertEquals(0L, realUserPartnerProgressFromFirestore(emptyMap()).officialSteps)
    }

    @Test
    fun allResultAndSyncStatesMapToStableEnums() {
        assertEquals(RealUserResult.WIN, "win".toRealUserResult())
        assertEquals(RealUserResult.LOSS, "loss".toRealUserResult())
        assertEquals(RealUserResult.DRAW, "draw".toRealUserResult())
        assertEquals(RealUserResult.PENDING, "pending".toRealUserResult())
        assertEquals(RealUserResult.UNKNOWN, "other".toRealUserResult())
        assertEquals(RealUserSyncState.SYNCED, "synced".toRealUserSyncState())
        assertEquals(RealUserSyncState.FINALIZED, "finalized".toRealUserSyncState())
        assertEquals(RealUserSyncState.PENDING, "pending".toRealUserSyncState())
        assertEquals(RealUserSyncState.UNKNOWN, "other".toRealUserSyncState())
        assertEquals(RealUserChallengeStatus.FINALIZED, "completed".toRealUserChallengeStatus())
    }

    @Test
    fun activeUsesOfficialStepsEvenWhenCompetitionStepsIsZero() {
        val value = RealUserPartnerProgress(officialSteps = 12_345, competitionSteps = 0)
        assertEquals(12_345L, realUserDisplayedSteps(value, RealUserChallengeStatus.ACTIVE))
        assertFalse(realUserShowsFinalizedDetails(RealUserChallengeStatus.ACTIVE))
    }

    @Test
    fun activeCompetitionDisplayUsesTheHundredThousandStepCap() {
        val value = RealUserPartnerProgress(officialSteps = 105_000, competitionSteps = 0)
        assertEquals(100_000L, realUserDisplayedSteps(value, RealUserChallengeStatus.ACTIVE))
    }

    @Test
    fun finalizedUsesCompetitionStepsAndShowsFinalizedDetails() {
        val value = RealUserPartnerProgress(officialSteps = 12_345, competitionSteps = 10_000, rewardSteps = 10_000, result = RealUserResult.DRAW)
        assertEquals(10_000L, realUserDisplayedSteps(value, RealUserChallengeStatus.FINALIZED))
        assertTrue(realUserShowsFinalizedDetails(RealUserChallengeStatus.FINALIZED))
    }
}
