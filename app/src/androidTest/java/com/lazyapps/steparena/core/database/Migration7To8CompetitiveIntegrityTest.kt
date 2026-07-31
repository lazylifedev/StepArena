package com.lazyapps.steparena.core.database

import android.content.Context
import androidx.room.testing.MigrationTestHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class Migration7To8CompetitiveIntegrityTest {
    private val name = "migration-7-8-integrity"
    private val context: Context = ApplicationProvider.getApplicationContext()

    @get:Rule
    val helper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        StepArenaDatabase::class.java,
        emptyList(),
        FrameworkSQLiteOpenHelperFactory(),
    )

    @Test fun migrationCreatesIntegritySegmentTable() {
        helper.createDatabase(name, 7).close()
        helper.runMigrationsAndValidate(name, 8, true, StepArenaDatabase.MIGRATION_7_8).use { db ->
            db.execSQL(
                """INSERT INTO competitive_integrity_segments VALUES(
                    'segment','2026-07-31','Asia/Tokyo',1,2,100,80,20,0,
                    'LIMITED','LOW_DETECTOR_COVERAGE',1,2)""",
            )
            db.query("SELECT totalSteps,eligibleSteps,restrictedSteps,classifierVersion FROM competitive_integrity_segments").use {
                it.moveToFirst()
                assertEquals(100, it.getLong(0))
                assertEquals(80, it.getLong(1))
                assertEquals(20, it.getLong(2))
                assertEquals(1, it.getInt(3))
            }
        }
        context.deleteDatabase(name)
    }
}
