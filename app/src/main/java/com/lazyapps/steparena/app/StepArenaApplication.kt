package com.lazyapps.steparena.app

import android.app.Application
import com.lazyapps.steparena.activity.ActivityRepository
import com.lazyapps.steparena.activity.UserProfileRepository
import com.lazyapps.steparena.core.database.StepArenaDatabase
import com.lazyapps.steparena.recovery.GapRecoveryRepository
import com.lazyapps.steparena.recovery.HealthConnectActivityDataSource
import com.lazyapps.steparena.recovery.RecoverySettingsRepository
import com.lazyapps.steparena.recovery.TrackingHealthWorker
import com.lazyapps.steparena.game.LocalGameRepository
import com.lazyapps.steparena.game.GameMaintenanceWorker
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.time.Clock
import com.lazyapps.steparena.release.DataManagementRepository

interface AppGraph {
    val database: StepArenaDatabase
    val activityRepository: ActivityRepository
    val gameRepository: LocalGameRepository
    val productionGameRepository: LocalGameRepository
    val clock: Clock
    val installationId: String?
    val isolatedScenario: Boolean
}

/**
 * Application entry point reserved for dependency injection and process-wide services.
 * Phase 0/1 intentionally uses manual construction to avoid adding an unused DI runtime.
 */
open class StepArenaApplication : Application(), AppGraph {
    private val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    override val database by lazy { StepArenaDatabase.get(this) }
    val profileRepository by lazy { UserProfileRepository(this) }
    override val activityRepository by lazy { ActivityRepository(database, profileRepository) }
    val externalActivityDataSource by lazy { HealthConnectActivityDataSource(this) }
    val recoverySettingsRepository by lazy { RecoverySettingsRepository(this) }
    val gapRecoveryRepository by lazy {
        GapRecoveryRepository(
            database,
            externalActivityDataSource,
            packageName,
            activityRepository,
        )
    }
    override val clock: Clock by lazy { Clock.systemDefaultZone() }
    override val installationId: String? = null
    override val isolatedScenario: Boolean = false
    override val gameRepository by lazy {
        LocalGameRepository(this, database, clock, installationIdOverride = installationId)
    }
    override val productionGameRepository: LocalGameRepository get() = gameRepository

    override fun onCreate() {
        super.onCreate()
        scheduleBackgroundWork()
        applicationScope.launch {
            DataManagementRepository(this@StepArenaApplication).completeInterruptedDeletionIfNeeded()
            gameRepository.runMaintenance()
        }
    }

    protected open fun scheduleBackgroundWork() {
        TrackingHealthWorker.schedule(this)
        GameMaintenanceWorker.schedule(this)
    }
}
