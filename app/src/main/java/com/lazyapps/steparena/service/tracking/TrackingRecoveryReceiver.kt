package com.lazyapps.steparena.service.tracking

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.core.content.ContextCompat
import com.lazyapps.steparena.tracking.TrackingStateRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class TrackingRecoveryReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action !in SUPPORTED_ACTIONS) return
        val pending = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val state = TrackingStateRepository(context).current()
                if (state.onboardingComplete && state.trackingRequested) {
                    ContextCompat.startForegroundService(
                        context,
                        Intent(context, StepTrackingService::class.java)
                            .setAction(StepTrackingService.ACTION_START),
                    )
                }
            } catch (error: RuntimeException) {
                // Foreground activity reconciliation is the guaranteed retry path.
                Log.w("TrackingRecovery", "Deferred until foreground: ${intent.action}", error)
            } finally {
                pending.finish()
            }
        }
    }

    companion object {
        private val SUPPORTED_ACTIONS = setOf(
            Intent.ACTION_BOOT_COMPLETED,
            Intent.ACTION_MY_PACKAGE_REPLACED,
        )
    }
}
