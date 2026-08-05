package com.lazyapps.steparena.auth

import android.app.Activity
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.GetCredentialCancellationException
import androidx.credentials.exceptions.NoCredentialException
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential

sealed interface GoogleCredentialResult {
    data class Success(val idToken: String) : GoogleCredentialResult
    data object Cancelled : GoogleCredentialResult
    data object ConfigurationError : GoogleCredentialResult
    data object GeneralError : GoogleCredentialResult
}

class GoogleCredentialProvider(private val activity: Activity) {
    private val manager = CredentialManager.create(activity)

    suspend fun clearState() {
        manager.clearCredentialState(androidx.credentials.ClearCredentialStateRequest())
    }

    suspend fun request(serverClientId: String): GoogleCredentialResult {
        if (serverClientId.isBlank()) return GoogleCredentialResult.ConfigurationError
        return try {
            request(serverClientId, authorizedOnly = true)
        } catch (_: NoCredentialException) {
            try {
                request(serverClientId, authorizedOnly = false)
            } catch (_: GetCredentialCancellationException) {
                GoogleCredentialResult.Cancelled
            } catch (_: Throwable) {
                GoogleCredentialResult.GeneralError
            }
        } catch (_: GetCredentialCancellationException) {
            GoogleCredentialResult.Cancelled
        } catch (_: Throwable) {
            GoogleCredentialResult.GeneralError
        }
    }

    private suspend fun request(serverClientId: String, authorizedOnly: Boolean): GoogleCredentialResult {
        val option = GetGoogleIdOption.Builder()
            .setFilterByAuthorizedAccounts(authorizedOnly)
            .setServerClientId(serverClientId)
            .build()
        val response = manager.getCredential(activity, GetCredentialRequest.Builder().addCredentialOption(option).build())
        val credential = response.credential
        if (credential !is CustomCredential || credential.type != GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL) {
            return GoogleCredentialResult.ConfigurationError
        }
        return try {
            GoogleCredentialResult.Success(GoogleIdTokenCredential.createFrom(credential.data).idToken)
        } catch (_: Throwable) {
            GoogleCredentialResult.ConfigurationError
        }
    }
}
