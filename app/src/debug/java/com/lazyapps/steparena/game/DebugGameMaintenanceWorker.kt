package com.lazyapps.steparena.game

import android.content.Context
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.Data
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.lazyapps.steparena.app.DebugDataMode
import com.lazyapps.steparena.app.DebugStepArenaApplication
import java.util.concurrent.TimeUnit

class DebugGameMaintenanceWorker(context: Context, params: WorkerParameters) :
    CoroutineWorker(context, params) {
    override suspend fun doWork(): Result = runCatching {
        check(inputData.getString(KEY_DATA_AREA) == DATA_AREA_DEBUG)
        val app = applicationContext as DebugStepArenaApplication
        check(app.dataMode == DebugDataMode.ISOLATED_SCENARIO)
        app.gameRepository.runMaintenance()
    }.fold(onSuccess = { Result.success() }, onFailure = { Result.failure() })

    companion object {
        const val UNIQUE_WORK = "game-maintenance-debug-scenario"
        const val KEY_DATA_AREA = "game_data_area"
        const val DATA_AREA_DEBUG = "debug_scenario"

        fun schedule(context: Context) {
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                UNIQUE_WORK,
                ExistingPeriodicWorkPolicy.UPDATE,
                PeriodicWorkRequestBuilder<DebugGameMaintenanceWorker>(12, TimeUnit.HOURS)
                    .setInputData(Data.Builder().putString(KEY_DATA_AREA, DATA_AREA_DEBUG).build())
                    .setConstraints(Constraints.Builder().setRequiresBatteryNotLow(true).build())
                    .build(),
            )
        }

        fun cancel(context: Context) {
            WorkManager.getInstance(context).cancelUniqueWork(UNIQUE_WORK)
        }
    }
}
