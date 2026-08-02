package com.lazyapps.steparena.repair

import android.content.Context
import android.database.Cursor
import android.util.Base64
import androidx.sqlite.db.SupportSQLiteDatabase
import com.lazyapps.steparena.BuildConfig
import com.lazyapps.steparena.core.database.StepArenaDatabase
import org.json.JSONArray
import org.json.JSONObject
import java.security.MessageDigest
import java.util.zip.GZIPInputStream

enum class RepairStatus { REPAIRED, ALREADY_REPAIRED }

data class RepairResult(val status: RepairStatus, val fingerprint: String)

/** Fixed, debug-only repair for the audited 2026-08-01 SOV41 overcount. */
object Phase7415Repair {
    const val REPAIR_ID = "phase-7.4.15-sov41-2026-08-01-overcount-204"
    const val MANIFEST_ASSET = "phase_7_4_15_repair_manifest.json"
    const val EXPECTED_MANIFEST_SHA = "352c961fb730b56b395249269fdc814785eecfe46ff4c78b8cba4567d309211b"
    private const val MARKERS = "phase_7_4_15_repair_markers"
    private val allowedTables = setOf(
        "competitive_integrity_segments", "hourly_activity_records",
        "walking_sessions", "daily_activity_records",
    )
    private val allowedColumns = mapOf(
        "competitive_integrity_segments" to setOf("totalSteps", "eligibleSteps", "restrictedSteps", "excludedSteps", "assessment", "reasons"),
        "hourly_activity_records" to setOf("steps", "distanceMeters", "estimatedCaloriesKcal", "averageWalkingSpeedKmh"),
        "walking_sessions" to setOf("steps", "distanceMeters", "estimatedCaloriesKcal", "averageMovingSpeedKmh", "averageElapsedSpeedKmh"),
        "daily_activity_records" to setOf("steps", "distanceMeters", "walkingDurationSeconds", "estimatedCaloriesKcal", "averageWalkingSpeedKmh"),
    )

