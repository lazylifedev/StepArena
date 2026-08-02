package com.lazyapps.steparena.recovery

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.first

private val Context.recoverySettingsStore by preferencesDataStore("recovery_settings")

data class RecoverySettings(
    val healthConnectEnabled: Boolean = false,
    val automaticRecovery: Boolean = false,
    val confirmBeforeRecovery: Boolean = true,
    val trackingStopWarnings: Boolean = true,
    val recoverExplicitStops: Boolean = false,
)

class RecoverySettingsRepository(private val context: Context) {
    val settings: Flow<RecoverySettings> = context.recoverySettingsStore.data.map {
        RecoverySettings(
            healthConnectEnabled = it[HEALTH] ?: false,
            automaticRecovery = it[AUTO] ?: false,
            confirmBeforeRecovery = it[CONFIRM] ?: true,
            trackingStopWarnings = it[WARNINGS] ?: true,
            recoverExplicitStops = it[EXPLICIT] ?: false,
        )
    }

    suspend fun update(value: RecoverySettings) {
        context.recoverySettingsStore.edit {
            it[HEALTH] = value.healthConnectEnabled
            it[AUTO] = value.automaticRecovery
            it[CONFIRM] = value.confirmBeforeRecovery
            it[WARNINGS] = value.trackingStopWarnings
            it[EXPLICIT] = value.recoverExplicitStops
        }
    }

    suspend fun current(): RecoverySettings = settings.first()

    suspend fun reset() { context.recoverySettingsStore.edit { it.clear() } }

    private companion object {
        val HEALTH = booleanPreferencesKey("health_connect_enabled")
        val AUTO = booleanPreferencesKey("automatic_recovery")
        val CONFIRM = booleanPreferencesKey("confirm_before_recovery")
        val WARNINGS = booleanPreferencesKey("tracking_stop_warnings")
        val EXPLICIT = booleanPreferencesKey("recover_explicit_stops")
    }
}
