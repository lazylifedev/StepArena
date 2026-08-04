package com.lazyapps.steparena.auth

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.google.android.gms.tasks.Task
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.GoogleAuthProvider
import com.lazyapps.steparena.BuildConfig
import com.lazyapps.steparena.app.StepArenaApplication
import com.lazyapps.steparena.tracking.TrackingStateRepository
import java.time.LocalDate
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.suspendCancellableCoroutine
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/** QA-only authentication reset. It never deletes/unlinks a user or modifies local application data. */
@RunWith(AndroidJUnit4::class)
class QaAuthResetTest {
    @Test fun signOutAndCreateFreshAnonymousUserPreservesLocalState() = runBlocking {
        assertEquals("qa", BuildConfig.FLAVOR)
        val app = InstrumentationRegistry.getInstrumentation().targetContext.applicationContext as StepArenaApplication
        val auth = FirebaseAuth.getInstance()
        val googleUser = requireNotNull(auth.currentUser)
        assertFalse(googleUser.isAnonymous)
        assertTrue(googleUser.providerData.any { it.providerId == GoogleAuthProvider.PROVIDER_ID })

        val today = LocalDate.now(app.clock).toString()
        val dailyBefore = app.database.daily().all().filter { it.localDate == today }
        val processingBefore = app.database.processingState().get()
        val trackingBefore = TrackingStateRepository(app).current()
        val googleSuffix = googleUser.uid.takeLast(4)

        auth.signOut()
        val anonymous = auth.signInAnonymously().await().user
        requireNotNull(anonymous)
        assertTrue(anonymous.isAnonymous)
        assertNotEquals(googleUser.uid, anonymous.uid)

        assertEquals(dailyBefore, app.database.daily().all().filter { it.localDate == today })
        assertEquals(processingBefore, app.database.processingState().get())
        assertEquals(trackingBefore, TrackingStateRepository(app).current())
        println("QA_AUTH_RESET googleUidSuffix=$googleSuffix anonymousUidSuffix=${anonymous.uid.takeLast(4)} " +
            "todaySteps=${dailyBefore.sumOf { it.steps }} today_steps=${trackingBefore.accumulatedTodaySteps} " +
            "lastCounter=${processingBefore?.lastCounterValue}")
    }
}

private suspend fun <T> Task<T>.await(): T = suspendCancellableCoroutine { continuation ->
    addOnSuccessListener { if (continuation.isActive) continuation.resume(it) }
    addOnFailureListener { if (continuation.isActive) continuation.resumeWithException(it) }
    addOnCanceledListener { continuation.cancel() }
}
