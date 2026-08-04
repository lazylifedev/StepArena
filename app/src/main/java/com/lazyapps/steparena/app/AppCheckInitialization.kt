package com.lazyapps.steparena.app

internal fun interface AppCheckInstaller {
    fun install()
}

/** Installs the build-specific App Check provider without blocking local app startup on failure. */
internal object AppCheckInitialization {
    private val lock = Any()

    @Volatile
    private var initialized = false

    fun initialize(installer: AppCheckInstaller = BuildAppCheckInstaller) {
        if (initialized) return
        synchronized(lock) {
            if (initialized) return
            try {
                installer.install()
                initialized = true
            } catch (_: RuntimeException) {
                // Do not log the exception: provider failures can contain attestation details.
            }
        }
    }

    internal fun resetForTest() {
        synchronized(lock) {
            initialized = false
        }
    }
}
