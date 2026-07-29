package com.lazyapps.steparena.app

import android.app.Application

/**
 * Application entry point reserved for dependency injection and process-wide services.
 * Phase 0/1 intentionally uses manual construction to avoid adding an unused DI runtime.
 */
class StepArenaApplication : Application()
