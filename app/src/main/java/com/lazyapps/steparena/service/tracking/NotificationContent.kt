package com.lazyapps.steparena.service.tracking

import java.util.concurrent.atomic.AtomicBoolean

sealed interface NotificationContent {
    data object Preparing : NotificationContent

    data class Tracking(
        val todaySteps: Long,
        val goalSteps: Long,
    ) : NotificationContent

    data class Walking(
        val sessionSteps: Long,
        val elapsedMinutes: Long,
        val todaySteps: Long,
        val goalSteps: Long,
    ) : NotificationContent
}

internal class ServiceSetupGate {
    private val started = AtomicBoolean(false)

    val isStarted: Boolean get() = started.get()

    fun claimInitialStart(): Boolean = started.compareAndSet(false, true)
}
