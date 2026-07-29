package com.lazyapps.steparena.core.database

import androidx.room.testing.MigrationTestHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class Migration3To4GameTest {
    private val dbName = "migration-3-4"
    @get:Rule val helper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        StepArenaDatabase::class.java,
        emptyList(),
        FrameworkSQLiteOpenHelperFactory(),
    )

    @Test fun migrationPreservesActivityAndCreatesGameTables() {
        helper.createDatabase(dbName, 3).apply {
            execSQL(
                """INSERT INTO daily_activity_records VALUES(
                    'keep','2026-07-28','Asia/Tokyo',1234,0,'UNKNOWN',NULL,NULL,NULL,NULL,
                    'MEASURED','UNKNOWN','UNKNOWN','UNKNOWN','UNKNOWN',1,0,1,NULL,1,1)""",
            )
            close()
        }
        helper.runMigrationsAndValidate(dbName, 4, true, StepArenaDatabase.MIGRATION_3_4).apply {
            query("SELECT steps FROM daily_activity_records WHERE id='keep'").use {
                assert(it.moveToFirst() && it.getLong(0) == 1234L)
            }
            listOf("game_player_profile", "daily_matches", "weekly_leagues", "game_seasons", "achievement_unlocks")
                .forEach { table ->
                    query("SELECT name FROM sqlite_master WHERE type='table' AND name='$table'").use {
                        assert(it.moveToFirst())
                    }
                }
            close()
        }
    }
}
