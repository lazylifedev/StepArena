package com.lazyapps.steparena.tracking

import android.app.ApplicationExitInfo
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ApplicationExitDiagnosticsTest {
    @Test fun knownReason_isNamed() {
        assertEquals("LOW_MEMORY", exitReasonLabel(ApplicationExitInfo.REASON_LOW_MEMORY))
    }

    @Test fun unknownReason_isExplicitlyUnknown() {
        assertEquals("不明(999)", exitReasonLabel(999))
    }

    @Test fun processedExitRecord_isNotProcessedTwice() {
        assertFalse(isNewExitRecord("same", "same"))
        assertTrue(isNewExitRecord("new", "old"))
    }

    @Test fun packageInstall_isNotMisclassifiedAsSettingsForceStop() {
        val packageName = "com.lazyapps.steparena"
        assertFalse(
            isLikelySettingsForceStop(
                ApplicationExitInfo.REASON_USER_REQUESTED,
                "stop $packageName due to installPackageLI",
                packageName,
            ),
        )
        assertTrue(
            isLikelySettingsForceStop(
                ApplicationExitInfo.REASON_USER_REQUESTED,
                "stop $packageName",
                packageName,
            ),
        )
    }
}
