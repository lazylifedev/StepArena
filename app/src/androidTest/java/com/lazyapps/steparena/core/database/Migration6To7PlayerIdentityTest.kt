package com.lazyapps.steparena.core.database

import androidx.room.testing.MigrationTestHelper
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class Migration6To7PlayerIdentityTest {
    private val databaseName = "migration-6-7-player-identity"

    @get:Rule
    val helper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        StepArenaDatabase::class.java,
    )

    @Test
    fun migrationAddsOptionalDisplayNameAndNormalizesLegacyParticipants() {
        helper.createDatabase(databaseName, 6).apply {
            insertProfile(this)
            insertLeague(this)
            close()
        }

        helper.runMigrationsAndValidate(databaseName, 7, true, StepArenaDatabase.MIGRATION_6_7)
            .use { db ->
                db.query("SELECT displayName FROM game_player_profile WHERE id = 'local_player'").use {
                    assertTrue(it.moveToFirst())
                    assertTrue(it.isNull(0))
                }
                db.query(
                    "SELECT participantId,displayName,rank,isLocalPlayer,generatedLocally " +
                        "FROM weekly_league_participants ORDER BY rank",
                ).use {
                    assertTrue(it.moveToFirst())
                    assertEquals("player", it.getString(0))
                    assertEquals("あなた", it.getString(1))
                    assertEquals(1, it.getInt(2))
                    assertEquals(1, it.getInt(3))
                    assertEquals(1, it.getInt(4))
                    assertTrue(it.moveToNext())
                    assertEquals("npc-1", it.getString(0))
                    assertEquals(2, it.getInt(2))
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

    private fun insertLeague(db: SupportSQLiteDatabase) {
        db.execSQL(
            """INSERT INTO weekly_leagues
                (id,weekStartLocalDate,weekEndLocalDate,zoneId,status,userPoints,userRank,
                participantsJson,finalizedAtEpochMillis,createdAtEpochMillis,updatedAtEpochMillis)
                VALUES ('league-1','2026-07-27','2026-08-02','Asia/Tokyo','ACTIVE',3,1,
                '[{"id":"player","name":"You","points":3,"steps":3619},{"id":"npc-1","name":"Aoi","points":2,"steps":3000}]',
                NULL,10,20)""",
        )
    }
}
