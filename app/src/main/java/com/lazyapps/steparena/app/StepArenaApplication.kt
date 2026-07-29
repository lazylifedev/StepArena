package com.lazyapps.steparena.app

import android.app.Application
import com.lazyapps.steparena.activity.ActivityRepository
import com.lazyapps.steparena.activity.UserProfileRepository
import com.lazyapps.steparena.core.database.StepArenaDatabase

/**
 * Application entry point reserved for dependency injection and process-wide services.
 * Phase 0/1 intentionally uses manual construction to avoid adding an unused DI runtime.
 */
class StepArenaApplication : Application() {
    val database by lazy { StepArenaDatabase.get(this) }
    val profileRepository by lazy { UserProfileRepository(this) }
    val activityRepository by lazy { ActivityRepository(database, profileRepository) }
}
