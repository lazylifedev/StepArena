package com.lazyapps.steparena.app

import com.lazyapps.steparena.core.designsystem.motion.MotionLevel
import com.lazyapps.steparena.feature.home.HomeContent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DebugHomeScenarioTest {
    @Test
    fun everyScenario_producesAnEvaluationState() {
        assertEquals(13, DebugHomeScenario.entries.size)

        DebugHomeScenario.entries.forEach { scenario ->
            val state = scenario.uiState(MotionLevel.OFF)
            assertEquals(MotionLevel.OFF, state.motionLevel)
            if (scenario == DebugHomeScenario.NO_DATA) {
                assertTrue(state.content is HomeContent.Empty)
            } else {
                assertTrue(state.content is HomeContent.Ready)
            }
        }
    }
}
