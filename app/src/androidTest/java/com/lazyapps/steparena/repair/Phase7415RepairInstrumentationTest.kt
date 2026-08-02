package com.lazyapps.steparena.repair

import android.content.Context
import android.util.Base64
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.lazyapps.steparena.core.database.StepArenaDatabase
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.util.zip.GZIPInputStream

@RunWith(AndroidJUnit4::class)
class Phase7415RepairInstrumentationTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private var database: StepArenaDatabase? = null

    @After fun close() { database?.close() }

    @Test fun repairsAndSecondRunDoesNotSubtractAgain() {
        val db = fixture("normal")
        assertEquals(RepairStatus.REPAIRED, execute(db).status)
        assertPostconditions(db)
        val before = targetDigest(db)
        assertEquals(RepairStatus.ALREADY_REPAIRED, execute(db).status)
        assertEquals(before, targetDigest(db))
    }

    @Test fun fingerprintMismatchWritesNothingAndCreatesNoMarker() {
        val db = fixture("mismatch")
        db.openHelper.writableDatabase.execSQL(
            "UPDATE competitive_integrity_segments SET totalSteps = totalSteps + 1 WHERE id = " +
                "(SELECT id FROM competitive_integrity_segments WHERE id LIKE 'integrity-boot-%' LIMIT 1)",
        )
        val before = targetDigest(db)
        assertTrue(runCatching { execute(db) }.isFailure)
        assertEquals(before, targetDigest(db))
        assertFalse(markerExists())
    }

    @Test fun injectedFailureRollsBackEverything() {
        val db = fixture("rollback")
        val before = targetDigest(db)
        assertTrue(runCatching { execute(db, failAfterUpdates = 50) }.isFailure)
        assertEquals(before, targetDigest(db))
        assertFalse(markerExists())
    }

    @Test fun missingMarkerAfterCommitIsRecoveredWithoutDatabaseWrite() {
        val db = fixture("marker")
        assertEquals(RepairStatus.REPAIRED, execute(db, skipMarkerOnce = true).status)
        assertFalse(markerExists())
        val before = targetDigest(db)
        assertEquals(RepairStatus.ALREADY_REPAIRED, execute(db).status)
        assertEquals(before, targetDigest(db))
        assertTrue(markerExists())
    }

    @Test fun augustSecondAndProcessingStateRemainUnchanged() {
        val db = fixture("isolation")
        val sql = db.openHelper.writableDatabase
        sql.execSQL("INSERT INTO daily_activity_records SELECT 'fixture-aug2','2026-08-02',zoneId,99,unclassifiedSteps,unclassifiedStepsQuality,externalRecoveredSteps,unallocatedMeasuredSteps,distanceMeters,walkingDurationSeconds,estimatedCaloriesKcal,averageWalkingSpeedKmh,stepsQuality,distanceQuality,durationQuality,caloriesQuality,speedQuality,activeHourCount,walkingSessionCount,finalized,finalizedAtEpochMillis,createdAtEpochMillis,updatedAtEpochMillis FROM daily_activity_records LIMIT 1")
        sql.execSQL("INSERT INTO activity_processing_state (`key`,lastCounterValue,lastEventEpochMillis,lastZoneId,lastBootSessionId,activeAutoSessionId,activeManualSessionId,lastDetectorEventEpochMillis,lastWalkingEventEpochMillis,updatedAtEpochMillis,activityRepairVersion,legacyOriginRepairVersion) VALUES ('sensor',7106,1,'Asia/Tokyo','boot',NULL,NULL,2,3,4,0,1)")
        val aug2 = scalar(db, "SELECT steps FROM daily_activity_records WHERE id='fixture-aug2'")
        val state = scalar(db, "SELECT lastCounterValue||'|'||updatedAtEpochMillis FROM activity_processing_state")
        execute(db)
        assertEquals(aug2, scalar(db, "SELECT steps FROM daily_activity_records WHERE id='fixture-aug2'"))
        assertEquals(state, scalar(db, "SELECT lastCounterValue||'|'||updatedAtEpochMillis FROM activity_processing_state"))
    }

    private fun fixture(suffix: String): StepArenaDatabase {
        context.getSharedPreferences("phase_7_4_15_repair_markers", Context.MODE_PRIVATE).edit().clear().commit()
        val name = "phase7415-$suffix.db"
        context.deleteDatabase(name)
        val db = Room.databaseBuilder(context, StepArenaDatabase::class.java, name)
            .allowMainThreadQueries().build().also { database = it }
        val payload = payload()
        val targets = payload.getJSONArray("targets")
        for (i in 0 until targets.length()) {
            val t = targets.getJSONObject(i)
            val row = t.getJSONObject("before")
            val columns = row.keys().asSequence().toList().sorted()
            val values = columns.map { key -> row.opt(key).let { if (it === JSONObject.NULL) null else it } }
            db.openHelper.writableDatabase.execSQL(
                "INSERT INTO `${t.getString("table")}` (${columns.joinToString { "`$it`" }}) VALUES (${columns.joinToString { "?" }})",
                values.toTypedArray(),
            )
        }
        db.openHelper.writableDatabase.execSQL(
            "INSERT INTO hourly_activity_records SELECT 'fixture-hour-other',localDate,0,zoneId,utcOffsetSeconds,periodStartEpochMillis,periodEndEpochMillis,5823,distanceMeters,walkingDurationSeconds,estimatedCaloriesKcal,averageWalkingSpeedKmh,stepsQuality,distanceQuality,durationQuality,caloriesQuality,speedQuality,firstActivityAtEpochMillis,lastActivityAtEpochMillis,sensorEventCount,recoveredSteps,estimatedSteps,appliedStepLengthMeters,appliedWeightKg,calorieFormulaVersion,createdAtEpochMillis,updatedAtEpochMillis FROM hourly_activity_records LIMIT 1",
        )
        db.openHelper.writableDatabase.execSQL(
            "INSERT INTO competitive_integrity_segments SELECT 'fixture-integrity-other',localDate,zoneId,startedAtEpochMillis,endedAtEpochMillis,7109,7063,46,0,'LIMITED','LOW_DETECTOR_COVERAGE',classifierVersion,createdAtEpochMillis FROM competitive_integrity_segments LIMIT 1",
        )
        return db
    }

    private fun execute(db: StepArenaDatabase, failAfterUpdates: Int? = null, skipMarkerOnce: Boolean = false) =
        Phase7415Repair.execute(context, Phase7415Repair.REPAIR_ID, Phase7415Repair.EXPECTED_MANIFEST_SHA, db, failAfterUpdates, skipMarkerOnce)

    private fun payload(): JSONObject {
        val wrapper = context.assets.open(Phase7415Repair.MANIFEST_ASSET).bufferedReader().use { JSONObject(it.readText()) }
        val bytes = GZIPInputStream(
            Base64.decode(wrapper.getString("payloadGzipBase64"), Base64.DEFAULT).inputStream(),
        ).use { it.readBytes() }
        return JSONObject(bytes.toString(Charsets.UTF_8))
    }

    private fun assertPostconditions(db: StepArenaDatabase) {
        assertEquals("7239", scalar(db, "SELECT steps FROM daily_activity_records WHERE localDate='2026-08-01'"))
        assertEquals("7237", scalar(db, "SELECT SUM(steps) FROM hourly_activity_records"))
        assertEquals("7239|7193|46|0", scalar(db, "SELECT SUM(totalSteps)||'|'||SUM(eligibleSteps)||'|'||SUM(restrictedSteps)||'|'||SUM(excludedSteps) FROM competitive_integrity_segments"))
        assertEquals("ok", scalar(db, "PRAGMA integrity_check"))
    }

    private fun markerExists() = context.getSharedPreferences("phase_7_4_15_repair_markers", Context.MODE_PRIVATE).contains("repairId")
    private fun targetDigest(db: StepArenaDatabase) = scalar(db, "SELECT total(steps)||'|'||total(distanceMeters) FROM daily_activity_records") + scalar(db, "SELECT total(totalSteps) FROM competitive_integrity_segments")
    private fun scalar(db: StepArenaDatabase, query: String): String = db.openHelper.writableDatabase.query(query).use { it.moveToFirst(); it.getString(0) }
}
