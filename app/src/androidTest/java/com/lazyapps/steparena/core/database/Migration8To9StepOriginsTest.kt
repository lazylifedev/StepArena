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
class Migration8To9StepOriginsTest {
    private val name = "migration-8-9-step-origins"
    private val context: Context = ApplicationProvider.getApplicationContext()

    @get:Rule
    val helper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(), StepArenaDatabase::class.java,
        emptyList(), FrameworkSQLiteOpenHelperFactory(),
    )

    @Test fun legacyUnclassifiedRecoveryMigratesToExternalOnly() {
        helper.createDatabase(name, 8).use { db ->
            db.execSQL(
                """INSERT INTO daily_activity_records VALUES(
                    'day','2026-08-01','Asia/Tokyo',3619,20,'RECOVERED',NULL,NULL,NULL,NULL,
                    'MIXED','UNKNOWN','UNKNOWN','UNKNOWN','UNKNOWN',0,0,0,NULL,1,1)""",
            )
        }
        helper.runMigrationsAndValidate(name, 9, true, StepArenaDatabase.MIGRATION_8_9).use { db ->
            db.query("SELECT steps,externalRecoveredSteps,unallocatedMeasuredSteps FROM daily_activity_records").use {
                it.moveToFirst()
                assertEquals(3_619L, it.getLong(0))
                assertEquals(20L, it.getLong(1))
                assertEquals(0L, it.getLong(2))
            }
        }
        context.deleteDatabase(name)
    }
}
