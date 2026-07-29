package com.lazyapps.steparena.test

import android.util.Log
import androidx.activity.ComponentActivity
import androidx.compose.ui.test.junit4.AndroidComposeTestRule
import androidx.lifecycle.Lifecycle
import androidx.test.ext.junit.rules.ActivityScenarioRule
import org.junit.Assert.fail

private const val TAG = "StepArenaComposeTest"

fun AndroidComposeTestRule<ActivityScenarioRule<ComponentActivity>, ComponentActivity>.awaitResumedHost() {
    val scenario = activityRule.scenario
    val initialState = scenario.state
    Log.i(TAG, "awaitResumedHost initialState=$initialState")
    if (initialState == Lifecycle.State.DESTROYED) {
        fail("Compose test host is closed before setContent; scenarioState=$initialState")
    }
    waitUntil(timeoutMillis = 10_000) {
        scenario.state == Lifecycle.State.RESUMED
    }
    waitForIdle()
    Log.i(TAG, "awaitResumedHost ready state=${scenario.state}")
}
