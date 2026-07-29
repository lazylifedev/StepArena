package com.lazyapps.steparena.tracking

import android.content.Context
import java.time.Instant

data class DiagnosticLogEntry(
    val timestamp: Instant,
    val event: String,
    val sessionId: String?,
    val status: TrackingStatus,
    val sensorValue: Long?,
    val delta: Long?,
    val todaySteps: Long,
    val baseline: Long?,
    val trackingRequested: Boolean,
    val heartbeat: Instant?,
    val bootSessionId: String,
    val localDate: String,
    val zoneId: String,
    val detail: String? = null,
) {
    fun encode(): String = listOf(
        timestamp.toString(), event, sessionId.orEmpty(), status.name,
        sensorValue?.toString().orEmpty(), delta?.toString().orEmpty(),
        todaySteps.toString(), baseline?.toString().orEmpty(),
        trackingRequested.toString(), heartbeat?.toString().orEmpty(),
        bootSessionId, localDate, zoneId, detail.orEmpty(),
    ).joinToString("\t") { it.replace('\t', ' ').replace('\n', ' ') }

    companion object {
        fun decode(line: String): DiagnosticLogEntry? {
            val p = line.split('\t')
            if (p.size < 14) return null
            return runCatching {
                DiagnosticLogEntry(
                    Instant.parse(p[0]), p[1], p[2].ifEmpty { null },
                    TrackingStatus.valueOf(p[3]), p[4].toLongOrNull(),
                    p[5].toLongOrNull(), p[6].toLong(), p[7].toLongOrNull(),
                    p[8].toBooleanStrict(), p[9].takeIf(String::isNotEmpty)?.let(Instant::parse),
                    p[10], p[11], p[12], p[13].ifEmpty { null },
                )
            }.getOrNull()
        }
    }
}

class DiagnosticLogRepository(context: Context) {
    private val preferences = context.getSharedPreferences("tracking_diagnostics", Context.MODE_PRIVATE)

    @Synchronized
    fun append(entry: DiagnosticLogEntry) {
        val lines = preferences.getString(KEY, "").orEmpty().lineSequence()
            .filter(String::isNotBlank).toMutableList()
        lines += entry.encode()
        preferences.edit().putString(KEY, lines.takeLast(MAX_ENTRIES).joinToString("\n")).apply()
    }

    fun read(): List<DiagnosticLogEntry> = preferences.getString(KEY, "").orEmpty()
        .lineSequence().mapNotNull(DiagnosticLogEntry::decode).toList()

    companion object {
        private const val KEY = "entries"
        const val MAX_ENTRIES = 200
    }
}
