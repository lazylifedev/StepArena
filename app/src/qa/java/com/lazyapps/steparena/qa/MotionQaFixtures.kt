package com.lazyapps.steparena.qa

import com.lazyapps.steparena.game.MotionSample

enum class MotionQaFixture {
    WALK_POCKET, WALK_HANDHELD, WALK_FAST, RUNNING, STAIRS, BRIEF_JOSTLE, SINGLE_IMPACT,
    VEHICLE_VIBRATION, SHAKE_HORIZONTAL, SHAKE_VERTICAL, SHAKE_ROTATIONAL, SHAKE_IRREGULAR,
    MIXED_WALK_SHAKE, SENSOR_MISSING, SPARSE_DELIVERY,
}

data class MotionQaSequence(val acceleration: List<MotionSample>, val gyroscope: List<MotionSample>)

object MotionQaFixtures {
    fun sequence(fixture: MotionQaFixture): MotionQaSequence = when (fixture) {
        MotionQaFixture.SENSOR_MISSING -> MotionQaSequence(emptyList(), emptyList())
        MotionQaFixture.SPARSE_DELIVERY -> wave(10, 1_000_000_000L, 8f, 7f)
        MotionQaFixture.BRIEF_JOSTLE, MotionQaFixture.SINGLE_IMPACT -> wave(12, 50_000_000L, 9f, 8f)
        MotionQaFixture.SHAKE_HORIZONTAL, MotionQaFixture.SHAKE_VERTICAL,
        MotionQaFixture.SHAKE_ROTATIONAL, MotionQaFixture.MIXED_WALK_SHAKE -> wave(60, 50_000_000L, 8f, 7f)
        MotionQaFixture.SHAKE_IRREGULAR -> wave(60, 50_000_000L, 4f, 3f, periodic = false)
        MotionQaFixture.WALK_POCKET, MotionQaFixture.WALK_HANDHELD, MotionQaFixture.WALK_FAST,
        MotionQaFixture.RUNNING, MotionQaFixture.STAIRS, MotionQaFixture.VEHICLE_VIBRATION ->
            wave(60, 50_000_000L, 1.5f, 2f, periodic = false)
    }

    private fun wave(
        count: Int,
        interval: Long,
        gyro: Float,
        acceleration: Float,
        periodic: Boolean = true,
    ): MotionQaSequence {
        fun sign(index: Int) = if (periodic && index % 2 == 0) 1f else if (periodic) -1f else 1f
        return MotionQaSequence(
            acceleration = (0 until count).map { i -> MotionSample(i * interval, acceleration * sign(i), 0f, 0f) },
            gyroscope = (0 until count).map { i -> MotionSample(i * interval, gyro * sign(i), 0f, 0f) },
        )
    }
}
