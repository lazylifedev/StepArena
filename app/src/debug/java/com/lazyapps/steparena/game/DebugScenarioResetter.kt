package com.lazyapps.steparena.game

import androidx.room.withTransaction
import com.lazyapps.steparena.app.DebugStepArenaApplication
import com.lazyapps.steparena.core.database.entity.GamePlayerProfileEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

interface DebugScenarioResetter {
    suspend fun resetAllScenarioData()
    suspend fun resetActivityData()
    suspend fun resetGameData()
    suspend fun resetNotificationData()
}

class RoomDebugScenarioResetter(
    private val application: DebugStepArenaApplication,
) : DebugScenarioResetter {
    private val database get() = application.debugDatabase

    override suspend fun resetAllScenarioData() {
        withContext(Dispatchers.IO) {
            database.clearAllTables()
        }
        application.debugStateStore.clearScenarioSettings()
        application.debugClock.reset()
        initializeProfile()
    }

    override suspend fun resetActivityData() {
        database.withTransaction {
            val sql = database.openHelper.writableDatabase
            ACTIVITY_TABLES.forEach { sql.execSQL("DELETE FROM $it") }
        }
    }

    override suspend fun resetGameData() {
        database.withTransaction {
            val sql = database.openHelper.writableDatabase
            GAME_TABLES.forEach { sql.execSQL("DELETE FROM $it") }
        }
        initializeProfile()
    }

    override suspend fun resetNotificationData() {
        database.openHelper.writableDatabase.execSQL("DELETE FROM game_notification_events")
    }

    private suspend fun initializeProfile() {
        val now = application.debugClock.millis()
        database.gamePlayerProfile().upsert(
            GamePlayerProfileEntity(createdAtEpochMillis = now, updatedAtEpochMillis = now),
        )
    }

    private companion object {
        val ACTIVITY_TABLES = listOf(
            "hourly_activity_records",
            "daily_activity_records",
            "walking_sessions",
            "activity_processing_state",
            "tracking_gap_records",
            "processed_external_step_records",
        )
        val GAME_TABLES = listOf(
            "daily_matches",
            "weekly_leagues",
            "game_seasons",
            "achievement_unlocks",
            "game_notification_events",
            "game_player_profile",
        )
    }
}
