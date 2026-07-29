package com.lazyapps.steparena.core.database

import android.content.Context
import androidx.room.testing.MigrationTestHelper
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class Migration1To2PreservesActivityDataTest {
    @get:Rule
    val helper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        StepArenaDatabase::class.java,
    )

    @Test
    fun migration1To2_preservesExistingRecordsAndAppliesSafeDefaults() {
        helper.createDatabase(NAME, 1).apply {
            insertVersion1Fixtures(this)
            close()
        }

        helper.runMigrationsAndValidate(NAME, 2, true, StepArenaDatabase.MIGRATION_1_2).use { db ->
            db.query(
                "SELECT steps, distanceMeters, walkingDurationSeconds, " +
                    "appliedStepLengthMeters, appliedWeightKg, calorieFormulaVersion " +
                    "FROM hourly_activity_records WHERE id = 'hour-1'",
            ).use {
                it.moveToFirst()
                assertEquals(321L, it.getLong(0))
                assertEquals(224.7, it.getDouble(1), 0.001)
                assertEquals(180L, it.getLong(2))
                assertEquals(0.70, it.getDouble(3), 0.001)
                assertEquals(60.0, it.getDouble(4), 0.001)
                assertEquals(1, it.getInt(5))
            }
            db.query(
                "SELECT steps, unclassifiedSteps, unclassifiedStepsQuality " +
                    "FROM daily_activity_records WHERE id = 'day-1'",
            ).use {
                it.moveToFirst()
                assertEquals(326L, it.getLong(0))
                assertEquals(5L, it.getLong(1))
                assertEquals("RECOVERED", it.getString(2))
            }
            db.query(
                "SELECT steps, isManual, detectorEventCount, estimatedStepCount, recoveredStepCount " +
                    "FROM walking_sessions WHERE id = 'session-1'",
            ).use {
                it.moveToFirst()
                assertEquals(100L, it.getLong(0))
                assertEquals(0, it.getInt(1))
                assertEquals(0, it.getInt(2))
                assertEquals(0L, it.getLong(3))
                assertEquals(0L, it.getLong(4))
            }
            db.query(
                "SELECT lastCounterValue, lastBootSessionId, activeAutoSessionId, " +
                    "activeManualSessionId FROM activity_processing_state WHERE `key` = 'sensor'",
            ).use {
                it.moveToFirst()
                assertEquals(1234L, it.getLong(0))
                assertEquals("boot-v1", it.getString(1))
                assertEquals(null, it.getString(2))
                assertEquals(null, it.getString(3))
            }
        }
        ApplicationProvider.getApplicationContext<Context>().deleteDatabase(NAME)
    }

    private fun insertVersion1Fixtures(db: SupportSQLiteDatabase) {
        db.execSQL(
            "INSERT INTO hourly_activity_records VALUES(" +
                "'hour-1','2026-07-29',12,'Asia/Tokyo',32400,1000,2000,321,224.7,180," +
                "10.5,4.494,'MEASURED','ESTIMATED','ESTIMATED','ESTIMATED','ESTIMATED'," +
                "1000,2000,3,0,0,1000,2000)",
        )
        db.execSQL(
            "INSERT INTO daily_activity_records VALUES(" +
                "'day-1','2026-07-29','Asia/Tokyo',326,5,228.2,180,10.5,4.5," +
                "'MIXED','ESTIMATED','ESTIMATED','ESTIMATED','ESTIMATED',1,1,0,NULL,1000,2000)",
        )
        db.execSQL(
            "INSERT INTO walking_sessions VALUES(" +
                "'session-1','2026-07-29','Asia/Tokyo',1000,2000,100,70.0,60,1000,940," +
                "3.0,4.2,0.252,'AUTO_DETECTED','COMPLETED','MEASURED','ESTIMATED'," +
                "'ESTIMATED','ESTIMATED','ESTIMATED','service-1',1000,2000)",
        )
        db.execSQL(
            "INSERT INTO activity_processing_state VALUES(" +
                "'sensor',1234,2000,'Asia/Tokyo','boot-v1',2000)",
        )
    }

    private companion object {
        const val NAME = "migration-1-to-2.db"
    }
}
