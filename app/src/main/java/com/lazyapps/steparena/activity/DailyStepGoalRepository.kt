package com.lazyapps.steparena.activity

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.distinctUntilChanged

object DailyStepGoal {
    const val DEFAULT = 10_000
    const val MINIMUM = 1_000
    const val MAXIMUM = 100_000

    fun persistedOrDefault(value: Int?): Int =
        value?.takeIf { it in MINIMUM..MAXIMUM } ?: DEFAULT
}

/**
 * Single source of truth for the user's daily step goal.
 *
 * SharedPreferences is used intentionally so the foreground service can read the current goal
 * synchronously before it must post its initial notification.
 */
class DailyStepGoalRepository(context: Context) {
    private val preferences = context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)

    val goalSteps: Flow<Int> = callbackFlow {
        val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
            if (key == KEY_GOAL_STEPS) trySend(current())
        }
        trySend(current())
        preferences.registerOnSharedPreferenceChangeListener(listener)
        awaitClose { preferences.unregisterOnSharedPreferenceChangeListener(listener) }
    }.distinctUntilChanged()

    fun current(): Int =
        DailyStepGoal.persistedOrDefault(
            if (preferences.contains(KEY_GOAL_STEPS)) {
                preferences.getInt(KEY_GOAL_STEPS, DailyStepGoal.DEFAULT)
            } else {
                null
            },
        )

    fun save(goalSteps: Int) {
        require(goalSteps in DailyStepGoal.MINIMUM..DailyStepGoal.MAXIMUM)
        preferences.edit { putInt(KEY_GOAL_STEPS, goalSteps) }
    }

    fun reset() {
        preferences.edit { remove(KEY_GOAL_STEPS) }
    }

    private companion object {
        const val PREFERENCES_NAME = "daily_step_goal"
        const val KEY_GOAL_STEPS = "goal_steps"
    }
}
