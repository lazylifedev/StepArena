package com.lazyapps.steparena.debug

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.core.content.ContextCompat
import com.lazyapps.steparena.service.tracking.StepTrackingService
import com.lazyapps.steparena.tracking.TrackingStateRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.time.LocalDate
import com.lazyapps.steparena.app.DebugStepArenaApplication

/**
 * Debug-only ADB bridge. It is intentionally absent from release source and manifest.
 *
 * adb shell am broadcast -a com.lazyapps.steparena.debug.FAKE_SENSOR \
 *   -n com.lazyapps.steparena/.debug.DebugFakeSensorReceiver --es command value --ef value 10000
 */
class DebugFakeSensorReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val pending = goAsync()
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try {
                execute(context.applicationContext, intent)
            } finally {
                pending.finish()
            }
        }
    }

    private suspend fun execute(context: Context, intent: Intent) {
        val command = intent.getStringExtra(EXTRA_COMMAND) ?: COMMAND_VALUE
        Log.w(TAG, "DEBUG ONLY fake sensor command=$command")
        val app = context as DebugStepArenaApplication
        if (app.isIsolatedScenario && command != COMMAND_COMPLETE_ONBOARDING) {
            Log.w(TAG, "Foreground-service fake sensor ignored in isolated scenario mode")
            return
        }
        when (command) {
            COMMAND_START -> {
                TrackingStateRepository(context).update {
                    it.copy(
                        trackingRequested = true,
                        sensorBaseline = null,
                        lastSensorValue = null,
                        sessionId = null,
                    )
                }
                send(context, intent.getFloatExtra(EXTRA_VALUE, 0f))
            }
            COMMAND_STOP -> {
                val session = TrackingStateRepository(context).current().sessionId
                context.startService(
                    Intent(context, StepTrackingService::class.java)
                        .setAction(StepTrackingService.ACTION_STOP)
                        .putExtra(StepTrackingService.EXTRA_SESSION_ID, session),
                )
            }
            COMMAND_STALE_STOP -> {
                context.startService(
                    Intent(context, StepTrackingService::class.java)
                        .setAction(StepTrackingService.ACTION_STOP)
                        .putExtra(StepTrackingService.EXTRA_SESSION_ID, "stale-debug-session"),
                )
            }
            COMMAND_START_MANUAL -> {
                ContextCompat.startForegroundService(
                    context,
                    Intent(context, StepTrackingService::class.java)
                        .setAction(StepTrackingService.ACTION_START_MANUAL_WALK),
                )
            }
            COMMAND_END_MANUAL, COMMAND_STALE_MANUAL_END -> {
                val activeId = (context as com.lazyapps.steparena.app.StepArenaApplication)
                    .activityRepository.currentManualSession()?.id
                context.startService(
                    Intent(context, StepTrackingService::class.java)
                        .setAction(StepTrackingService.ACTION_END_MANUAL_WALK)
                        .putExtra(
                            StepTrackingService.EXTRA_MANUAL_SESSION_ID,
                            if (command == COMMAND_STALE_MANUAL_END) "stale-manual-uuid" else activeId,
                        ),
                )
            }
            COMMAND_VALUE -> send(context, intent.getFloatExtra(EXTRA_VALUE, Float.NaN))
            COMMAND_INCREMENT -> {
                val current = TrackingStateRepository(context).current().lastSensorValue
                    ?: intent.getLongExtra(EXTRA_BASELINE, 0L)
                send(context, (current + intent.getLongExtra(EXTRA_STEPS, 1L)).toFloat())
            }
            COMMAND_SEQUENCE -> {
                var value = intent.getLongExtra(EXTRA_BASELINE, 0L)
                send(context, value.toFloat())
                repeat(intent.getIntExtra(EXTRA_COUNT, 1).coerceIn(1, 1_000)) {
                    delay(intent.getLongExtra(EXTRA_INTERVAL_MS, 250L).coerceIn(10L, 60_000L))
                    value += intent.getLongExtra(EXTRA_STEPS, 1L)
                    send(context, value.toFloat())
                }
            }
            COMMAND_RESET -> send(context, intent.getFloatExtra(EXTRA_VALUE, 0f))
            COMMAND_DATE_CHANGE -> {
                TrackingStateRepository(context).update {
                    it.copy(currentLocalDate = LocalDate.parse(intent.getStringExtra(EXTRA_DATE)))
                }
            }
            COMMAND_TIMEZONE_CHANGE -> {
                TrackingStateRepository(context).update {
                    it.copy(currentZoneId = intent.getStringExtra(EXTRA_ZONE) ?: "UTC")
                }
            }
            COMMAND_COMPLETE_ONBOARDING -> {
                TrackingStateRepository(context).update {
                    it.copy(onboardingComplete = true, onboardingStep = 6)
                }
            }
            else -> Log.w(TAG, "Unknown debug command ignored")
        }
    }

    private fun send(context: Context, value: Float) {
        ContextCompat.startForegroundService(
            context,
            Intent(context, StepTrackingService::class.java)
                .setAction(StepTrackingService.debugAction())
                .putExtra(StepTrackingService.debugValueExtra(), value),
        )
    }

    companion object {
        const val ACTION = "com.lazyapps.steparena.debug.FAKE_SENSOR"
        const val EXTRA_COMMAND = "command"
        const val EXTRA_VALUE = "value"
        const val EXTRA_BASELINE = "baseline"
        const val EXTRA_STEPS = "steps"
        const val EXTRA_COUNT = "count"
        const val EXTRA_INTERVAL_MS = "interval_ms"
        const val EXTRA_DATE = "date"
        const val EXTRA_ZONE = "zone"
        const val COMMAND_VALUE = "value"
        const val COMMAND_START = "start"
        const val COMMAND_STOP = "stop"
        const val COMMAND_STALE_STOP = "stale_stop"
        const val COMMAND_START_MANUAL = "start_manual"
        const val COMMAND_END_MANUAL = "end_manual"
        const val COMMAND_STALE_MANUAL_END = "stale_manual_end"
        const val COMMAND_INCREMENT = "increment"
        const val COMMAND_SEQUENCE = "sequence"
        const val COMMAND_RESET = "reset"
        const val COMMAND_DATE_CHANGE = "date_change"
        const val COMMAND_TIMEZONE_CHANGE = "timezone_change"
        const val COMMAND_COMPLETE_ONBOARDING = "complete_onboarding"
        private const val TAG = "StepArenaDebug"
    }
}
