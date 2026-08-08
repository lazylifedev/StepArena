package com.lazyapps.steparena.qa.telemetry

import android.content.Context
import android.os.Build
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.functions.FirebaseFunctions
import com.lazyapps.steparena.BuildConfig
import com.lazyapps.steparena.activity.ActivityRepository
import com.lazyapps.steparena.app.StepArenaApplication
import com.lazyapps.steparena.tracking.StepTrackingState
import com.lazyapps.steparena.tracking.TrackingStateRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.suspendCancellableCoroutine
import org.json.JSONArray
import org.json.JSONObject
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.util.UUID
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

data class QaTelemetryStatus(
    val enabled: Boolean,
    val lastUploadEpochMillis: Long? = null,
    val pendingEvents: Int = 0,
    val lastSnapshotEpochMillis: Long? = null,
    val deviceAlias: String,
)

data class QaTelemetryEvent(
    val eventId: String,
    val type: String,
    val timestamp: String,
    val data: Map<String, Any?>,
)

/** QA-only telemetry. Every public operation is a no-op for a Production APK. */
class QaTelemetryClient(
    private val context: Context,
    private val activityRepository: ActivityRepository,
) {
    private val store = QaTelemetryStore(context)
    private val functions = FirebaseFunctions.getInstance("us-central1")
    private val tracking = TrackingStateRepository(context)
    private val _status = MutableStateFlow(store.status())
    val status: StateFlow<QaTelemetryStatus> = _status.asStateFlow()

    val enabled: Boolean get() = QaTelemetryPolicy.isEnabled(BuildConfig.FLAVOR)

    suspend fun recordProcessStart() {
        if (!enabled) return
        recordEvent(
            "PROCESS_START",
            mapOf("app_process_start" to true),
        )
        uploadPending()
    }

    suspend fun recordEvent(type: String, data: Map<String, Any?> = emptyMap()) {
        if (!enabled) return
        require(type.matches(Regex("[A-Z][A-Z0-9_]{0,63}")))
        store.append(
            QaTelemetryEvent(
                eventId = UUID.randomUUID().toString(),
                type = type,
                timestamp = Instant.now().toString(),
                data = QaTelemetrySanitizer.sanitize(data),
            ),
        )
        refreshStatus()
    }

    suspend fun captureAndUpload() {
        if (!enabled) return
        val snapshot = captureSnapshot()
        uploadPending(snapshot)
    }

    suspend fun uploadPending(snapshot: Map<String, Any?>? = null) {
        if (!enabled || FirebaseAuth.getInstance().currentUser == null) return
        val events = store.events()
        if (events.isEmpty() && snapshot == null) return
        val requestId = UUID.randomUUID().toString()
        val payload = linkedMapOf<String, Any?>(
            "anonymousDeviceId" to store.deviceAlias,
            "requestId" to requestId,
            "events" to events.map { event ->
                mapOf(
                    "eventId" to event.eventId,
                    "type" to event.type,
                    "timestamp" to event.timestamp,
                    "data" to event.data,
                )
            },
        )
        snapshot?.let {
            payload["snapshot"] = mapOf(
                "snapshotId" to "snapshot-${System.currentTimeMillis() / SNAPSHOT_BUCKET_MILLIS}",
                "timestamp" to Instant.now().toString(),
                "data" to QaTelemetrySanitizer.sanitize(it),
            )
        }
        runCatching { call(payload) }.onSuccess {
            store.remove(events.map(QaTelemetryEvent::eventId))
            if (snapshot != null) store.markSnapshot(System.currentTimeMillis())
            store.markUpload(System.currentTimeMillis())
        }
        refreshStatus()
    }

    suspend fun captureSnapshot(): Map<String, Any?> {
        if (!enabled) return emptyMap()
        val now = Instant.now()
        val zone = ZoneId.systemDefault()
        val date = LocalDate.now(zone)
        val state = tracking.current()
        val daily = activityRepository.officialProgressSnapshot(date, zone)
        val hours = activityRepository.observeHours(date, zone).first()
        val current = activityRepository.currentManualSession()
        return linkedMapOf<String, Any?>(
            "timestamp_utc" to now.toString(),
            "local_timestamp" to now.atZone(zone).toString(),
            "timezone" to zone.id,
            "app_version_name" to BuildConfig.VERSION_NAME,
            "version_code" to BuildConfig.VERSION_CODE,
            "build_flavor" to BuildConfig.FLAVOR,
            "device_model" to Build.MODEL.take(120),
            "android_version" to Build.VERSION.RELEASE.take(40),
            "api_level" to Build.VERSION.SDK_INT,
            "daily_steps" to state.accumulatedTodaySteps,
            "hourly_steps" to hours.associate { it.hourOfDay.toString() to it.steps },
            "sensor_counter" to state.lastSensorValue,
            "previous_sensor_counter" to state.previousSensorValue,
            "sensor_delta" to state.lastSensorValue?.let { currentValue ->
                state.previousSensorValue?.let { previous -> (currentValue - previous).coerceAtLeast(0) }
            },
            "tracking_requested" to state.trackingRequested,
            "tracking_state" to state.trackingStatus.name,
            "foreground_service_state" to state.serviceRunning,
            "current_session" to current?.id?.let { "present" },
            "session_count" to hours.sumOf { if (it.sensorEventCount > 0) 1 else 0 },
            "unallocated_steps" to (activityRepository.officialProgressSnapshot(date, zone).totalSteps - hours.sumOf { it.steps }).coerceAtLeast(0),
            "counter_reset" to (state.lastStopReason?.name == "SENSOR_UNAVAILABLE"),
            "boot_session_state" to if (state.bootSessionId.isBlank()) "unknown" else "present",
            "distance" to hours.sumOf { it.distanceMeters ?: 0.0 },
            "walking_duration" to hours.sumOf { it.walkingDurationSeconds ?: 0L },
            "kcal" to hours.sumOf { it.estimatedCaloriesKcal ?: 0.0 },
            "average_speed" to hours.mapNotNull { it.averageWalkingSpeedKmh }.average().takeUnless { it.isNaN() },
            "total" to daily.totalSteps,
            "eligible" to daily.eligibleSteps,
            "restricted" to daily.restrictedSteps,
            "excluded" to daily.excludedSteps,
            "integrity_version" to daily.integrityVersion,
            "source_revision" to daily.sourceRevision,
            "official_steps" to daily.eligibleSteps,
            "last_successful_sync" to store.lastUploadEpochMillis,
        )
    }

    private suspend fun call(payload: Map<String, Any?>) {
        suspendCancellableCoroutine { continuation ->
            val task = functions.getHttpsCallable("submitQaTelemetry").call(payload)
            task.addOnSuccessListener { continuation.resume(Unit) }
                .addOnFailureListener { continuation.resumeWithException(it) }
        }
    }

    private fun refreshStatus() { _status.value = store.status() }

    companion object {
        const val WORK_NAME = "qa-telemetry-hourly-snapshot"
        private const val SNAPSHOT_BUCKET_MILLIS = 3_600_000L

        fun schedule(context: Context) {
            if (BuildConfig.FLAVOR != "qa") return
            val request = PeriodicWorkRequestBuilder<QaTelemetryWorker>(1, TimeUnit.HOURS)
                .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
                .build()
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                WORK_NAME, ExistingPeriodicWorkPolicy.UPDATE, request,
            )
        }
    }
}

class QaTelemetryWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result = runCatching {
        val app = applicationContext as StepArenaApplication
        app.qaTelemetry.captureAndUpload()
    }.fold({ Result.success() }, { Result.retry() })
}

private class QaTelemetryStore(context: Context) {
    private val preferences = context.getSharedPreferences("qa_telemetry", Context.MODE_PRIVATE)
    val deviceAlias: String = preferences.getString(KEY_ALIAS, null) ?: defaultAlias().also {
        preferences.edit().putString(KEY_ALIAS, it).apply()
    }
    val lastUploadEpochMillis: Long? get() = preferences.getLong(KEY_LAST_UPLOAD, 0L).takeIf { it > 0 }

    @Synchronized
    fun append(event: QaTelemetryEvent) {
        val current = events().filterNot { it.timestamp < Instant.now().minusSeconds(7 * 24 * 60 * 60).toString() }
            .takeLast(MAX_EVENTS - 1) + event
        preferences.edit().putString(KEY_EVENTS, encode(current)).apply()
    }

    @Synchronized fun events(): List<QaTelemetryEvent> = decode(preferences.getString(KEY_EVENTS, "[]").orEmpty())

    @Synchronized
    fun remove(ids: List<String>) {
        if (ids.isEmpty()) return
        val remaining = events().filterNot { it.eventId in ids }
        preferences.edit().putString(KEY_EVENTS, encode(remaining)).apply()
    }

