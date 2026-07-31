package com.lazyapps.steparena.game

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.lazyapps.steparena.app.DebugStepArenaApplication
import com.lazyapps.steparena.core.database.StepArenaDatabase
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class DebugDataIsolationInstrumentationTest {
    private lateinit var context: Context
    private lateinit var production: StepArenaDatabase
    private lateinit var debug: StepArenaDatabase

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        context.deleteDatabase(PRODUCTION_NAME)
        context.deleteDatabase(DebugStepArenaApplication.DEBUG_DATABASE_NAME)
        production = StepArenaDatabase.build(context, PRODUCTION_NAME)
        debug = StepArenaDatabase.build(context, DebugStepArenaApplication.DEBUG_DATABASE_NAME)
    }

    @After
    fun tearDown() {
        production.close()
        debug.close()
        context.deleteDatabase(PRODUCTION_NAME)
        context.deleteDatabase(DebugStepArenaApplication.DEBUG_DATABASE_NAME)
    }

    @Test
    fun debugMutationsAndResetNeverChangeProductionSentinels() = runBlocking {
        insertSentinels(production, rating = 1_234, wins = 7, steps = 321)
        insertSentinels(debug, rating = 1_025, wins = 1, steps = 5_000)

        assertEquals(1_234, production.gamePlayerProfile().get()?.rating)
        assertEquals(7, production.gamePlayerProfile().get()?.wins)
        assertEquals(321L, production.daily().get(DATE, ZONE)?.steps)
        assertEquals(1_025, debug.gamePlayerProfile().get()?.rating)
        assertEquals(5_000L, debug.daily().get(DATE, ZONE)?.steps)

        debug.clearAllTables()

        assertNull(debug.gamePlayerProfile().get())
        assertNull(debug.daily().get(DATE, ZONE))
        assertEquals(1_234, production.gamePlayerProfile().get()?.rating)
        assertEquals(7, production.gamePlayerProfile().get()?.wins)
        assertEquals(321L, production.daily().get(DATE, ZONE)?.steps)
        assertEquals("production-match", scalar(production, "SELECT id FROM daily_matches LIMIT 1"))
        assertEquals("production-event", scalar(production, "SELECT id FROM game_notification_events LIMIT 1"))
        assertEquals("production-achievement", scalar(production, "SELECT achievementId FROM achievement_unlocks LIMIT 1"))
    }

    private fun insertSentinels(database: StepArenaDatabase, rating: Int, wins: Int, steps: Int) {
        val sql = database.openHelper.writableDatabase
        sql.execSQL(
            """INSERT INTO game_player_profile
                (id,displayName,rating,rankTier,rankDivision,totalMatches,wins,losses,draws,
                noContests,currentWinStreak,bestWinStreak,currentLossStreak,
                beginnerMatchesRemaining,lastOutcome,createdAtEpochMillis,updatedAtEpochMillis)
                VALUES ('local_player',NULL,$rating,'BRONZE',3,7,$wins,0,0,0,1,1,0,0,'WIN',1,1)""",
        )
        sql.execSQL(
            """INSERT INTO daily_activity_records
                (id,localDate,zoneId,steps,unclassifiedSteps,unclassifiedStepsQuality,
                externalRecoveredSteps,unallocatedMeasuredSteps,distanceMeters,walkingDurationSeconds,
                estimatedCaloriesKcal,averageWalkingSpeedKmh,stepsQuality,distanceQuality,durationQuality,
                caloriesQuality,speedQuality,activeHourCount,walkingSessionCount,finalized,
                finalizedAtEpochMillis,createdAtEpochMillis,updatedAtEpochMillis) VALUES
                ('$DATE|$ZONE','$DATE','$ZONE',$steps,0,'UNKNOWN',0,0,$steps*0.7,3600,10.0,3.0,
                'MEASURED','ESTIMATED','MEASURED','ESTIMATED','ESTIMATED',
                1,0,0,NULL,1,1)""",
        )
        if (database === production) {
            sql.execSQL(
                """INSERT INTO daily_matches VALUES
                    ('production-match','$DATE','$ZONE','2026-07','DAILY','ACTIVE',NULL,
                    'npc','NPC','avatar','BRONZE',3,'STEADY',4000,0,0,0,0,'','FULL',
                    $rating,NULL,NULL,NULL,NULL,1,1)""",
            )
            sql.execSQL(
                """INSERT INTO game_notification_events VALUES
                    ('production-event','MATCH_RESULT','production-match','production-key',
                    'title','message','match',1,1,NULL,0)""",
            )
            sql.execSQL(
                """INSERT INTO achievement_unlocks VALUES
                    ('production-achievement',1,1,NULL,0)""",
            )
        }
    }

    private fun scalar(database: StepArenaDatabase, query: String): String? =
        database.openHelper.readableDatabase.query(query).use {
            if (it.moveToFirst()) it.getString(0) else null
        }

    private companion object {
        const val PRODUCTION_NAME = "step_arena_isolation_production_test.db"
        const val DATE = "2026-07-29"
        const val ZONE = "Asia/Tokyo"
    }
}
