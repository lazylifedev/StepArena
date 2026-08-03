package com.lazyapps.steparena.auth

import com.google.firebase.FirebaseNetworkException
import com.google.firebase.auth.AuthCredential
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseAuthUserCollisionException
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.GoogleAuthProvider
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.suspendCancellableCoroutine

sealed interface AccountAuthState {
    data object Initializing : AccountAuthState
    data object SigningInAnonymously : AccountAuthState
    data class Anonymous(val error: AuthError? = null) : AccountAuthState
    data class LinkingGoogle(val account: AccountProfile) : AccountAuthState
    data class GoogleLinked(val account: AccountProfile) : AccountAuthState
    data class AccountConflict(val account: AccountProfile) : AccountAuthState
    data class NetworkError(val account: AccountProfile?) : AccountAuthState
    data class ConfigurationError(val account: AccountProfile?) : AccountAuthState
    data class GeneralError(val account: AccountProfile?) : AccountAuthState
}

enum class AuthError { NETWORK, CONFIGURATION, GENERAL }

data class AccountProfile(
    val uid: String,
    val isAnonymous: Boolean,
    val displayName: String? = null,
    val email: String? = null,
    val photoUrl: String? = null,
    val providers: Set<String> = emptySet(),
) {
    val isGoogleLinked: Boolean get() = !isAnonymous && GoogleAuthProvider.PROVIDER_ID in providers
}

sealed interface LinkResult {
    data object Linked : LinkResult
    data object AlreadyLinked : LinkResult
    data object Conflict : LinkResult
    data object NoCurrentUser : LinkResult
    data object NetworkFailure : LinkResult
    data object GeneralFailure : LinkResult
    data object IgnoredWhileBusy : LinkResult
}

interface AuthGateway {
    fun currentUser(): AccountProfile?
    suspend fun signInAnonymously(): AccountProfile
    suspend fun linkGoogle(idToken: String): AccountProfile
    fun addUserListener(listener: (AccountProfile?) -> Unit): AutoCloseable
}

class AccountCollisionException(cause: Throwable? = null) : Exception(cause)
class AuthNetworkException(cause: Throwable? = null) : Exception(cause)

class AccountAuthRepository(
    private val gateway: AuthGateway,
    private val scope: CoroutineScope,
) : AutoCloseable {
    private val started = AtomicBoolean(false)
    private val operationInProgress = AtomicBoolean(false)
    private val credentialLinkStarted = AtomicBoolean(false)
    private var listener: AutoCloseable? = null
    private val mutableState = MutableStateFlow<AccountAuthState>(AccountAuthState.Initializing)
    val state: StateFlow<AccountAuthState> = mutableState.asStateFlow()

    fun initialize() {
        if (!started.compareAndSet(false, true)) return
        listener = gateway.addUserListener { user ->
            if (!operationInProgress.get() && user != null) mutableState.value = user.toState()
        }
        val existing = gateway.currentUser()
        if (existing != null) {
            mutableState.value = existing.toState()
            return
        }
        operationInProgress.set(true)
        mutableState.value = AccountAuthState.SigningInAnonymously
        scope.launch {
            try {
                mutableState.value = gateway.signInAnonymously().toState()
            } catch (error: Throwable) {
                mutableState.value = AccountAuthState.Anonymous(error.classify())
            } finally {
                operationInProgress.set(false)
            }
        }
    }

    fun startGoogleLink(): Boolean {
        val current = gateway.currentUser() ?: run {
            mutableState.value = AccountAuthState.GeneralError(null)
            return false
        }
        if (current.isGoogleLinked) {
            mutableState.value = AccountAuthState.GoogleLinked(current)
            return false
        }
        if (!operationInProgress.compareAndSet(false, true)) return false
        mutableState.value = AccountAuthState.LinkingGoogle(current)
        return true
    }

    suspend fun linkGoogle(idToken: String): LinkResult {
        val current = gateway.currentUser() ?: run {
            cancelPendingGoogleLink(AccountAuthState.GeneralError(null))
            return LinkResult.NoCurrentUser
        }
        if (current.isGoogleLinked) {
            cancelPendingGoogleLink(AccountAuthState.GoogleLinked(current))
            return LinkResult.AlreadyLinked
        }
        if (!operationInProgress.get() && !startGoogleLink()) return LinkResult.IgnoredWhileBusy
        if (!credentialLinkStarted.compareAndSet(false, true)) return LinkResult.IgnoredWhileBusy
        return try {
            val linked = gateway.linkGoogle(idToken)
            check(linked.uid == current.uid) { "Firebase account identity changed during link" }
            mutableState.value = linked.toState()
            LinkResult.Linked
        } catch (_: AccountCollisionException) {
            mutableState.value = AccountAuthState.AccountConflict(current)
            LinkResult.Conflict
        } catch (_: AuthNetworkException) {
            mutableState.value = AccountAuthState.NetworkError(current)
            LinkResult.NetworkFailure
        } catch (_: Throwable) {
            mutableState.value = AccountAuthState.GeneralError(current)
            LinkResult.GeneralFailure
        } finally {
            credentialLinkStarted.set(false)
            operationInProgress.set(false)
        }
    }

    fun googleSelectionCancelled() {
        cancelPendingGoogleLink(gateway.currentUser()?.toState())
    }

    fun configurationFailed() {
        cancelPendingGoogleLink(AccountAuthState.ConfigurationError(gateway.currentUser()))
    }

    fun credentialFailed() {
        cancelPendingGoogleLink(AccountAuthState.GeneralError(gateway.currentUser()))
    }

    private fun cancelPendingGoogleLink(newState: AccountAuthState?) {
        credentialLinkStarted.set(false)
        operationInProgress.set(false)
        if (newState != null) mutableState.value = newState
    }

    override fun close() {
        listener?.close()
        listener = null
    }
}

