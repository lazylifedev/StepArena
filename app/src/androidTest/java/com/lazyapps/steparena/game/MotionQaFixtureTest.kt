package com.lazyapps.steparena.game

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.lazyapps.steparena.qa.MotionQaFixture
import com.lazyapps.steparena.qa.MotionQaFixtures
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class MotionQaFixtureTest {
    private val analyzer = MotionEvidenceAnalyzer()

    @Test fun normalAndBriefFixturesAreNeverConfirmed() {
        val normal = MotionQaFixture.entries.filterNot {
            it.name.startsWith("SHAKE_") || it == MotionQaFixture.MIXED_WALK_SHAKE
        }
        normal.forEach { fixture ->
            assertNotEquals(fixture.name, MotionEvidenceAssessment.SHAKE_CONFIRMED, assess(fixture))
        }
    }

    @Test fun strongShakeFixturesAreConfirmed() {
        listOf(
            MotionQaFixture.SHAKE_HORIZONTAL,
            MotionQaFixture.SHAKE_VERTICAL,
            MotionQaFixture.SHAKE_ROTATIONAL,
        ).forEach { fixture -> assertEquals(fixture.name, MotionEvidenceAssessment.SHAKE_CONFIRMED, assess(fixture)) }
    }

    @Test fun missingAndSparseSensorsAreUnknown() {
        assertEquals(MotionEvidenceAssessment.UNKNOWN, assess(MotionQaFixture.SENSOR_MISSING))
        assertEquals(MotionEvidenceAssessment.UNKNOWN, assess(MotionQaFixture.SPARSE_DELIVERY))
    }

    private fun assess(fixture: MotionQaFixture): MotionEvidenceAssessment {
        val sequence = MotionQaFixtures.sequence(fixture)
        return analyzer.analyze(sequence.acceleration, sequence.gyroscope).assessment
    }
}
