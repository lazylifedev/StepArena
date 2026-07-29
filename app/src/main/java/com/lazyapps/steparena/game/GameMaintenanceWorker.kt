package com.lazyapps.steparena.game

import android.content.Context
import androidx.work.*
import com.lazyapps.steparena.app.StepArenaApplication
import java.util.concurrent.TimeUnit

class GameMaintenanceWorker(context: Context, params: WorkerParameters) :
    CoroutineWorker(context, params) {
    override suspend fun doWork(): Result = runCatching {
        val repository = (applicationContext as StepArenaApplication).gameRepository
        repository.runMaintenance()
    }.fold(onSuccess = { Result.success() }, onFailure = { Result.retry() })

    companion object {
        const val UNIQUE_WORK = "game-maintenance"
        fun schedule(context: Context) {
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                UNIQUE_WORK, ExistingPeriodicWorkPolicy.UPDATE,
                PeriodicWorkRequestBuilder<GameMaintenanceWorker>(12, TimeUnit.HOURS)
                    .setConstraints(Constraints.Builder().setRequiresBatteryNotLow(true).build())
                    .build(),
            )
        }
    }
}
