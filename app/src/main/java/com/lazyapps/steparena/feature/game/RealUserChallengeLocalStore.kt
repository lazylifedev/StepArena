package com.lazyapps.steparena.feature.game

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.first
import java.security.MessageDigest

private val Context.realUserChallengeDataStore by preferencesDataStore("real_user_challenge")

data class SavedRealUserChallenge(val challengeId: String, val ownerHash: String)

class RealUserChallengeLocalStore(private val context: Context) {
    suspend fun read(): SavedRealUserChallenge? {
        val values = context.realUserChallengeDataStore.data.first()
        val challengeId = values[Keys.CHALLENGE_ID] ?: return null
        val ownerHash = values[Keys.OWNER_HASH] ?: return null
        return SavedRealUserChallenge(challengeId, ownerHash)
    }

    suspend fun save(challengeId: String, uid: String) {
        context.realUserChallengeDataStore.edit {
            it[Keys.CHALLENGE_ID] = challengeId
            it[Keys.OWNER_HASH] = uid.sha256()
        }
    }

    suspend fun clear() {
        context.realUserChallengeDataStore.edit { it.clear() }
    }

    fun belongsTo(saved: SavedRealUserChallenge, uid: String): Boolean =
        saved.ownerHash == uid.sha256()

    private object Keys {
        val CHALLENGE_ID = stringPreferencesKey("challenge_id")
        val OWNER_HASH = stringPreferencesKey("owner_hash")
    }
}

private fun String.sha256(): String =
    MessageDigest.getInstance("SHA-256").digest(toByteArray()).joinToString("") { "%02x".format(it) }