private fun AccountProfile.toState(): AccountAuthState =
    if (isGoogleLinked) AccountAuthState.GoogleLinked(this) else AccountAuthState.Anonymous()

private fun Throwable.classify(): AuthError = when (this) {
    is FirebaseNetworkException -> AuthError.NETWORK
    is IllegalStateException -> AuthError.CONFIGURATION
    else -> AuthError.GENERAL
}

class FirebaseAuthGateway(private val auth: FirebaseAuth) : AuthGateway {
    override fun currentUser(): AccountProfile? = auth.currentUser?.toProfile()

    override suspend fun signInAnonymously(): AccountProfile =
        auth.signInAnonymously().awaitResult().user?.toProfile()
            ?: error("Anonymous authentication returned no user")

    override suspend fun linkGoogle(idToken: String): AccountProfile {
        val user = auth.currentUser ?: error("No current Firebase user")
        val credential: AuthCredential = GoogleAuthProvider.getCredential(idToken, null)
        return try {
            user.linkWithCredential(credential).awaitResult().user?.reloadAndProfile()
                ?: error("Google link returned no user")
        } catch (error: FirebaseAuthUserCollisionException) {
            throw AccountCollisionException(error)
        } catch (error: FirebaseNetworkException) {
            throw AuthNetworkException(error)
        }
    }

    override fun addUserListener(listener: (AccountProfile?) -> Unit): AutoCloseable {
        val firebaseListener = FirebaseAuth.AuthStateListener { listener(it.currentUser?.toProfile()) }
        auth.addAuthStateListener(firebaseListener)
        return AutoCloseable { auth.removeAuthStateListener(firebaseListener) }
    }
}

private suspend fun FirebaseUser.reloadAndProfile(): AccountProfile {
    reload().awaitResult()
    return toProfile()
}

private fun FirebaseUser.toProfile() = AccountProfile(
    uid = uid,
    isAnonymous = isAnonymous,
    displayName = displayName,
    email = email,
    photoUrl = photoUrl?.toString(),
    providers = providerData.mapNotNull { it.providerId }.toSet(),
)

private suspend fun <T> com.google.android.gms.tasks.Task<T>.awaitResult(): T =
    suspendCancellableCoroutine { continuation ->
        addOnSuccessListener { if (continuation.isActive) continuation.resume(it) }
        addOnFailureListener { if (continuation.isActive) continuation.resumeWithException(it) }
        addOnCanceledListener { continuation.cancel() }
    }
