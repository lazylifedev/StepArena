package com.lazyapps.steparena.service.tracking

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class NotificationContentTest {
    @Test fun onlyTheFirstServiceStartCanPublishPreparing() {
        val gate = ServiceSetupGate()

        assertTrue(gate.claimInitialStart())
        assertTrue(gate.isStarted)
        assertEquals(false, gate.claimInitialStart())
        assertEquals(false, gate.claimInitialStart())
    }

    @Test fun preparingCarriesNoStepOrGoalValue() {
        assertTrue(NotificationContent.Preparing is NotificationContent)
    }

    @Test fun trackingKeepsTodayStepsAndGoalDistinct() {
        val content = NotificationContent.Tracking(todaySteps = 2_096, goalSteps = 6_800)

        assertEquals(2_096, content.todaySteps)
        assertEquals(6_800, content.goalSteps)
    }

    @Test fun walkingKeepsSessionAndDailyValuesDistinct() {
        val content = NotificationContent.Walking(123, 4, 2_096, 6_800)

        assertEquals(123, content.sessionSteps)
        assertEquals(2_096, content.todaySteps)
    }
}
