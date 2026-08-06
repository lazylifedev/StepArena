package com.lazyapps.steparena.feature.game

import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Before
import org.junit.Test

class RealUserChallengeLocalStoreTest {
    private val store = RealUserChallengeLocalStore(ApplicationProvider.getApplicationContext())

    @Before fun clearBefore() = runBlocking { store.clear() }
    @After fun clearAfter() = runBlocking { store.clear() }

    @Test fun dataStoreRoundTripsChallengeIdAndOwnerHash() = runBlocking {
        store.save("challenge-test", "uid-test")
        val saved = store.read()
        assertNotNull(saved)
        assertEquals("challenge-test", saved?.challengeId)
        assertEquals(realUserOwnerHash("uid-test"), saved?.ownerHash)
    }
}
