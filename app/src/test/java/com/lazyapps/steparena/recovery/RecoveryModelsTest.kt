package com.lazyapps.steparena.recovery

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.Instant

class RecoveryModelsTest {
    private val start = Instant.parse("2026-07-29T00:00:00Z")

    @Test fun validationRejectsNegativeAndInvalidIntervals() {
        assertEquals(
            SegmentValidation.REJECTED_NEGATIVE_STEPS,
            segment(steps = -1).validation(),
        )
        assertEquals(
            SegmentValidation.REJECTED_INVALID_INTERVAL,
            segment(end = start).validation(),
        )
        assertEquals(SegmentValidation.VALID, segment(steps = 0).validation())
    }

    @Test fun clippingUsesProportionalStepsAndRejectsOutsideRange() {
        val value = segment(steps = 120, end = start.plusSeconds(120))
        assertEquals(
            60L,
            value.clippedTo(start.plusSeconds(60), start.plusSeconds(120))?.steps,
        )
        assertNull(value.clippedTo(start.plusSeconds(121), start.plusSeconds(180)))
    }

    @Test fun fingerprintIsStableButChangesForUpdatedRecord() {
        assertEquals(segment().fingerprint(), segment().fingerprint())
        assertNotEquals(segment().fingerprint(), segment(steps = 101).fingerprint())
    }

    @Test fun ownOriginAndOnDeviceOriginsAreClassified() {
        assertEquals(
            ExternalDataOriginType.STEP_ARENA,
            classifyDataOrigin("com.lazyapps.steparena", "com.lazyapps.steparena"),
        )
        assertEquals(
            ExternalDataOriginType.ANDROID_ON_DEVICE,
            classifyDataOrigin("android", "com.lazyapps.steparena"),
        )
        assertEquals(
            ExternalDataOriginType.ANDROID_ON_DEVICE,
            classifyDataOrigin(
                "com.android.healthconnect.phone.synthetic",
                "com.lazyapps.steparena",
            ),
        )
    }

    private fun segment(
        steps: Long = 100,
        end: Instant = start.plusSeconds(60),
    ) = ExternalStepSegment(
        start = start,
        end = end,
        steps = steps,
        dataOriginPackage = "fitness.app",
        recordId = "record",
        lastModifiedAt = start,
        recordingMethod = ExternalRecordingMethod.AUTOMATIC,
    )
}
