package com.lazyapps.steparena.service.tracking

import com.lazyapps.steparena.game.MotionSample
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant

class MotionCaptureControllerTest {
    @Test fun bufferIsBoundedAndResetDropsOldWindow() {
        val controller = MotionCaptureController(maxSamplesPerSensor = 32)
        controller.onDetector(Instant.EPOCH)
        repeat(100_000) { controller.addGyroscope(MotionSample(it.toLong(), 1f, 0f, 0f)) }
        assertEquals(0 to 32, controller.sampleCounts())
        controller.reset()
        assertFalse(controller.isCapturing())
        assertEquals(null, controller.finish())
    }

    @Test fun detectorsShareOneWindowAndFinishOnce() {
        val controller = MotionCaptureController()
        val first = controller.onDetector(Instant.EPOCH)
        val second = controller.onDetector(Instant.EPOCH.plusSeconds(1))
        val result = controller.finish()
        assertEquals(first, second)
        assertEquals(2, result?.detectorTimes?.size)
        assertTrue(controller.finish() == null)
    }
}
