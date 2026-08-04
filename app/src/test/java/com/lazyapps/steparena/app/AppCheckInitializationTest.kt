package com.lazyapps.steparena.app

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Test

class AppCheckInitializationTest {
    @After
    fun tearDown() {
        AppCheckInitialization.resetForTest()
    }

    @Test
    fun initialize_installsProviderOnlyOnce() {
        var calls = 0
        val installer = AppCheckInstaller { calls++ }

        AppCheckInitialization.initialize(installer)
        AppCheckInitialization.initialize(installer)

        assertEquals(1, calls)
    }

    @Test
    fun initialize_allowsRetryAfterProviderFailure() {
        var calls = 0
        val installer = AppCheckInstaller {
            calls++
            if (calls == 1) error("attestation details must not be logged")
        }

        AppCheckInitialization.initialize(installer)
        AppCheckInitialization.initialize(installer)

        assertEquals(2, calls)
    }
}
