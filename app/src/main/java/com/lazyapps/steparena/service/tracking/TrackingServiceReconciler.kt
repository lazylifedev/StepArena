package com.lazyapps.steparena.service.tracking

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat
import com.lazyapps.steparena.tracking.StepTrackingState
import com.lazyapps.steparena.tracking.TrackingStateRepository

object TrackingServiceProcessRegistry {
    @Volatile var serviceAlive: Boolean = false
        internal set
}

internal fun shouldRequestTrackingServiceStart(
    state: StepTrackingState,
    permissionGranted: Boolean,
): Boolean = state.onboardingComplete && state.trackingRequested && permissionGranted

class TrackingServiceReconciler(
    private val context: Context,
    private val repository: TrackingStateRepository = TrackingStateRepository(context),
    private val start: (Intent) -> Unit = { ContextCompat.startForegroundService(context, it) },
) {
    suspend fun reconcileForeground(): Boolean {
        val state = repository.current()
        val permissionGranted = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.ACTIVITY_RECOGNITION,
        ) == PackageManager.PERMISSION_GRANTED
        if (!shouldRequestTrackingServiceStart(state, permissionGranted)) return false
        start(
            Intent(context, StepTrackingService::class.java)
                .setAction(StepTrackingService.ACTION_START),
        )
        return true
    }
}
