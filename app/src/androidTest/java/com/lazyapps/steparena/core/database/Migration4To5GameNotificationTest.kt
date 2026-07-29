package com.lazyapps.steparena.core.database

import androidx.room.testing.MigrationTestHelper
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class Migration4To5GameNotificationTest {
    private val databaseName = "migration-4-5-notification"

    @get:Rule
    val helper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        StepArenaDatabase::class.java,
    )

    @Test
    fun migrationCreatesNotificationTableAndPreservesGameData() {
        helper.createDatabase(databaseName, 4).apply {
            insertProfile(this)
            close()
        }
        helper.runMigrationsAndValidate(databaseName, 5, true, StepArenaDatabase.MIGRATION_4_5)
            .use { db ->
                db.query("SELECT rating FROM game_player_profile").use {
                    it.moveToFirst()
                    assertEquals(1234, it.getInt(0))
                }
                db.execSQL(
                    """INSERT INTO game_notification_events
                        (id,type,sourceId,deduplicationKey,title,message,destinationRoute,
                        createdAtEpochMillis,notBeforeEpochMillis,deliveredAtEpochMillis,acknowledged)
                        VALUES ('e1','MATCH_RESULT','m1','match:m1','result','win','match',1,1,NULL,0)""",
                )
                db.query("SELECT COUNT(*) FROM game_notification_events").use {
                    it.moveToFirst()
                    assertEquals(1, it.getInt(0))
                }
            }
    }

    private fun insertProfile(db: SupportSQLiteDatabase) {
        db.execSQL(
            """INSERT INTO game_player_profile
                (id,rating,rankTier,rankDivision,totalMatches,wins,losses,draws,noContests,
                currentWinStreak,bestWinStreak,currentLossStreak,beginnerMatchesRemaining,
                lastOutcome,createdAtEpochMillis,updatedAtEpochMillis)
                VALUES ('local_player',1234,'BRONZE',2,0,0,0,0,0,0,0,0,5,NULL,1,1)""",
        )
    }
}
