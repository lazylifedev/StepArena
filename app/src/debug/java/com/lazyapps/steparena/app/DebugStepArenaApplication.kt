package com.lazyapps.steparena.app

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.lazyapps.steparena.activity.ActivityRepository
import com.lazyapps.steparena.core.database.StepArenaDatabase
import com.lazyapps.steparena.game.DebugGameMaintenanceWorker
import com.lazyapps.steparena.game.LocalGameRepository
import com.lazyapps.steparena.game.GameNotificationConfig
import com.lazyapps.steparena.recovery.TrackingHealthWorker
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.runBlocking
import java.time.Clock
import java.time.Instant
import java.time.ZoneId

enum class DebugDataMode {
    NORMAL_DATA,
    ISOLATED_SCENARIO,
}

private val Context.debugScenarioDataStore by preferencesDataStore("step_arena_debug_scenario")

class DebugScenarioStateStore(private val context: Context) {
    private val modeKey = stringPreferencesKey("data_mode")

    fun currentMode(): DebugDataMode = runBlocking {
        context.debugScenarioDataStore.data.map { preferences ->
            preferences[modeKey]?.let(DebugDataMode::valueOf) ?: DebugDataMode.NORMAL_DATA
        }.first()
    }

    suspend fun setMode(mode: DebugDataMode) {
        context.debugScenarioDataStore.edit { it[modeKey] = mode.name }
    }

    suspend fun clearScenarioSettings() {
        context.debugScenarioDataStore.edit { preferences ->
            val retainedMode = preferences[modeKey]
            preferences.clear()
            if (retainedMode != null) preferences[modeKey] = retainedMode
        }
    }
}

class DebugStepArenaApplication : StepArenaApplication() {
    companion object {
        const val DEBUG_DATABASE_NAME = "step_arena_debug_game.db"
        const val DEBUG_INSTALLATION_ID = "step-arena-debug-scenario"
    }

    val debugStateStore by lazy { DebugScenarioStateStore(this) }
    val dataMode: DebugDataMode get() = debugStateStore.currentMode()
    val isIsolatedScenario: Boolean get() = dataMode == DebugDataMode.ISOLATED_SCENARIO
    private val productionDatabase by lazy { StepArenaDatabase.get(this) }
    val debugDatabase by lazy { StepArenaDatabase.build(this, DEBUG_DATABASE_NAME) }
    private val productionActivityRepository by lazy {
        ActivityRepository(productionDatabase, profileRepository)
    }
    private val debugActivityRepository by lazy {
        ActivityRepository(debugDatabase, profileRepository)
    }
    val debugClock by lazy { PersistentDebugClock(this) }
    override val productionGameRepository by lazy {
        LocalGameRepository(this, productionDatabase, Clock.systemDefaultZone())
    }
    private val debugGameRepository by lazy {
        LocalGameRepository(
            this,
            debugDatabase,
            debugClock,
            installationIdOverride = DEBUG_INSTALLATION_ID,
            notificationConfig = GameNotificationConfig(
                preferences = "step_arena_debug_game_notifications",
                channelId = "debug_game_results",
                channelName = "Debugゲーム結果",
                group = "step_arena_debug_game",
                titlePrefix = "[隔離テスト] ",
                requestCodeSalt = 0x5A17,
                dataArea = "debug_scenario",
            ),
        )
    }

    override val database: StepArenaDatabase
        get() = if (isIsolatedScenario) debugDatabase else productionDatabase
    override val activityRepository: ActivityRepository
        get() = if (isIsolatedScenario) debugActivityRepository else productionActivityRepository
    override val clock: Clock
        get() = if (isIsolatedScenario) debugClock else Clock.systemDefaultZone()
    override val installationId: String?
        get() = if (isIsolatedScenario) DEBUG_INSTALLATION_ID else null
    override val isolatedScenario: Boolean get() = isIsolatedScenario
    override val gameRepository: LocalGameRepository
        get() = if (isIsolatedScenario) debugGameRepository else productionGameRepository

    override fun scheduleBackgroundWork() {
        if (isIsolatedScenario) {
            DebugGameMaintenanceWorker.schedule(this)
        } else {
            TrackingHealthWorker.schedule(this)
            com.lazyapps.steparena.game.GameMaintenanceWorker.schedule(this)
        }
    }
}

class PersistentDebugClock(private val application: DebugStepArenaApplication) : Clock() {
    private val preferences by lazy {
        application.getSharedPreferences("step_arena_debug_clock", Context.MODE_PRIVATE)
    }

    override fun getZone(): ZoneId =
        ZoneId.of(preferences.getString("zone", defaultZone) ?: defaultZone)

    override fun withZone(zone: ZoneId): Clock = apply {
        preferences.edit().putString("zone", zone.id).apply()
    }

    override fun instant(): Instant =
        Instant.ofEpochMilli(preferences.getLong("epoch", System.currentTimeMillis()))

    fun advanceDays(days: Long) {
        preferences.edit().putLong("epoch", instant().plusSeconds(days * 86_400).toEpochMilli()).apply()
    }

    fun advanceMonths(months: Long) {
        preferences.edit().putLong("epoch", instant().atZone(zone).plusMonths(months).toInstant().toEpochMilli()).apply()
    }

    fun rollbackHours(hours: Long) {
        preferences.edit().putLong("epoch", instant().minusSeconds(hours * 3_600).toEpochMilli()).apply()
    }

    fun changeZone() {
        preferences.edit().putString("zone", if (zone.id == defaultZone) "UTC" else defaultZone).apply()
    }

    fun reset() {
        preferences.edit().clear().apply()
    }

    private val defaultZone: String get() = ZoneId.systemDefault().id
}
