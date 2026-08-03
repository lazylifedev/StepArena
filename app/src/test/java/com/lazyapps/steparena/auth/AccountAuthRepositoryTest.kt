package com.lazyapps.steparena.auth

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AccountAuthRepositoryTest {
    @Test fun existingUser_skipsAnonymousSignIn() {
        val fake = FakeAuthGateway(anonymous())
        val repository = repository(fake)
        repository.initialize()
        assertEquals(0, fake.anonymousCalls)
        assertTrue(repository.state.value is AccountAuthState.Anonymous)
    }

    @Test fun missingUser_signsInAnonymouslyOnlyOnce() {
        val fake = FakeAuthGateway(null)
        val repository = repository(fake)
        repository.initialize()
        repository.initialize()
        assertEquals(1, fake.anonymousCalls)
        assertTrue(repository.state.value is AccountAuthState.Anonymous)
    }

    @Test fun anonymousFailure_doesNotThrowOrRetry() {
        val fake = FakeAuthGateway(null).apply { anonymousFailure = IllegalStateException("config") }
        val repository = repository(fake)
        repository.initialize()
        assertEquals(1, fake.anonymousCalls)
        assertEquals(AuthError.CONFIGURATION, (repository.state.value as AccountAuthState.Anonymous).error)
    }

    @Test fun googleLink_preservesUidAndPublishesLinkedState() = runBlocking {
        val fake = FakeAuthGateway(anonymous()).apply { linkedUser = google(uid = "stable") }
        val repository = repository(fake)
        repository.initialize()
        assertEquals(LinkResult.Linked, repository.linkGoogle("token-not-logged"))
        assertEquals("stable", (repository.state.value as AccountAuthState.GoogleLinked).account.uid)
        assertEquals(1, fake.linkCalls)
    }

    @Test fun alreadyLinked_doesNotLinkAgain() = runBlocking {
        val fake = FakeAuthGateway(google())
        val repository = repository(fake)
        repository.initialize()
        assertEquals(LinkResult.AlreadyLinked, repository.linkGoogle("unused"))
        assertEquals(0, fake.linkCalls)
    }

    @Test fun collision_keepsAnonymousUserAndDoesNotDelete() = runBlocking {
        val fake = FakeAuthGateway(anonymous()).apply {
            linkFailure = AccountCollisionException()
        }
        val repository = repository(fake)
        repository.initialize()
        assertEquals(LinkResult.Conflict, repository.linkGoogle("token"))
        assertTrue(repository.state.value is AccountAuthState.AccountConflict)
        assertTrue(fake.currentUser()!!.isAnonymous)
    }

    @Test fun cancellation_restoresAnonymousAndListenerIsRemovedOnClose() {
        val fake = FakeAuthGateway(anonymous())
        val repository = repository(fake)
        repository.initialize()
        repository.configurationFailed()
        repository.googleSelectionCancelled()
        assertTrue(repository.state.value is AccountAuthState.Anonymous)
        repository.close()
        assertTrue(fake.listenerClosed)
    }

    @Test fun concurrentLink_isNotExecutedTwice() = runBlocking {
        val fake = FakeAuthGateway(anonymous()).apply { holdLink = true }
        val repository = repository(fake)
        repository.initialize()
        val first = async(Dispatchers.Default) { repository.linkGoogle("first") }
        while (fake.linkCalls == 0) Thread.yield()
        assertEquals(LinkResult.IgnoredWhileBusy, repository.linkGoogle("second"))
        fake.releaseLink()
        first.await()
        assertEquals(1, fake.linkCalls)
    }

    @Test fun chooserPhase_rejectsSecondTapAndCancelReenablesAction() {
        val fake = FakeAuthGateway(anonymous())
        val repository = repository(fake)
        repository.initialize()
        assertTrue(repository.startGoogleLink())
        assertFalse(repository.startGoogleLink())
        assertTrue(repository.state.value is AccountAuthState.LinkingGoogle)
        repository.googleSelectionCancelled()
        assertTrue(repository.startGoogleLink())
    }

    private fun repository(fake: FakeAuthGateway) =
        AccountAuthRepository(fake, CoroutineScope(Dispatchers.Unconfined))

    private fun anonymous(uid: String = "stable") = AccountProfile(uid, isAnonymous = true)
    private fun google(uid: String = "stable") = AccountProfile(
        uid, isAnonymous = false, providers = setOf("google.com"), email = "masked@example.invalid",
    )
}

private class FakeAuthGateway(private var user: AccountProfile?) : AuthGateway {
    var anonymousCalls = 0
    var linkCalls = 0
    var anonymousFailure: Throwable? = null
    var linkFailure: Throwable? = null
    var linkedUser: AccountProfile = AccountProfile("stable", false, providers = setOf("google.com"))
    var listenerClosed = false
    var holdLink = false
    private val linkGate = java.util.concurrent.CountDownLatch(1)

    override fun currentUser() = user
    override suspend fun signInAnonymously(): AccountProfile {
        anonymousCalls++
        anonymousFailure?.let { throw it }
        return AccountProfile("stable", true).also { user = it }
    }
    override suspend fun linkGoogle(idToken: String): AccountProfile {
        linkCalls++
        if (holdLink) linkGate.await()
        linkFailure?.let { throw it }
        return linkedUser.also { user = it }
    }
    fun releaseLink() = linkGate.countDown()
    override fun addUserListener(listener: (AccountProfile?) -> Unit) = AutoCloseable { listenerClosed = true }
}
