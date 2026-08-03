package com.lazyapps.steparena.auth

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.work.WorkManager
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.GoogleAuthProvider
import com.lazyapps.steparena.BuildConfig
import com.lazyapps.steparena.app.StepArenaApplication
import com.lazyapps.steparena.backup.BackupScheduler
import com.lazyapps.steparena.tracking.TrackingStateRepository
import java.time.LocalDate
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/** Read-only local verification plus persistent suppression of automatic backup for this switched account. */
@RunWith(AndroidJUnit4::class)
class QaExistingAccountVerificationTest {
    @Test fun existingGoogleAccountIsSafeAndLocalStateIsIntact() = runBlocking {
        assertEquals("qa", BuildConfig.FLAVOR)
        val app = InstrumentationRegistry.getInstrumentation().targetContext.applicationContext as StepArenaApplication
        val user = requireNotNull(FirebaseAuth.getInstance().currentUser)
        assertFalse(user.isAnonymous)
        assertTrue(user.providerData.any { it.providerId == GoogleAuthProvider.PROVIDER_ID })

        app.existingAccountSafetyStore.markPendingReview(user.uid)
        val work = WorkManager.getInstance(app)
        work.cancelUniqueWork(BackupScheduler.ONE_TIME).result.get(10, TimeUnit.SECONDS)
        work.cancelUniqueWork(BackupScheduler.PERIODIC).result.get(10, TimeUnit.SECONDS)

        val today = LocalDate.now(app.clock).toString()
        val daily = app.database.daily().all().filter { it.localDate == today }
        val processing = app.database.processingState().get()
        val tracking = TrackingStateRepository(app).current()
        val sqlite = app.database.openHelper.readableDatabase
        val version = sqlite.version
        val integrity = sqlite.query("PRAGMA integrity_check").use { cursor ->
            check(cursor.moveToFirst())
            cursor.getString(0)
        }
        assertEquals(10, version)
        assertEquals("ok", integrity)
        println("QA_EXISTING_ACCOUNT uidSuffix=${user.uid.takeLast(4)} todaySteps=${daily.sumOf { it.steps }} " +
            "today_steps=${tracking.accumulatedTodaySteps} lastCounter=${processing?.lastCounterValue} " +
            "dbVersion=$version integrity=$integrity")
    }
}
