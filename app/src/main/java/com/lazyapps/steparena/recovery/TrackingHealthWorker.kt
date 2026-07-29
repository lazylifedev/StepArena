package com.lazyapps.steparena.recovery

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.lazyapps.steparena.app.StepArenaApplication
import com.lazyapps.steparena.tracking.TrackingStateRepository
import java.time.Instant
import java.time.ZoneId
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.flow.first

class TrackingHealthWorker(
    appContext: Context,
    params: WorkerParameters,
) : CoroutineWorker(appContext, params) {
    override suspend fun doWork(): Result {
        val app = applicationContext as StepArenaApplication
        val state = TrackingStateRepository(applicationContext).current()
        val health = evaluateTrackingHealth(state, Instant.now())
        if (state.trackingRequested &&
            health in setOf(TrackingHealthStatus.STALE, TrackingHealthStatus.STOPPED)
        ) {
            state.lastHeartbeatAt?.let {
                val gap = app.gapRecoveryRepository.detectHeartbeatGap(
                    it,
                    Instant.now(),
                    runCatching { ZoneId.of(state.currentZoneId) }.getOrDefault(ZoneId.systemDefault()),
                    explicitUserStop = false,
                )
                val settings = app.recoverySettingsRepository.settings.first()
                if (gap != null && settings.trackingStopWarnings && !state.needsReview) {
                    TrackingStopNotifier.notify(
                        applicationContext,
                        severe = health == TrackingHealthStatus.STOPPED,
                    )
                    TrackingStateRepository(applicationContext).update {
                        current -> current.copy(
                            needsReview = true,
                            lastNotificationAt = Instant.now(),
                            trackingStatus =
                                com.lazyapps.steparena.tracking.TrackingStatus.SERVICE_HEARTBEAT_STALE,
                        )
                    }
                }
            }
        }
        return Result.success()
    }

    companion object {
        const val UNIQUE_NAME = "tracking-health-monitor"
        fun schedule(context: Context) {
            val request = PeriodicWorkRequestBuilder<TrackingHealthWorker>(
                15,
                TimeUnit.MINUTES,
            ).build()
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                UNIQUE_NAME,
                ExistingPeriodicWorkPolicy.KEEP,
                request,
            )
        }
    }
}
