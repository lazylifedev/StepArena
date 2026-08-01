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
import java.time.Instant

@RunWith(AndroidJUnit4::class)
class Migration9To10OriginRepairTest {
    private val context: Context = ApplicationProvider.getApplicationContext()

    @get:Rule
    val helper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(), StepArenaDatabase::class.java,
        emptyList(), FrameworkSQLiteOpenHelperFactory(),
    )

    @Test fun externalRecordCrossingJstMidnightIsSplitOnlyAcrossExistingDays() {
        val name = "migration-9-10-midnight"
        createV9Fixture(name)
        helper.runMigrationsAndValidate(name, 10, true, StepArenaDatabase.MIGRATION_9_10).use { db ->
            db.query("SELECT localDate,externalRecoveredSteps,unallocatedMeasuredSteps FROM daily_activity_records ORDER BY localDate").use { cursor ->
                cursor.moveToFirst()
                assertEquals("2026-08-01", cursor.getString(0)); assertEquals(500L, cursor.getLong(1)); assertEquals(500L, cursor.getLong(2))
                cursor.moveToNext()
                assertEquals("2026-08-02", cursor.getString(0)); assertEquals(500L, cursor.getLong(1)); assertEquals(500L, cursor.getLong(2))
            }
        }
        context.deleteDatabase(name)
    }

    @Test fun migrationKeepsLegacyRemainderSeparateFromV9UnallocatedSteps() {
        val name = "migration-9-10-existing-unallocated"
        createV9Fixture(name, legacy = 1_020, proven = 20, existingUnallocated = 300, crossesMidnight = false)
        helper.runMigrationsAndValidate(name, 10, true, StepArenaDatabase.MIGRATION_9_10).use { db ->
            db.query("SELECT externalRecoveredSteps,unallocatedMeasuredSteps FROM daily_activity_records WHERE localDate = '2026-08-01'").use { cursor ->
                cursor.moveToFirst()
                assertEquals(20L, cursor.getLong(0))
                assertEquals(1_300L, cursor.getLong(1))
            }
        }
        context.deleteDatabase(name)
    }

    @Test fun v9NewDatabaseKeepsOnlyNewUnallocatedSteps() {
        val name = "migration-9-10-v9-new-db"
        createV9Fixture(name, legacy = 20, proven = 20, existingUnallocated = 300, crossesMidnight = false)
        helper.runMigrationsAndValidate(name, 10, true, StepArenaDatabase.MIGRATION_9_10).use { db ->
            db.query("SELECT externalRecoveredSteps,unallocatedMeasuredSteps FROM daily_activity_records WHERE localDate = '2026-08-01'").use { cursor ->
                cursor.moveToFirst()
                assertEquals(20L, cursor.getLong(0))
                assertEquals(300L, cursor.getLong(1))
            }
        }
        context.deleteDatabase(name)
    }

    @Test fun migration8To9To10KeepsLegacyValueOnce() {
        val name = "migration-8-9-10-origin-path"
        helper.createDatabase(name, 8).use { db ->
            db.execSQL("""INSERT INTO daily_activity_records VALUES(
                'day','2026-08-01','Asia/Tokyo',1020,1020,'MIXED',NULL,NULL,NULL,NULL,
                'MIXED','UNKNOWN','UNKNOWN','UNKNOWN','UNKNOWN',0,0,0,NULL,0,0)""")
        }
        helper.runMigrationsAndValidate(
            name, 10, true, StepArenaDatabase.MIGRATION_8_9, StepArenaDatabase.MIGRATION_9_10,
        ).use { db ->
            db.query("SELECT externalRecoveredSteps,unallocatedMeasuredSteps FROM daily_activity_records").use { cursor ->
                cursor.moveToFirst()
                assertEquals(0L, cursor.getLong(0))
                assertEquals(1_020L, cursor.getLong(1))
            }
        }
        context.deleteDatabase(name)
    }

    private fun createV9Fixture(name: String, legacy: Long = 1_000, proven: Long = 1_000, existingUnallocated: Long = 0, crossesMidnight: Boolean = true) {
        val start = Instant.parse(if (crossesMidnight) "2026-08-01T14:50:00Z" else "2026-08-01T13:00:00Z").toEpochMilli()
        val end = Instant.parse(if (crossesMidnight) "2026-08-01T15:10:00Z" else "2026-08-01T13:10:00Z").toEpochMilli()
        helper.createDatabase(name, 9).use { db ->
            db.execSQL("""INSERT INTO daily_activity_records
                (id,localDate,zoneId,steps,unclassifiedSteps,unclassifiedStepsQuality,externalRecoveredSteps,unallocatedMeasuredSteps,
                 distanceMeters,walkingDurationSeconds,estimatedCaloriesKcal,averageWalkingSpeedKmh,stepsQuality,distanceQuality,durationQuality,caloriesQuality,speedQuality,activeHourCount,walkingSessionCount,finalized,finalizedAtEpochMillis,createdAtEpochMillis,updatedAtEpochMillis)
                VALUES('day1','2026-08-01','Asia/Tokyo',${legacy},${legacy},'MIXED',0,${existingUnallocated},NULL,NULL,NULL,NULL,'MIXED','UNKNOWN','UNKNOWN','UNKNOWN','UNKNOWN',0,0,0,NULL,0,0)""")
            db.execSQL("""INSERT INTO daily_activity_records
                (id,localDate,zoneId,steps,unclassifiedSteps,unclassifiedStepsQuality,externalRecoveredSteps,unallocatedMeasuredSteps,
                 distanceMeters,walkingDurationSeconds,estimatedCaloriesKcal,averageWalkingSpeedKmh,stepsQuality,distanceQuality,durationQuality,caloriesQuality,speedQuality,activeHourCount,walkingSessionCount,finalized,finalizedAtEpochMillis,createdAtEpochMillis,updatedAtEpochMillis)
                VALUES('day2','2026-08-02','Asia/Tokyo',1000,1000,'MIXED',0,0,NULL,NULL,NULL,NULL,'MIXED','UNKNOWN','UNKNOWN','UNKNOWN','UNKNOWN',0,0,0,NULL,0,0)""")
            db.execSQL("""INSERT INTO tracking_gap_records
                (id,startedAtEpochMillis,endedAtEpochMillis,zoneId,reason,status,expectedTracking,explicitUserStop,recoveredSteps,unresolvedSteps,recoverySource,quality,externalRecordCount,externalOriginsJson,fingerprint,detectedAtEpochMillis,createdAtEpochMillis,updatedAtEpochMillis)
                VALUES('gap',$start,$end,'Asia/Tokyo','x','RECOVERED',1,0,0,0,NULL,'MIXED',1,NULL,'gap-fp',0,0,0)""")
            db.execSQL("""INSERT INTO processed_external_step_records
                (id,recordId,dataOriginPackage,startedAtEpochMillis,endedAtEpochMillis,steps,lastModifiedAtEpochMillis,fingerprint,processedAtEpochMillis,appliedSteps,gapId)
                VALUES('record','record','test',$start,$end,$proven,NULL,'record-fp',0,$proven,'gap')""")
        }
    }
}
