package com.lazyapps.steparena.backup

import android.content.Context
import androidx.work.*
import com.lazyapps.steparena.app.StepArenaApplication
import java.util.concurrent.TimeUnit

class BackupWorker(context: Context, parameters: WorkerParameters) : CoroutineWorker(context, parameters) {
    override suspend fun doWork(): Result = when (val result =
        (applicationContext as StepArenaApplication).cloudBackupRepository.backupNow()) {
        is BackupResult.Success, is BackupResult.Skipped -> Result.success()
        is BackupResult.Failure -> if (result.category in RETRYABLE) Result.retry() else Result.failure()
    }

    private companion object {
        val RETRYABLE = setOf(BackupErrorCategory.NETWORK, BackupErrorCategory.UNKNOWN)
    }
}

class BackupScheduler(private val context: Context) {
    fun schedulePeriodic() {
        val request = PeriodicWorkRequestBuilder<BackupWorker>(6, TimeUnit.HOURS)
            .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).setRequiresBatteryNotLow(true).build())
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
            .build()
        WorkManager.getInstance(context).enqueueUniquePeriodicWork(PERIODIC, ExistingPeriodicWorkPolicy.KEEP, request)
    }

    fun enqueueOneTime() {
        val request = OneTimeWorkRequestBuilder<BackupWorker>()
            .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
            .setInitialDelay(30, TimeUnit.SECONDS)
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
            .build()
        WorkManager.getInstance(context).enqueueUniqueWork(ONE_TIME, ExistingWorkPolicy.KEEP, request)
    }

    companion object {
        const val PERIODIC = "cloud-backup-periodic-v1"
        const val ONE_TIME = "cloud-backup-one-time-v1"
    }
}
