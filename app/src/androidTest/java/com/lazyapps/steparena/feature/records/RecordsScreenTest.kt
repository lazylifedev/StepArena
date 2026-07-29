package com.lazyapps.steparena.feature.records

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.unit.dp
import com.lazyapps.steparena.core.database.entity.DailyActivityRecordEntity
import com.lazyapps.steparena.core.database.entity.HourlyActivityRecordEntity
import com.lazyapps.steparena.core.database.model.DataQuality
import com.lazyapps.steparena.core.designsystem.theme.StepArenaTheme
import com.lazyapps.steparena.test.awaitResumedHost
import org.junit.Before
import org.junit.Rule
import org.junit.Test

class RecordsScreenTest {
    @get:Rule val composeRule = createAndroidComposeRule<ComponentActivity>()

    @Before fun awaitHost() = composeRule.awaitResumedHost()

    @Test fun dailySummary_changesWithAllMetrics_andNeverTotalsSpeed() {
        setRecords()
        composeRule.onNodeWithTag("record_period_daily").performClick()
        listOf(
            "steps" to "5,000歩",
            "distance" to "3.42 km",
            "duration" to "52分",
            "calories" to "180 kcal",
            "speed" to "4.8 km/h",
        ).forEach { (metric, value) ->
            composeRule.onNodeWithTag("record_metric_$metric").performClick()
            composeRule.onNodeWithTag("records_screen").performScrollToNode(hasText(value))
            composeRule.onNodeWithText(value).assertIsDisplayed()
        }
        composeRule.onNodeWithText("合計速度", substring = true).assertDoesNotExist()
    }

    @Test fun recordedHours_canBeSelectedWithoutTouchingBars_andControlsAre48dp() {
        setRecords()
        composeRule.onNodeWithTag("records_screen").performScrollToNode(hasTestTag("selected_hour_title"))
        composeRule.onNodeWithTag("selected_hour_title")
            .assertIsDisplayed()
            .assertTextEquals("1時台（UTC+09:00）")
        composeRule.onNodeWithTag("previous_hour").assertHeightIsAtLeast(48.dp)
        composeRule.onNodeWithTag("next_hour").assertHeightIsAtLeast(48.dp).performClick()
        composeRule.onNodeWithTag("selected_hour_title").assertTextEquals("2時台（UTC+09:00）")
    }

    @Test fun duplicateDstHour_isSwitchedInInstantOrderByUtcOffset() {
        setRecords(
            hours = listOf(
                hour(1_000, 1, -14_400),
                hour(2_000, 1, -18_000),
            ),
        )
        composeRule.onNodeWithTag("records_screen").performScrollToNode(hasTestTag("selected_hour_title"))
        composeRule.onNodeWithTag("selected_hour_title")
            .assertIsDisplayed()
            .assertTextEquals("1時台（UTC-04:00）")
        composeRule.onNodeWithTag("next_hour").performClick()
        composeRule.onNodeWithTag("selected_hour_title").assertTextEquals("1時台（UTC-05:00）")
    }

    private fun setRecords(hours: List<HourlyActivityRecordEntity> = defaultHours) {
        composeRule.setContent {
            StepArenaTheme {
                RecordsContent(daily(), hours, emptyList())
            }
        }
    }

    private fun daily() = DailyActivityRecordEntity(
        id = "day", localDate = "2026-07-30", zoneId = "Asia/Tokyo",
        steps = 5000, unclassifiedSteps = 0, unclassifiedStepsQuality = DataQuality.UNKNOWN,
        distanceMeters = 3420.0, walkingDurationSeconds = 3120,
        estimatedCaloriesKcal = 180.0, averageWalkingSpeedKmh = 4.8,
        stepsQuality = DataQuality.MEASURED, distanceQuality = DataQuality.ESTIMATED,
        durationQuality = DataQuality.MEASURED, caloriesQuality = DataQuality.ESTIMATED,
        speedQuality = DataQuality.ESTIMATED, activeHourCount = 2, walkingSessionCount = 1,
        finalized = false, finalizedAtEpochMillis = null,
        createdAtEpochMillis = 0, updatedAtEpochMillis = 0,
    )

    private val defaultHours get() = listOf(hour(1_000, 1), hour(2_000, 2))

    private fun hour(start: Long, localHour: Int, offset: Int = 32_400) =
        HourlyActivityRecordEntity(
            id = start.toString(), localDate = "2026-07-30", hourOfDay = localHour,
            zoneId = "Asia/Tokyo", utcOffsetSeconds = offset,
            periodStartEpochMillis = start, periodEndEpochMillis = start + 3_600_000,
            steps = 1000, distanceMeters = 700.0, walkingDurationSeconds = 600,
            estimatedCaloriesKcal = 40.0, averageWalkingSpeedKmh = 4.2,
            stepsQuality = DataQuality.MEASURED, distanceQuality = DataQuality.ESTIMATED,
            durationQuality = DataQuality.MEASURED, caloriesQuality = DataQuality.ESTIMATED,
            speedQuality = DataQuality.ESTIMATED,
            firstActivityAtEpochMillis = null, lastActivityAtEpochMillis = null,
            sensorEventCount = 1, recoveredSteps = 0, estimatedSteps = 0,
            appliedStepLengthMeters = .7, appliedWeightKg = 60.0, calorieFormulaVersion = 1,
            createdAtEpochMillis = 0, updatedAtEpochMillis = 0,
        )
}
