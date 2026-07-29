package com.lazyapps.steparena.app

import com.lazyapps.steparena.game.LocalGameRepository
import android.content.Context
import java.time.Clock
import java.time.Instant
import java.time.ZoneId

class DebugStepArenaApplication : StepArenaApplication() {
    val debugClock by lazy { PersistentDebugClock(this) }
    override val gameRepository by lazy { LocalGameRepository(this, database, debugClock) }
}

class PersistentDebugClock(private val application: DebugStepArenaApplication) : Clock() {
    private val preferences by lazy {
        application.getSharedPreferences("debug_game_clock", Context.MODE_PRIVATE)
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
        val next = instant().atZone(zone).plusMonths(months).toInstant()
        preferences.edit().putLong("epoch", next.toEpochMilli()).apply()
    }

    fun rollbackHours(hours: Long) {
        preferences.edit().putLong("epoch", instant().minusSeconds(hours * 3_600).toEpochMilli()).apply()
    }

    fun changeZone() {
        val next = if (zone.id == defaultZone) "UTC" else defaultZone
        preferences.edit().putString("zone", next).apply()
    }

    fun reset() {
        preferences.edit()
            .putLong("epoch", System.currentTimeMillis())
            .putString("zone", defaultZone)
            .apply()
    }

    private val defaultZone: String
        get() = ZoneId.systemDefault().id
}
