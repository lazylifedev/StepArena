package com.lazyapps.steparena.tracking

import android.content.Context
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import java.time.Instant
import java.time.LocalDate

private val Context.trackingDataStore by preferencesDataStore("step_tracking")

class TrackingStateRepository(private val context: Context) {
    val state: Flow<StepTrackingState> = context.trackingDataStore.data.map(::decode)

    suspend fun current(): StepTrackingState = state.first()
    suspend fun clear() { context.trackingDataStore.edit { it.clear() } }
    suspend fun update(transform: (StepTrackingState) -> StepTrackingState): StepTrackingState {
        var result = StepTrackingState()
        context.trackingDataStore.edit { preferences ->
            result = transform(decode(preferences))
            encode(preferences, result)
        }
        return result
    }

    suspend fun saveDailySummary(summary: DailyStepSummary) {
        context.trackingDataStore.edit {
            it[Keys.DAILY_DATE] = summary.localDate.toString()
            it[Keys.DAILY_ZONE] = summary.zoneId
            it[Keys.DAILY_STEPS] = summary.steps
            it[Keys.DAILY_FINALIZED] = summary.finalizedAt.toEpochMilli()
        }
    }

    suspend fun lastDailySummary(): DailyStepSummary? {
        val p = context.trackingDataStore.data.first()
        val date = p[Keys.DAILY_DATE] ?: return null
        return DailyStepSummary(
            localDate = LocalDate.parse(date),
            zoneId = p[Keys.DAILY_ZONE].orEmpty(),
            steps = p[Keys.DAILY_STEPS] ?: 0,
            finalizedAt = Instant.ofEpochMilli(p[Keys.DAILY_FINALIZED] ?: 0),
        )
    }

    private fun decode(p: Preferences) = StepTrackingState(
        trackingRequested = p[Keys.REQUESTED] ?: false,
        trackingStatus = enumValueOr(p[Keys.STATUS], TrackingStatus.INITIALIZING),
        bootSessionId = p[Keys.BOOT].orEmpty(),
        sensorBaseline = p[Keys.BASELINE],
        lastSensorValue = p[Keys.LAST_VALUE],
        accumulatedTodaySteps = p[Keys.STEPS] ?: 0,
        currentLocalDate = runCatching { LocalDate.parse(p[Keys.DATE]) }.getOrDefault(LocalDate.now()),
        currentZoneId = p[Keys.ZONE] ?: java.time.ZoneId.systemDefault().id,
        lastSensorEventAt = instant(p[Keys.LAST_SENSOR]),
        lastHeartbeatAt = instant(p[Keys.HEARTBEAT]),
        lastServiceStartedAt = instant(p[Keys.STARTED]),
        lastServiceStoppedAt = instant(p[Keys.STOPPED]),
        lastStopReason = enumValueOrNullable<TrackingStopReason>(p[Keys.STOP_REASON]),
        sessionId = p[Keys.SESSION],
        onboardingStep = p[Keys.ONBOARDING_STEP] ?: 0,
        onboardingComplete = p[Keys.ONBOARDING_COMPLETE] ?: false,
        onboardingVersion = p[Keys.ONBOARDING_VERSION] ?: 0,
        trackingExplanationSeen = p[Keys.TRACKING_EXPLANATION] ?: false,
        notificationExplanationSeen = p[Keys.NOTIFICATION_EXPLANATION] ?: false,
        healthConnectExplanationSeen = p[Keys.HEALTH_EXPLANATION] ?: false,
        gameRulesExplanationSeen = p[Keys.GAME_EXPLANATION] ?: false,
        privacyPolicyVersionSeen = p[Keys.PRIVACY_VERSION] ?: 0,
        batteryGuidanceAcknowledged = p[Keys.BATTERY_ACK] ?: false,
        notificationGuidanceAcknowledged = p[Keys.NOTIFICATION_ACK] ?: false,
        needsReview = p[Keys.NEEDS_REVIEW] ?: false,
        lastNotificationAt = instant(p[Keys.LAST_NOTIFICATION]),
        lastExitInfoKey = p[Keys.LAST_EXIT_KEY],
        lastExitSummary = p[Keys.LAST_EXIT_SUMMARY],
    )

