package com.lazyapps.steparena.repair

import android.content.Context
import androidx.sqlite.db.SupportSQLiteDatabase
import com.lazyapps.steparena.BuildConfig
import com.lazyapps.steparena.core.database.StepArenaDatabase
import org.json.JSONObject
import java.security.MessageDigest

enum class TimestampRepairStatus { REPAIRED, ALREADY_REPAIRED }

data class TimestampRepairResult(
    val status: TimestampRepairStatus,
    val beforeFingerprint: String,
    val afterFingerprint: String,
)

object Phase7415dTimestampRepair {
    const val REPAIR_ID = "phase-7.4.15d-sov41-game-timestamps"
    const val MANIFEST_ASSET = "phase_7_4_15d_game_timestamp_manifest.json"
    const val EXPECTED_MANIFEST_SHA = "d246c4571c12c712f27df32eff7e2662824361bdb889f7534a4b73895882abd3"
    private const val EXPECTED_PACKAGE = "com.lazyapps.steparena"
    private const val MARKERS = "phase_7_4_15d_timestamp_repair_markers"

    fun execute(
        context: Context,
        repairId: String,
        manifestSha: String,
        database: StepArenaDatabase = StepArenaDatabase.get(context),
    ): TimestampRepairResult {
        check(BuildConfig.DEBUG)
        check(context.packageName == EXPECTED_PACKAGE)
        check(repairId == REPAIR_ID)
        val bytes = context.assets.open(MANIFEST_ASSET).use { it.readBytes() }
        check(sha256(bytes) == EXPECTED_MANIFEST_SHA && manifestSha == EXPECTED_MANIFEST_SHA)
        val manifest = JSONObject(bytes.toString(Charsets.UTF_8))
        check(manifest.getString("repairId") == REPAIR_ID)
        check(manifest.getString("packageName") == EXPECTED_PACKAGE)
        val db = database.openHelper.writableDatabase
        val before = fingerprint(db)
        val allBefore = targetsMatch(db, manifest, "before")
        val allAfter = targetsMatch(db, manifest, "after")
        val markers = context.getSharedPreferences(MARKERS, Context.MODE_PRIVATE)
        if (allAfter) {
            markers.edit().putString("repairId", REPAIR_ID)
                .putString("manifestSha", EXPECTED_MANIFEST_SHA).apply()
            return TimestampRepairResult(TimestampRepairStatus.ALREADY_REPAIRED, before, before)
        }
        check(allBefore) { "Timestamp repair fingerprint mismatch" }
        db.beginTransaction()
        try {
            val targets = manifest.getJSONArray("targets")
            for (index in 0 until targets.length()) update(db, targets.getJSONObject(index))
            check(targetsMatch(db, manifest, "after"))
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
        val after = fingerprint(db)
        markers.edit().putString("repairId", REPAIR_ID)
            .putString("manifestSha", EXPECTED_MANIFEST_SHA)
            .putString("beforeFingerprint", before)
            .putString("afterFingerprint", after).apply()
        return TimestampRepairResult(TimestampRepairStatus.REPAIRED, before, after)
    }

    private fun targetsMatch(db: SupportSQLiteDatabase, manifest: JSONObject, valueName: String): Boolean {
        val targets = manifest.getJSONArray("targets")
        for (index in 0 until targets.length()) {
            val target = targets.getJSONObject(index)
            val (where, args) = where(target.getJSONObject("key"))
            val values = mutableListOf<Long>()
            db.query("SELECT `${target.getString("column")}` FROM `${target.getString("table")}` WHERE $where", args)
                .use { cursor -> while (cursor.moveToNext()) values += cursor.getLong(0) }
            val expectedRows = target.optInt("expectedRows", 1)
            if (values.size != expectedRows || values.any { it != target.getLong(valueName) }) return false
        }
        return true
    }

    private fun update(db: SupportSQLiteDatabase, target: JSONObject) {
        val (where, args) = where(target.getJSONObject("key"))
        val before = target.getLong("before")
        val updateArgs = arrayOf<Any>(target.getLong("after"), *args, before)
        db.execSQL(
            "UPDATE `${target.getString("table")}` SET `${target.getString("column")}` = ? " +
                "WHERE $where AND `${target.getString("column")}` = ?",
            updateArgs,
        )
    }

    private fun where(key: JSONObject): Pair<String, Array<Any>> {
        val names = key.keys().asSequence().toList().sorted()
        return names.joinToString(" AND ") { "`$it` = ?" } to names.map { key.getString(it) }.toTypedArray()
    }

    private fun fingerprint(db: SupportSQLiteDatabase): String {
        val rows = mutableListOf<String>()
        listOf("daily_matches", "weekly_league_participants", "weekly_leagues").forEach { table ->
            db.query("SELECT * FROM `$table` ORDER BY 1, 2").use { cursor ->
                while (cursor.moveToNext()) {
                    rows += buildString {
                        append(table)
                        for (column in 0 until cursor.columnCount) {
                            append('|').append(cursor.getType(column)).append(':')
                            if (!cursor.isNull(column)) append(cursor.getString(column))
                        }
                    }
                }
            }
        }
        return sha256(rows.joinToString("\n").toByteArray())
    }

    private fun sha256(bytes: ByteArray): String = MessageDigest.getInstance("SHA-256")
        .digest(bytes).joinToString("") { "%02x".format(it) }
}