    fun execute(
        context: Context,
        repairId: String,
        manifestSha: String,
        database: StepArenaDatabase = StepArenaDatabase.get(context),
        failAfterUpdates: Int? = null,
        skipMarkerOnce: Boolean = false,
    ): RepairResult {
        check(BuildConfig.DEBUG) { "Repair is disabled outside debug builds" }
        check(context.packageName == BuildConfig.APPLICATION_ID)
        check(repairId == REPAIR_ID)
        val manifest = loadManifest(context)
        check(manifestSha == EXPECTED_MANIFEST_SHA && manifestSha == manifest.first)
        val payload = manifest.second
        check(payload.getString("repairId") == REPAIR_ID)
        check(payload.getString("localDate") == "2026-08-01")
        check(payload.getString("zoneId") == "Asia/Tokyo")
        val db = database.openHelper.writableDatabase
        check(integrityCheck(db) == "ok")
        val beforeFingerprint = fingerprint(db, payload)
        val expectedBefore = payload.getString("targetedFingerprintBefore")
        val expectedAfter = payload.getString("targetedFingerprintAfter")
        val markers = context.getSharedPreferences(MARKERS, Context.MODE_PRIVATE)
        val marked = markers.getString("repairId", null) == REPAIR_ID
        if (beforeFingerprint == expectedAfter) {
            check(!marked || markers.getString("manifestSha", null) == manifestSha)
            saveMarker(markers, manifestSha, expectedBefore, expectedAfter)
            return RepairResult(RepairStatus.ALREADY_REPAIRED, expectedAfter)
        }
        check(!marked) { "Repair marker exists but postcondition does not match" }
        check(beforeFingerprint == expectedBefore) { "Targeted fingerprint mismatch" }
        db.beginTransaction()
        try {
            var updates = 0
            val targets = payload.getJSONArray("targets")
            for (i in 0 until targets.length()) {
                val target = targets.getJSONObject(i)
                updateTarget(db, target)
                updates++
                if (failAfterUpdates == updates) error("Injected rollback")
            }
            check(fingerprint(db, payload) == expectedAfter) { "Postcondition mismatch" }
            check(integrityCheck(db) == "ok")
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
        if (!skipMarkerOnce) saveMarker(markers, manifestSha, expectedBefore, expectedAfter)
        return RepairResult(RepairStatus.REPAIRED, expectedAfter)
    }

    private fun loadManifest(context: Context): Pair<String, JSONObject> {
        val wrapper = context.assets.open(MANIFEST_ASSET).bufferedReader().use { it.readText() }.let(::JSONObject)
        val payloadBytes = GZIPInputStream(
            Base64.decode(wrapper.getString("payloadGzipBase64"), Base64.DEFAULT).inputStream(),
        ).use { it.readBytes() }
        val sha = sha256(payloadBytes)
        check(sha == wrapper.getString("manifestSha") && sha == EXPECTED_MANIFEST_SHA)
        return sha to JSONObject(payloadBytes.toString(Charsets.UTF_8))
    }

    private fun fingerprint(db: SupportSQLiteDatabase, payload: JSONObject): String {
        val out = StringBuilder()
        val targets = payload.getJSONArray("targets")
        for (i in 0 until targets.length()) {
            val t = targets.getJSONObject(i)
            val table = t.getString("table").also { check(it in allowedTables) }
            val id = t.getString("id")
            db.query("SELECT * FROM `$table` WHERE id = ?", arrayOf(id)).use { c ->
                check(c.moveToFirst()) { "Missing target $table/$id" }
                val row = canonical(c)
                check(!c.moveToNext()) { "Duplicate target $table/$id" }
                out.append(table).append('|').append(id).append('|').append(row).append('\n')
            }
        }
        return sha256(out.toString().toByteArray())
    }

    private fun canonical(c: Cursor): String = c.columnNames.sorted().joinToString("|") { name ->
        val index = c.getColumnIndexOrThrow(name)
        val value = when (c.getType(index)) {
            Cursor.FIELD_TYPE_NULL -> "n:"
            Cursor.FIELD_TYPE_INTEGER -> "i:${c.getLong(index)}"
            Cursor.FIELD_TYPE_FLOAT -> "f:${java.lang.Double.toString(c.getDouble(index))}"
            Cursor.FIELD_TYPE_STRING -> "s:${c.getString(index)}"
            else -> error("Unsupported target value")
        }
        "$name=$value"
    }

    private fun updateTarget(db: SupportSQLiteDatabase, target: JSONObject) {
        val table = target.getString("table").also { check(it in allowedTables) }
        val after = target.getJSONObject("after")
        val columns = after.keys().asSequence().toList().sorted()
        check(columns.isNotEmpty() && columns.all { it in allowedColumns.getValue(table) })
        val args = columns.map { after.toSqlValue(it) }.toMutableList<Any?>().apply { add(target.getString("id")) }
        db.execSQL("UPDATE `$table` SET ${columns.joinToString { "`$it` = ?" }} WHERE id = ?", args.toTypedArray())
        check(db.query("SELECT changes()").use { it.moveToFirst(); it.getInt(0) } == 1)
    }

    private fun JSONObject.toSqlValue(key: String): Any? = when (val v = get(key)) {
        JSONObject.NULL -> null
        is Number, is String -> v
        else -> error("Unsupported manifest value")
    }

    private fun integrityCheck(db: SupportSQLiteDatabase): String =
        db.query("PRAGMA integrity_check").use { check(it.moveToFirst()); it.getString(0) }

    private fun saveMarker(prefs: android.content.SharedPreferences, manifestSha: String, before: String, after: String) {
        check(prefs.edit().putString("repairId", REPAIR_ID).putString("manifestSha", manifestSha)
            .putString("targetedFingerprint", after).putLong("completedAt", System.currentTimeMillis())
            .putString("preconditionSummary", before).putString("postconditionSummary", after).commit())
    }

    private fun sha256(bytes: ByteArray): String = MessageDigest.getInstance("SHA-256")
        .digest(bytes).joinToString("") { "%02x".format(it) }
}
