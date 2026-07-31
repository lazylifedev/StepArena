package com.lazyapps.steparena.activity

import com.lazyapps.steparena.feature.game.Phase721ChallengeFixture
import org.junit.Assert.assertEquals
import org.junit.Test

class DailyStepGoalTest {
    @Test fun defaultGoalMatchesPhase721AcrossScreenAndNotificationConsumers() {
        assertEquals(10_000, DailyStepGoal.DEFAULT)
        assertEquals(Phase721ChallengeFixture.DAILY_GOAL_STEPS, DailyStepGoal.DEFAULT)
        assertEquals(10_000, DailyStepGoal.persistedOrDefault(null))
        assertEquals(10_000, DailyStepGoal.persistedOrDefault(10_000))
    }

    @Test fun invalidPersistedGoalFallsBackToTheSharedDefault() {
        assertEquals(DailyStepGoal.DEFAULT, DailyStepGoal.persistedOrDefault(999))
        assertEquals(DailyStepGoal.DEFAULT, DailyStepGoal.persistedOrDefault(100_001))
    }
}
