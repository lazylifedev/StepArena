package com.lazyapps.steparena.app

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.android.gms.tasks.Tasks
import com.google.firebase.appcheck.FirebaseAppCheck
import com.google.firebase.auth.FirebaseAuth
import com.lazyapps.steparena.BuildConfig
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.util.concurrent.TimeUnit

@RunWith(AndroidJUnit4::class)
class QaAppCheckVerificationTest {
    @Test
    fun debugProviderReturnsTokenWithoutChangingFirebaseAccount() {
        assumeTrue("QA-only App Check verification", BuildConfig.FLAVOR == "qa")
        assertEquals("debug", BuildConfig.BUILD_TYPE)
        val uidBefore = FirebaseAuth.getInstance().currentUser?.uid
        assertNotNull("QA verification requires the existing linked account", uidBefore)

        try {
            val result = Tasks.await(
                FirebaseAppCheck.getInstance().getAppCheckToken(true),
                30,
                TimeUnit.SECONDS,
            )
            assertFalse("App Check returned an empty token", result.token.isBlank())
        } finally {
            assertEquals(uidBefore, FirebaseAuth.getInstance().currentUser?.uid)
        }
    }
}
