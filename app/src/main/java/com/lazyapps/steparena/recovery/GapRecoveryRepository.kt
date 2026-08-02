package com.lazyapps.steparena.recovery

import androidx.room.withTransaction
import com.lazyapps.steparena.core.database.StepArenaDatabase
import com.lazyapps.steparena.core.database.entity.ProcessedExternalStepRecordEntity
import com.lazyapps.steparena.core.database.entity.TrackingGapRecordEntity
import com.lazyapps.steparena.core.database.model.DataQuality
import java.time.Instant
import java.time.ZoneId
import java.util.UUID
import com.lazyapps.steparena.activity.ActivityRepository

class GapRecoveryRepository(
    private val database: StepArenaDatabase,
    private val external: ExternalActivityDataSource,
    private val ownPackageName: String,
    private val activityRepository: ActivityRepository,
    private val settingsRepository: RecoverySettingsRepository,
) {
    fun observeHistory() = database.trackingGaps().observeHistory()
    fun observeUnresolvedCount() = database.trackingGaps().observeUnresolvedCount()

    suspend fun detectHeartbeatGap(
        lastHeartbeat: Instant,
        now: Instant,
        zoneId: ZoneId,
        explicitUserStop: Boolean,
    ): TrackingGapRecordEntity? {
        if (explicitUserStop || !lastHeartbeat.isBefore(now)) return null
        val fingerprint = sha256("HEARTBEAT_STALE|$lastHeartbeat|$now|${zoneId.id}")
        database.trackingGaps().byFingerprint(fingerprint)?.let { return it }
        val timestamp = now.toEpochMilli()
        val record = TrackingGapRecordEntity(
            id = UUID.randomUUID().toString(),
            startedAtEpochMillis = lastHeartbeat.toEpochMilli(),
            endedAtEpochMillis = timestamp,
            zoneId = zoneId.id,
            reason = TrackingGapReason.HEARTBEAT_STALE,
            status = TrackingGapStatus.DETECTED,
            expectedTracking = true,
            explicitUserStop = false,
            recoveredSteps = 0,
            unresolvedSteps = 0,
            recoverySource = null,
            quality = DataQuality.UNKNOWN,
            externalRecordCount = 0,
            externalOriginsJson = null,
            fingerprint = fingerprint,
            detectedAtEpochMillis = timestamp,
            recoveredAtEpochMillis = null,
            reviewedAtEpochMillis = null,
            createdAtEpochMillis = timestamp,
            updatedAtEpochMillis = timestamp,
        )
        database.trackingGaps().upsert(record)
        return record
    }

    suspend fun recover(gapId: String, selfMeasuredSteps: Long = 0): TrackingGapRecordEntity? {
        val gap = database.trackingGaps().get(gapId) ?: return null
        if (gap.explicitUserStop || gap.status == TrackingGapStatus.RECOVERED) return gap
        if (!settingsRepository.current().healthConnectEnabled) {
            return gap.copy(
                status = TrackingGapStatus.UNRESOLVED,
                updatedAtEpochMillis = Instant.now().toEpochMilli(),
            ).also { database.trackingGaps().upsert(it) }
        }
        val start = Instant.ofEpochMilli(gap.startedAtEpochMillis)
        val end = Instant.ofEpochMilli(gap.endedAtEpochMillis)
        val result = external.readSteps(start, end)
        if (result.error != null) {
            val failed = gap.copy(
                status = if (result.error == ExternalReadError.PERMISSION_REQUIRED) {
                    TrackingGapStatus.RECOVERY_PENDING
                } else TrackingGapStatus.UNRESOLVED,
                updatedAtEpochMillis = Instant.now().toEpochMilli(),
            )
            database.trackingGaps().upsert(failed)
            return failed
        }
        val updated = database.withTransaction {
            val accepted = result.segments.mapNotNull { it.clippedTo(start, end) }.filter {
                it.validation() == SegmentValidation.VALID &&
                    classifyDataOrigin(it.dataOriginPackage, ownPackageName) !=
                    ExternalDataOriginType.STEP_ARENA
            }
            var appliedExternal = 0L
            val now = Instant.now().toEpochMilli()
            accepted.forEach { segment ->
                val fingerprint = sha256("${segment.fingerprint()}|$RECOVERY_POLICY_VERSION")
                val existing = segment.recordId?.let {
                    database.processedExternalSteps().byRecordId(it, segment.dataOriginPackage)
                } ?: database.processedExternalSteps().byFingerprint(fingerprint)
                val alreadyApplied = database.processedExternalSteps().appliedInRange(
                    segment.start.toEpochMilli(),
                    segment.end.toEpochMilli(),
                )
                val measuredInInterval = measuredStepsInInterval(
                    database.hourly().overlapping(
                        segment.start.toEpochMilli(),
                        segment.end.toEpochMilli(),
                    ),
                    segment.start,
                    segment.end,
                )
                if (existing == null) {
                    val candidate = recoverableSteps(
                        segment.steps,
                        measuredInInterval + selfMeasuredSteps,
                        alreadyApplied,
                    )
                    database.processedExternalSteps().upsert(
                        ProcessedExternalStepRecordEntity(
                            id = UUID.randomUUID().toString(),
                            recordId = segment.recordId,
                            dataOriginPackage = segment.dataOriginPackage,
                            startedAtEpochMillis = segment.start.toEpochMilli(),
                            endedAtEpochMillis = segment.end.toEpochMilli(),
                            steps = segment.steps,
                            lastModifiedAtEpochMillis = segment.lastModifiedAt?.toEpochMilli(),
                            fingerprint = fingerprint,
                            processedAtEpochMillis = now,
                            appliedSteps = candidate,
                            gapId = gap.id,
                        ),
                    )
                    appliedExternal += candidate
                } else if (existing.fingerprint != fingerprint) {
                    val otherApplied = (alreadyApplied - existing.appliedSteps).coerceAtLeast(0)
                    val recalculated = recoverableSteps(
                        segment.steps,
                        measuredInInterval + selfMeasuredSteps,
                        otherApplied,
                    ).coerceAtLeast(existing.appliedSteps)
                    database.processedExternalSteps().upsert(
                        existing.copy(
                            steps = segment.steps,
                            startedAtEpochMillis = segment.start.toEpochMilli(),
                            endedAtEpochMillis = segment.end.toEpochMilli(),
                            lastModifiedAtEpochMillis = segment.lastModifiedAt?.toEpochMilli(),
                            fingerprint = fingerprint,
                            processedAtEpochMillis = now,
                            appliedSteps = recalculated,
                        ),
                    )
                    appliedExternal += recalculated - existing.appliedSteps
                }
            }
            val quality = if (accepted.all {
                    it.start.toEpochMilli() >= gap.startedAtEpochMillis &&
                        it.end.toEpochMilli() <= gap.endedAtEpochMillis
                }
            ) DataQuality.RECOVERED else DataQuality.MIXED
            val updated = gap.copy(
                status = if (appliedExternal > 0 || accepted.all { it.steps == 0L }) {
                    TrackingGapStatus.RECOVERED
                } else TrackingGapStatus.UNRESOLVED,
                recoveredSteps = gap.recoveredSteps + appliedExternal,
                recoverySource = if (accepted.isEmpty()) null else RecoverySource.HEALTH_CONNECT,
                quality = if (accepted.isEmpty()) DataQuality.UNKNOWN else quality,
                externalRecordCount = accepted.size,
                externalOriginsJson = accepted.map { it.dataOriginPackage }.distinct()
                    .joinToString(prefix = "[\"", postfix = "\"]", separator = "\",\""),
                recoveredAtEpochMillis = if (accepted.isEmpty()) null else now,
                updatedAtEpochMillis = now,
            )
            database.trackingGaps().upsert(updated)
            updated
        }
        if (updated.recoveredSteps > gap.recoveredSteps) {
            activityRepository.recordExternalRecoveredSteps(
                updated.recoveredSteps - gap.recoveredSteps,
                end,
                runCatching { ZoneId.of(gap.zoneId) }.getOrDefault(ZoneId.systemDefault()),
            )
        }
        return updated
    }

    private companion object {
        const val RECOVERY_POLICY_VERSION = "interval-difference-v2"
    }
}

internal fun recoverableSteps(
    externalSteps: Long,
    measuredSteps: Long,
    alreadyRecoveredSteps: Long,
): Long = (externalSteps - measuredSteps - alreadyRecoveredSteps).coerceAtLeast(0)

internal fun measuredStepsInInterval(
    hours: List<com.lazyapps.steparena.core.database.entity.HourlyActivityRecordEntity>,
    start: Instant,
    end: Instant,
): Long = hours.sumOf { hour ->
    val hourStart = Instant.ofEpochMilli(hour.periodStartEpochMillis)
    val hourEnd = Instant.ofEpochMilli(hour.periodEndEpochMillis)
    val overlapStart = maxOf(start, hourStart)
    val overlapEnd = minOf(end, hourEnd)
    if (!overlapStart.isBefore(overlapEnd)) return@sumOf 0L
    val fullMillis = java.time.Duration.between(hourStart, hourEnd).toMillis()
    val overlapMillis = java.time.Duration.between(overlapStart, overlapEnd).toMillis()
    if (fullMillis <= 0) 0L else hour.steps * overlapMillis / fullMillis
}
