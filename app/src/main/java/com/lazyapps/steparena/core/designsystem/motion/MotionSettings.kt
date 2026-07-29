package com.lazyapps.steparena.core.designsystem.motion

enum class MotionLevel { FULL, REDUCED, OFF }

interface MotionSettingsRepository {
    suspend fun read(): MotionLevel
    suspend fun save(level: MotionLevel)
}

/**
 * Phase 0/1 in-memory implementation. The interface is deliberately compatible with
 * a future DataStore-backed implementation without pretending persistence exists today.
 */
class InMemoryMotionSettingsRepository(
    private var level: MotionLevel = MotionLevel.FULL,
) : MotionSettingsRepository {
    override suspend fun read(): MotionLevel = level
    override suspend fun save(level: MotionLevel) {
        this.level = level
    }
}