    private fun encode(p: androidx.datastore.preferences.core.MutablePreferences, s: StepTrackingState) {
        p[Keys.REQUESTED] = s.trackingRequested
        p[Keys.STATUS] = s.trackingStatus.name
        p[Keys.BOOT] = s.bootSessionId
        nullable(p, Keys.BASELINE, s.sensorBaseline)
        nullable(p, Keys.LAST_VALUE, s.lastSensorValue)
        p[Keys.STEPS] = s.accumulatedTodaySteps
        p[Keys.DATE] = s.currentLocalDate.toString()
        p[Keys.ZONE] = s.currentZoneId
        nullable(p, Keys.LAST_SENSOR, s.lastSensorEventAt?.toEpochMilli())
        nullable(p, Keys.HEARTBEAT, s.lastHeartbeatAt?.toEpochMilli())
        nullable(p, Keys.STARTED, s.lastServiceStartedAt?.toEpochMilli())
        nullable(p, Keys.STOPPED, s.lastServiceStoppedAt?.toEpochMilli())
        nullable(p, Keys.STOP_REASON, s.lastStopReason?.name)
        nullable(p, Keys.SESSION, s.sessionId)
        p[Keys.ONBOARDING_STEP] = s.onboardingStep
        p[Keys.ONBOARDING_COMPLETE] = s.onboardingComplete
        p[Keys.ONBOARDING_VERSION] = s.onboardingVersion
        p[Keys.TRACKING_EXPLANATION] = s.trackingExplanationSeen
        p[Keys.NOTIFICATION_EXPLANATION] = s.notificationExplanationSeen
        p[Keys.HEALTH_EXPLANATION] = s.healthConnectExplanationSeen
        p[Keys.GAME_EXPLANATION] = s.gameRulesExplanationSeen
        p[Keys.PRIVACY_VERSION] = s.privacyPolicyVersionSeen
        p[Keys.BATTERY_ACK] = s.batteryGuidanceAcknowledged
        p[Keys.NOTIFICATION_ACK] = s.notificationGuidanceAcknowledged
        p[Keys.NEEDS_REVIEW] = s.needsReview
        nullable(p, Keys.LAST_NOTIFICATION, s.lastNotificationAt?.toEpochMilli())
        nullable(p, Keys.LAST_EXIT_KEY, s.lastExitInfoKey)
        nullable(p, Keys.LAST_EXIT_SUMMARY, s.lastExitSummary)
    }

    private fun instant(value: Long?): Instant? = value?.let(Instant::ofEpochMilli)
    private inline fun <reified T : Enum<T>> enumValueOr(value: String?, fallback: T): T =
        value?.let { runCatching { enumValueOf<T>(it) }.getOrNull() } ?: fallback
    private inline fun <reified T : Enum<T>> enumValueOrNullable(value: String?): T? =
        value?.let { runCatching { enumValueOf<T>(it) }.getOrNull() }

    private fun <T> nullable(
        p: androidx.datastore.preferences.core.MutablePreferences,
        key: Preferences.Key<T>,
        value: T?,
    ) {
        if (value == null) p.remove(key) else p[key] = value
    }

    private object Keys {
        val REQUESTED = booleanPreferencesKey("tracking_requested")
        val STATUS = stringPreferencesKey("tracking_status")
        val BOOT = stringPreferencesKey("boot_session")
        val BASELINE = longPreferencesKey("sensor_baseline")
        val LAST_VALUE = longPreferencesKey("last_sensor_value")
        val STEPS = longPreferencesKey("today_steps")
        val DATE = stringPreferencesKey("local_date")
        val ZONE = stringPreferencesKey("zone_id")
        val LAST_SENSOR = longPreferencesKey("last_sensor_at")
        val HEARTBEAT = longPreferencesKey("heartbeat_at")
        val STARTED = longPreferencesKey("service_started_at")
        val STOPPED = longPreferencesKey("service_stopped_at")
        val STOP_REASON = stringPreferencesKey("stop_reason")
        val SESSION = stringPreferencesKey("session_id")
        val ONBOARDING_STEP = intPreferencesKey("onboarding_step")
        val ONBOARDING_COMPLETE = booleanPreferencesKey("onboarding_complete")
        val ONBOARDING_VERSION = intPreferencesKey("onboarding_version")
        val TRACKING_EXPLANATION = booleanPreferencesKey("tracking_explanation_seen")
        val NOTIFICATION_EXPLANATION = booleanPreferencesKey("notification_explanation_seen")
        val HEALTH_EXPLANATION = booleanPreferencesKey("health_connect_explanation_seen")
        val GAME_EXPLANATION = booleanPreferencesKey("game_rules_explanation_seen")
        val PRIVACY_VERSION = intPreferencesKey("privacy_policy_version_seen")
        val BATTERY_ACK = booleanPreferencesKey("battery_guidance_ack")
        val NOTIFICATION_ACK = booleanPreferencesKey("notification_guidance_ack")
        val NEEDS_REVIEW = booleanPreferencesKey("needs_review")
        val LAST_NOTIFICATION = longPreferencesKey("last_notification_at")
        val LAST_EXIT_KEY = stringPreferencesKey("last_exit_key")
        val LAST_EXIT_SUMMARY = stringPreferencesKey("last_exit_summary")
        val DAILY_DATE = stringPreferencesKey("daily_date")
        val DAILY_ZONE = stringPreferencesKey("daily_zone")
        val DAILY_STEPS = longPreferencesKey("daily_steps")
        val DAILY_FINALIZED = longPreferencesKey("daily_finalized")
    }
}
