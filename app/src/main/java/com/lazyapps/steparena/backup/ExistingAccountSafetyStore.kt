package com.lazyapps.steparena.backup

import android.content.Context
import java.security.MessageDigest

/** Keeps automatic backup disabled after an explicit switch to an existing cloud account. */
class ExistingAccountSafetyStore(context: Context) {
    private val preferences = context.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE)

    fun markPendingReview(uid: String) {
        preferences.edit().putString(KEY_UID_HASH, uid.sha256()).commit()
    }

    fun isPendingReview(uid: String): Boolean =
        preferences.getString(KEY_UID_HASH, null) == uid.sha256()

    fun allowExplicitBackup(uid: String) {
        if (isPendingReview(uid)) preferences.edit().remove(KEY_UID_HASH).commit()
    }

    private fun String.sha256(): String = MessageDigest.getInstance("SHA-256")
        .digest(toByteArray(Charsets.UTF_8)).joinToString("") { "%02x".format(it) }

    private companion object {
        const val PREFERENCES = "existing_account_safety"
        const val KEY_UID_HASH = "pending_review_uid_hash"
    }
}
