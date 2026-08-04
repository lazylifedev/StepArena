package com.lazyapps.steparena.backup

import androidx.work.WorkInfo
import androidx.work.WorkManager
import com.google.firebase.auth.FirebaseUser
import com.lazyapps.steparena.app.StepArenaApplication
import java.util.concurrent.TimeUnit
import org.junit.Assert.assertTrue

/** Prevents an already-scheduled automatic backup from racing an explicit real-cloud QA test. */
internal suspend fun StepArenaApplication.suppressAutomaticBackupForQaTest(user: FirebaseUser) {
    existingAccountSafetyStore.markPendingReview(user.uid)
    val workManager = WorkManager.getInstance(this)
    workManager.cancelUniqueWork(BackupScheduler.ONE_TIME).result.get(10, TimeUnit.SECONDS)
    workManager.cancelUniqueWork(BackupScheduler.PERIODIC).result.get(10, TimeUnit.SECONDS)
    val remaining = listOf(BackupScheduler.ONE_TIME, BackupScheduler.PERIODIC)
        .flatMap { workManager.getWorkInfosForUniqueWork(it).get(10, TimeUnit.SECONDS) }
    assertTrue(
        "Automatic backup work must be quiescent before explicit cloud QA",
        remaining.none { it.state == WorkInfo.State.RUNNING || it.state == WorkInfo.State.ENQUEUED || it.state == WorkInfo.State.BLOCKED },
    )
    backupOperationGate.awaitIdle()
    cloudRestoreRepository.check()
    backupOperationGate.awaitIdle()
}
