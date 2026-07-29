package com.lazyapps.steparena.core.database

import androidx.room.testing.MigrationTestHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class Migration2To3RecoveryTest {
    private val dbName = "migration-2-3"

    @get:Rule
    val helper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        StepArenaDatabase::class.java,
        emptyList(),
        FrameworkSQLiteOpenHelperFactory(),
    )

    @Test fun migrationPreservesV2DataAndCreatesRecoveryTables() {
        helper.createDatabase(dbName, 2).apply {
            execSQL(
                """INSERT INTO daily_activity_records (
                    id, localDate, zoneId, steps, unclassifiedSteps,
                    unclassifiedStepsQuality, distanceMeters, walkingDurationSeconds,
                    estimatedCaloriesKcal, averageWalkingSpeedKmh, stepsQuality,
                    distanceQuality, durationQuality, caloriesQuality, speedQuality,
                    activeHourCount, walkingSessionCount, finalized, finalizedAtEpochMillis,
                    createdAtEpochMillis, updatedAtEpochMillis
                ) VALUES (
                    'day', '2026-07-29', 'Asia/Tokyo', 321, 0, 'UNKNOWN',
                    NULL, NULL, NULL, NULL, 'MEASURED', 'UNKNOWN', 'UNKNOWN',
                    'UNKNOWN', 'UNKNOWN', 1, 0, 0, NULL, 1, 1
                )""",
            )
            close()
        }
        helper.runMigrationsAndValidate(dbName, 3, true, StepArenaDatabase.MIGRATION_2_3)
            .apply {
                query("SELECT steps FROM daily_activity_records WHERE id = 'day'").use {
                    org.junit.Assert.assertTrue(it.moveToFirst())
                    org.junit.Assert.assertEquals(321L, it.getLong(0))
                }
                query("SELECT COUNT(*) FROM tracking_gap_records").use {
                    org.junit.Assert.assertTrue(it.moveToFirst())
                    org.junit.Assert.assertEquals(0, it.getInt(0))
                }
                query("SELECT COUNT(*) FROM processed_external_step_records").use {
                    org.junit.Assert.assertTrue(it.moveToFirst())
                    org.junit.Assert.assertEquals(0, it.getInt(0))
                }
            }
    }
}