    fun markUpload(at: Long) { preferences.edit().putLong(KEY_LAST_UPLOAD, at).apply() }
    fun markSnapshot(at: Long) { preferences.edit().putLong(KEY_LAST_SNAPSHOT, at).apply() }

    fun status() = QaTelemetryStatus(
        enabled = BuildConfig.FLAVOR == "qa",
        lastUploadEpochMillis = lastUploadEpochMillis,
        pendingEvents = events().size,
        lastSnapshotEpochMillis = preferences.getLong(KEY_LAST_SNAPSHOT, 0L).takeIf { it > 0 },
        deviceAlias = deviceAlias,
    )

    private fun encode(events: List<QaTelemetryEvent>): String = JSONArray().apply {
        events.forEach { event ->
            put(JSONObject().apply {
                put("eventId", event.eventId)
                put("type", event.type)
                put("timestamp", event.timestamp)
                put("data", JSONObject(event.data))
            })
        }
    }.toString()

    private fun decode(value: String): List<QaTelemetryEvent> = runCatching {
        val json = JSONArray(value)
        (0 until json.length()).mapNotNull { index ->
            val item = json.optJSONObject(index) ?: return@mapNotNull null
            val data = item.optJSONObject("data")?.toMap().orEmpty()
            QaTelemetryEvent(
                item.getString("eventId"), item.getString("type"), item.getString("timestamp"), data,
            )
        }
    }.getOrDefault(emptyList())

    private fun defaultAlias(): String = if (Build.MODEL.contains("2412DPC0AG", true)) {
        "POCO_X7_PRO_QA"
    } else if (Build.MODEL.contains("SOV41", true) || Build.MANUFACTURER.equals("SONY", true)) {
        "SOV41_QA"
    } else "ANDROID_QA"

    companion object {
        private const val KEY_ALIAS = "device_alias"
        private const val KEY_EVENTS = "events"
        private const val KEY_LAST_UPLOAD = "last_upload"
        private const val KEY_LAST_SNAPSHOT = "last_snapshot"
        private const val MAX_EVENTS = 500
    }
}

private fun JSONObject.toMap(): Map<String, Any?> = keys().asSequence().associateWith { key ->
    when (val value = opt(key)) {
        JSONObject.NULL -> null
        is JSONObject -> value.toMap()
        is JSONArray -> (0 until value.length()).map { value.opt(it) }
        else -> value
    }
}

object QaTelemetrySanitizer {
    private val forbiddenKey = Regex("(?i)(uid|email|token|secret|credential|password|ssid|bssid|access[_-]?key|challengeid)")
    private val sensitiveValue = Regex("(?i)(bearer\\s+|access_token|refresh_token|@|AIza[0-9A-Za-z_-]{20,})")

    fun sanitize(input: Map<String, Any?>): Map<String, Any?> = input.entries
        .filterNot { forbiddenKey.containsMatchIn(it.key) }
        .associate { (key, value) -> key to sanitizeValue(value) }

    private fun sanitizeValue(value: Any?): Any? = when (value) {
        is String -> if (sensitiveValue.containsMatchIn(value)) "[REDACTED]" else value.take(512)
        is Map<*, *> -> sanitize(value.entries.associate { it.key.toString() to it.value })
        is Iterable<*> -> value.map(::sanitizeValue)
        else -> value
    }
}

object QaTelemetryPolicy {
    fun isEnabled(flavor: String): Boolean = flavor == "qa"
}
