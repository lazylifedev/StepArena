package com.lazyapps.steparena.activity

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.doublePreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.profileDataStore by preferencesDataStore("activity_profile")

class UserProfileRepository(private val context: Context) {
    val profile: Flow<UserBodyProfile> = context.profileDataStore.data.map {
        UserBodyProfile(
            heightCm = it[HEIGHT],
            weightKg = it[WEIGHT],
            manualStepLengthMeters = it[STEP_LENGTH],
            useAutomaticStepLength = it[AUTO_STEP] ?: true,
        )
    }

    suspend fun current(): UserBodyProfile = profile.first()

    suspend fun save(profile: UserBodyProfile) {
        require(profile.heightCm == null || profile.heightCm.isFinite() && profile.heightCm > 0)
        require(profile.weightKg == null || profile.weightKg.isFinite() && profile.weightKg > 0)
        require(profile.manualStepLengthMeters == null ||
            profile.manualStepLengthMeters.isFinite() && profile.manualStepLengthMeters > 0)
        context.profileDataStore.edit {
            setNullable(it, HEIGHT, profile.heightCm)
            setNullable(it, WEIGHT, profile.weightKg)
            setNullable(it, STEP_LENGTH, profile.manualStepLengthMeters)
            it[AUTO_STEP] = profile.useAutomaticStepLength
        }
    }

    private fun <T> setNullable(
        preferences: androidx.datastore.preferences.core.MutablePreferences,
        key: androidx.datastore.preferences.core.Preferences.Key<T>,
        value: T?,
    ) {
        if (value == null) preferences.remove(key) else preferences[key] = value
    }

    private companion object {
        val HEIGHT = doublePreferencesKey("height_cm")
        val WEIGHT = doublePreferencesKey("weight_kg")
        val STEP_LENGTH = doublePreferencesKey("step_length_m")
        val AUTO_STEP = booleanPreferencesKey("auto_step_length")
    }
}
