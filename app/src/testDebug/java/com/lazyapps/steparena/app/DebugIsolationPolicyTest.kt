package com.lazyapps.steparena.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class DebugIsolationPolicyTest {
    @Test
    fun modesAreExplicitAndDefaultCanBeNormal() {
        assertEquals(DebugDataMode.NORMAL_DATA, DebugDataMode.valueOf("NORMAL_DATA"))
        assertEquals(DebugDataMode.ISOLATED_SCENARIO, DebugDataMode.valueOf("ISOLATED_SCENARIO"))
    }

    @Test
    fun debugIdentityAndDatabaseDifferFromProduction() {
        assertNotEquals(
            com.lazyapps.steparena.core.database.StepArenaDatabase.PRODUCTION_DATABASE_NAME,
            DebugStepArenaApplication.DEBUG_DATABASE_NAME,
        )
        assertEquals("step-arena-debug-scenario", DebugStepArenaApplication.DEBUG_INSTALLATION_ID)
    }
}
