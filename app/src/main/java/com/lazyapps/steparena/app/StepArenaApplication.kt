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

/**
 * Application entry point reserved for dependency injection and process-wide services.
 * Phase 0/1 intentionally uses manual construction to avoid adding an unused DI runtime.
 */
open class StepArenaApplication : Application() {
    private val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    val database by lazy { StepArenaDatabase.get(this) }
    val profileRepository by lazy { UserProfileRepository(this) }
    val activityRepository by lazy { ActivityRepository(database, profileRepository) }
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
    open val gameRepository by lazy { LocalGameRepository(this, database) }

    override fun onCreate() {
        super.onCreate()
        TrackingHealthWorker.schedule(this)
        GameMaintenanceWorker.schedule(this)
        applicationScope.launch {
            gameRepository.runMaintenance()
        }
    }
}
