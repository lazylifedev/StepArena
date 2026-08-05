package com.lazyapps.steparena.activity

import androidx.room.withTransaction
import com.lazyapps.steparena.core.database.StepArenaDatabase
import com.lazyapps.steparena.core.database.entity.ActivityProcessingStateEntity
import com.lazyapps.steparena.core.database.entity.DailyActivityRecordEntity
import com.lazyapps.steparena.core.database.entity.HourlyActivityRecordEntity
import com.lazyapps.steparena.core.database.entity.WalkingSessionEntity
import com.lazyapps.steparena.core.database.entity.CompetitiveIntegritySegmentEntity
import com.lazyapps.steparena.core.database.model.DataQuality
import com.lazyapps.steparena.core.database.model.WalkingSessionStatus
import com.lazyapps.steparena.core.database.model.WalkingSessionType
import com.lazyapps.steparena.core.database.model.mergeQuality
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.temporal.ChronoUnit
import java.util.UUID
import com.lazyapps.steparena.game.CompetitiveIntegrityClassifier
import com.lazyapps.steparena.game.CompetitiveIntegrityInput
import com.lazyapps.steparena.game.DetectorEvidence
import com.lazyapps.steparena.game.MotionEvidenceAssessment
import com.lazyapps.steparena.game.allocateCompetitiveDays
import com.lazyapps.steparena.game.CompetitiveIntegrityAssessment
import com.lazyapps.steparena.game.CompetitiveIntegrityReason
import com.lazyapps.steparena.game.CompetitiveAllocation
import com.lazyapps.steparena.game.ResolvedMotionAllocation
import com.lazyapps.steparena.game.finalizePendingMotionClassification

data class PersistedActivityUpdate(
    val localDate: LocalDate,
    val zoneId: ZoneId,
    val officialDailySteps: Long,
)

private data class PendingMotionAllocation(
    val windowId: String,
    val segmentId: String,
    val assignedSteps: Long,
    val createdAtEpochMillis: Long,
)

private data class PendingSegmentClassification(
    val base: CompetitiveAllocation,
    val windows: MutableMap<String, ResolvedMotionAllocation> = linkedMapOf(),
)

data class MotionEvidenceApplyResult(val queuedEventsUpdated: Int, val pendingAllocationsResolved: Int, val integritySegmentsUpdated: Int)

data class MotionRepositorySnapshot(
    val detectorEvents: Int,
    val pendingAllocations: Int,
    val pendingSegments: Int,
)

data class OfficialProgressStorageSnapshot(
    val totalSteps: Long,
    val eligibleSteps: Long,
    val restrictedSteps: Long,
    val excludedSteps: Long,
    val integrityVersion: Int,
    val sourceRevision: String,
)

class ActivityRepository(
    private val database: StepArenaDatabase,
    private val profileRepository: UserProfileRepository,
    private val stepLengthEstimator: StepLengthEstimator = DefaultStepLengthEstimator(),
    private val calorieEstimator: CalorieEstimator = DistanceCalorieEstimator(),
    private val policy: WalkingDetectionPolicy = WalkingDetectionPolicy(),
    private val durationCalculator: WalkingDurationCalculator =
        WalkingDurationCalculator(policy.activeGapThresholdSeconds),
    private val integrityClassifier: CompetitiveIntegrityClassifier = CompetitiveIntegrityClassifier(),
) {
    suspend fun officialProgressSnapshot(date: LocalDate, zoneId: ZoneId): com.lazyapps.steparena.official.OfficialProgressSnapshot {
        val daily = database.daily().get(date.toString(), zoneId.id)
        val segments = database.competitiveIntegritySegments().forDate(date.toString(), zoneId.id)
        val total = daily?.steps ?: segments.sumOf { it.totalSteps }
        val eligible = segments.sumOf { it.eligibleSteps }.coerceAtMost(total)
        val restricted = segments.sumOf { it.restrictedSteps }.coerceAtMost(total - eligible)
        val excluded = (total - eligible - restricted).coerceAtLeast(0)
        val revision = maxOf(daily?.updatedAtEpochMillis ?: 0L, segments.maxOfOrNull { it.createdAtEpochMillis } ?: 0L)
        return com.lazyapps.steparena.official.OfficialProgressSnapshot(
            date, zoneId.id, total, eligible, restricted, excluded,
            segments.maxOfOrNull { it.classifierVersion } ?: 0, revision.toString(),
        )
    }
    private val writer = Mutex()
    private val detectorEvents = ArrayDeque<DetectorEvidence>()
    private val pendingMotionAllocations = ArrayDeque<PendingMotionAllocation>()
    private val pendingSegmentClassifications = mutableMapOf<String, PendingSegmentClassification>()
    private var learnedCadenceStepsPerMinute: Double? = null

    fun observeToday(date: LocalDate, zoneId: ZoneId) =
        database.daily().observeDate(date.toString(), zoneId.id)
    fun observeHours(date: LocalDate, zoneId: ZoneId) =
        database.hourly().observeDate(date.toString(), zoneId.id)
    suspend fun officialDailySteps(date: LocalDate, zoneId: ZoneId): Long =
        database.daily().get(date.toString(), zoneId.id)?.steps ?: 0
    fun observeSessions() = database.sessions().observeAll()
    fun observeActiveManualSession() = database.sessions().observeActiveManual()
    suspend fun currentManualSession() = database.sessions().active(true)

    suspend fun startManualSession(
        at: Instant,
        zoneId: ZoneId,
        trackingServiceSessionId: String,
    ): WalkingSessionEntity = writer.withLock {
        database.withTransaction {
            database.sessions().active(true)?.let { return@withTransaction it }
            database.sessions().active(false)?.let { finishSessionLocked(it, at) }
            val profile = profileRepository.current()
            val id = UUID.randomUUID().toString()
            val session = newManualSession(id, at, zoneId, trackingServiceSessionId, profile)
            database.sessions().upsert(session)
            updateProcessingSessionIds(at)
            session
        }
    }

    suspend fun endManualSession(id: String, at: Instant): Boolean = writer.withLock {
        database.withTransaction {
            val session = database.sessions().get(id)
            if (session == null || !session.isManual ||
                session.status !in setOf(WalkingSessionStatus.ACTIVE, WalkingSessionStatus.PAUSED)
            ) return@withTransaction false
            finishSessionLocked(session, at, forceCompleted = true)
            updateProcessingSessionIds(at)
            true
        }
    }

    suspend fun finishAllActiveSessions(at: Instant) = writer.withLock {
        database.withTransaction {
            database.sessions().activeSessions().forEach { finishSessionLocked(it, at) }
            updateProcessingSessionIds(at)
        }
    }

    suspend fun recordDetector(at: Instant) = recordDetector(DetectorEvidence(at))

    suspend fun recordDetector(evidence: DetectorEvidence) = writer.withLock {
        detectorEvents.addLast(evidence)
        while (detectorEvents.firstOrNull()?.at?.isBefore(evidence.at.minusSeconds(3_600)) == true) {
            detectorEvents.removeFirst()
        }
    }

    suspend fun applyMotionEvidence(windowId: String, assessment: MotionEvidenceAssessment, confidence: Double): MotionEvidenceApplyResult =
        writer.withLock {
            var resolved = 0
            var segmentsUpdated = 0
            val stateSnapshot = pendingSegmentClassifications.mapValues { (_, state) ->
                PendingSegmentClassification(state.base, LinkedHashMap(state.windows))
            }
            try {
                database.withTransaction {
                val pending = pendingMotionAllocations.filter { it.windowId == windowId }
                pending.forEach { allocation ->
                    val segment = database.competitiveIntegritySegments().byId(allocation.segmentId)
                        ?: return@forEach
                    val state = pendingSegmentClassifications.getOrPut(segment.id) {
                        PendingSegmentClassification(CompetitiveAllocation(segment.eligibleSteps, segment.restrictedSteps,
                            segment.excludedSteps, segment.assessment, parseReasons(segment.reasons), segment.classifierVersion))
                    }
                    state.windows.putIfAbsent(windowId, ResolvedMotionAllocation(windowId, allocation.assignedSteps, assessment))
                    val result = finalizePendingMotionClassification(segment.totalSteps, state.base, state.windows.values.toList())
                    check(database.competitiveIntegritySegments().updateClassification(segment.id, result.eligibleSteps,
                        result.restrictedSteps, result.excludedSteps, result.assessment,
                        result.reasons.joinToString(",") { it.name }, result.classifierVersion) == 1)
                    check(database.competitiveIntegritySegments().byId(segment.id)?.totalSteps == segment.totalSteps)
                    resolved++
                    segmentsUpdated++
                }
                }
            } catch (failure: Throwable) {
                pendingSegmentClassifications.clear()
                pendingSegmentClassifications.putAll(stateSnapshot)
                throw failure
            }
            pendingMotionAllocations.removeAll { it.windowId == windowId }
            val updated = detectorEvents.map { event ->
                if (event.evidenceWindowId == windowId) event.copy(assessment = assessment, confidence = confidence)
                else event
            }
            detectorEvents.clear()
            detectorEvents.addAll(updated)
            MotionEvidenceApplyResult(updated.count { it.evidenceWindowId == windowId }, resolved, segmentsUpdated)
        }

    suspend fun clearPendingMotionClassifications() = writer.withLock {
        pendingMotionAllocations.clear()
        pendingSegmentClassifications.clear()
    }

    suspend fun clearAllMotionEvidence() = writer.withLock {
        detectorEvents.clear()
        pendingMotionAllocations.clear()
        pendingSegmentClassifications.clear()
    }

    suspend fun motionRepositorySnapshot() = writer.withLock {
        MotionRepositorySnapshot(detectorEvents.size, pendingMotionAllocations.size, pendingSegmentClassifications.size)
    }

    private fun parseReasons(value: String): Set<CompetitiveIntegrityReason> = value.split(',').mapNotNull {
        runCatching { CompetitiveIntegrityReason.valueOf(it.trim()) }.getOrNull()
    }.toSet()

    suspend fun recordExternalRecoveredSteps(
        steps: Long,
        at: Instant,
        zoneId: ZoneId,
    ) = writer.withLock {
        if (steps <= 0) return@withLock
        val profile = profileRepository.current()
        database.withTransaction {
            rebuildDay(
                at.atZone(zoneId).toLocalDate(),
                zoneId,
                profile,
                at,
                addedExternalRecoveredSteps = steps,
            )
        }
    }

    suspend fun recordCounterDelta(
        sensorValue: Long,
        delta: Long,
        at: Instant,
        zoneId: ZoneId,
        bootSessionId: String,
        trackingServiceSessionId: String?,
        recovered: Boolean,
        detectorAvailable: Boolean = false,
        motionSensorAvailable: Boolean = false,
    ): PersistedActivityUpdate? = writer.withLock {
        if (delta <= 0) return@withLock null
        val localDate = at.atZone(zoneId).toLocalDate()
        val profile = profileRepository.current()
        val persisted = database.withTransaction {
            val processing = database.processingState().get()
            if (processing != null && processing.lastBootSessionId != bootSessionId) {
                detectorEvents.clear()
                pendingMotionAllocations.clear()
                pendingSegmentClassifications.clear()
            } else if (processing != null && (processing.lastZoneId != zoneId.id ||
                    processing.lastEventEpochMillis?.let { Instant.ofEpochMilli(it).atZone(zoneId).toLocalDate() } != localDate)) {
                pendingMotionAllocations.clear()
                pendingSegmentClassifications.clear()
            }
            if ((processing?.activityRepairVersion ?: 0) < ACTIVITY_REPAIR_VERSION) {
                repairImplausibleActivity(profile, at)
            }
            if (
                processing?.lastCounterValue == sensorValue &&
                processing.lastBootSessionId == bootSessionId
            ) return@withTransaction false

            val previousAt = processing?.lastEventEpochMillis?.let(Instant::ofEpochMilli)
            val longGap = previousAt != null && Duration.between(previousAt, at).toHours() >= 2
            while (previousAt != null && detectorEvents.firstOrNull()?.at?.isBefore(previousAt) == true) {
                detectorEvents.removeFirst()
            }
            val consumedDetectors = detectorEvents
                .takeWhile { !it.at.isAfter(at) }
                .take(delta.coerceAtMost(Int.MAX_VALUE.toLong()).toInt())
            val consumedTimes = consumedDetectors.map { it.at }
            val allocations = allocate(delta, previousAt, at, zoneId, longGap, consumedTimes)
            val durationResult = durationCalculator.calculate(
                delta, consumedTimes, previousAt, at, recovered || longGap,
                learnedCadenceStepsPerMinute,
            )
            val integrity = integrityClassifier.classify(
                CompetitiveIntegrityInput(
                    steps = delta,
                    startedAt = previousAt,
                    endedAt = at,
                    detectorEvents = consumedDetectors.size,
                    detectorAvailable = detectorAvailable,
                    bootSessionChanged = processing?.lastBootSessionId != null &&
                        processing.lastBootSessionId != bootSessionId,
                    recoveredOrLongGap = recovered || longGap,
                    recentCadences = listOfNotNull(learnedCadenceStepsPerMinute),
                    shakeSuspectedDetectorEvents = consumedDetectors.count {
                        it.assessment == MotionEvidenceAssessment.SHAKE_SUSPECTED
                    },
                    shakeConfirmedDetectorEvents = consumedDetectors.count {
                        it.assessment == MotionEvidenceAssessment.SHAKE_CONFIRMED
                    },
                    motionEvaluatedDetectorEvents = consumedDetectors.count {
                        it.assessment != MotionEvidenceAssessment.UNKNOWN
                    },
                    motionSensorAvailable = motionSensorAvailable || consumedDetectors.any {
                        it.assessment != MotionEvidenceAssessment.UNKNOWN
                    },
                ),
            )
            val segmentAllocations = if (allocations.isEmpty()) {
                // A long gap cannot be placed on a clock timeline. Keep it visible as
                // unallocated instead of pretending that it belongs to eventAt's day.
                mapOf(HourBucket.of(at, zoneId) to delta)
            } else allocations
            val reasonText = integrity.reasons.joinToString(",") { it.name }
            val grouped = segmentAllocations.entries.groupBy { it.key.date to it.key.zone }
            val groupedEntries = grouped.entries.toList()
            val groupWeights = groupedEntries.map { it.value.sumOf { entry -> entry.value } }
            val dayAllocations = allocateCompetitiveDays(
                groupWeights, integrity.eligibleSteps, integrity.restrictedSteps, integrity.excludedSteps,
            )
            groupedEntries.forEachIndexed { groupIndex, (dayAndZone, entries) ->
                val daySteps = entries.sumOf { it.value }
                val total = daySteps
                val allocation = dayAllocations[groupIndex]
                val eligible = allocation.eligible
                val restricted = allocation.restricted
                val excluded = allocation.excluded
                val segmentId = "integrity-$bootSessionId-${at.toEpochMilli()}-${sensorValue}-${dayAndZone.first}-${dayAndZone.second}-$groupIndex"
                database.competitiveIntegritySegments().upsert(
                    CompetitiveIntegritySegmentEntity(
                        id = segmentId,
                        localDate = dayAndZone.first.toString(), zoneId = dayAndZone.second.id,
                        startedAtEpochMillis = entries.minOf { it.key.start.toEpochMilli() },
                        endedAtEpochMillis = entries.maxOf { it.key.end.toEpochMilli() }.coerceAtMost(at.toEpochMilli()),
                        totalSteps = total, eligibleSteps = eligible,
                        restrictedSteps = restricted, excludedSteps = excluded,
                        assessment = integrity.assessment, reasons = reasonText,
                        classifierVersion = integrity.classifierVersion, createdAtEpochMillis = at.toEpochMilli(),
                    ),
                )
                consumedDetectors.filter { event ->
                    val bucket = HourBucket.of(event.at, dayAndZone.second)
                    bucket.date == dayAndZone.first && bucket.zone == dayAndZone.second &&
                        event.evidenceWindowId != null &&
                        event.assessment == MotionEvidenceAssessment.UNKNOWN
                }.groupBy { it.evidenceWindowId }
                    .forEach { (windowId, events) ->
                        pendingMotionAllocations.addLast(
                            PendingMotionAllocation(windowId!!, segmentId, events.size.toLong().coerceAtMost(total), at.toEpochMilli()),
                        )
                    }
                trimPending(at.toEpochMilli())
            }
            if (durationResult.quality == DataQuality.MEASURED) {
                durationResult.cadenceStepsPerMinute
                    ?.takeIf { it in WalkingDurationCalculator.MIN_CADENCE..WalkingDurationCalculator.MAX_CADENCE }
                    ?.let { cadence ->
                        learnedCadenceStepsPerMinute = learnedCadenceStepsPerMinute
                            ?.let { previous -> previous * 0.75 + cadence * 0.25 } ?: cadence
                    }
            }
            val durationQuality = durationResult.quality
            val durationAllocations = allocateByStepRatio(durationResult.totalSeconds, allocations)
            allocations.forEach { (bucket, steps) ->
                upsertHour(
                    bucket, steps, at, profile, durationQuality,
                    durationAllocations[bucket] ?: 0,
                    consumedTimes.count { HourBucket.of(it, zoneId) == bucket },
                )
            }
            splitManualSessionAtDateBoundary(at, zoneId, profile, trackingServiceSessionId)
            val manual = database.sessions().active(true)
            if (manual != null) {
                updateManualSession(
                    manual, delta, at, profile, durationQuality, durationResult.totalSeconds, consumedDetectors.size,
                )
            } else {
                updateAutoSession(
                    delta, at, zoneId, profile, durationQuality, trackingServiceSessionId,
                    durationResult.totalSeconds, consumedDetectors.size,
                )
            }
            val affectedDays = allocations.keys.map { it.date to it.zone }.toSet()
            if (longGap) {
                rebuildDay(
                    at.atZone(zoneId).toLocalDate(),
                    zoneId,
                    profile,
                    at,
                    addedUnallocatedMeasuredSteps = delta,
                )
            } else {
                affectedDays.forEach { (date, zone) ->
                    rebuildDay(date, zone, profile, at)
                }
            }
            database.processingState().upsert(
                ActivityProcessingStateEntity(
                    lastCounterValue = sensorValue,
                    lastEventEpochMillis = at.toEpochMilli(),
                    lastZoneId = zoneId.id,
                    lastBootSessionId = bootSessionId,
                    activeAutoSessionId = database.sessions().active(false)?.id,
                    activeManualSessionId = database.sessions().active(true)?.id,
                    lastDetectorEventEpochMillis = consumedDetectors.lastOrNull()?.at?.toEpochMilli()
                        ?: processing?.lastDetectorEventEpochMillis,
                    lastWalkingEventEpochMillis = at.toEpochMilli(),
                    updatedAtEpochMillis = at.toEpochMilli(),
                    activityRepairVersion = ACTIVITY_REPAIR_VERSION,
                ),
            )
            repeat(consumedDetectors.size) { detectorEvents.removeFirstOrNull() }
            true
        }
        if (!persisted) return@withLock null
        PersistedActivityUpdate(localDate, zoneId, officialDailySteps(localDate, zoneId))
    }

    private fun trimPending(now: Long) {
        val cutoff = now - 3_600_000L
        while (pendingMotionAllocations.firstOrNull()?.createdAtEpochMillis?.let { it < cutoff } == true ||
            pendingMotionAllocations.size > 1_024) pendingMotionAllocations.removeFirstOrNull()
        while (pendingMotionAllocations.map { it.windowId }.distinct().size > 256) {
            val oldestWindow = pendingMotionAllocations.firstOrNull()?.windowId ?: break
            pendingMotionAllocations.removeAll { it.windowId == oldestWindow }
        }
        val liveSegments = pendingMotionAllocations.map { it.segmentId }.toSet()
        pendingSegmentClassifications.keys.retainAll(liveSegments)
    }

    suspend fun finishSession(id: String?, at: Instant) = writer.withLock {
        if (id == null) return@withLock
        database.withTransaction {
            val session = database.sessions().get(id) ?: return@withTransaction
            val completed = session.steps >= policy.minimumSessionSteps ||
                session.activeDurationSeconds >= policy.minimumSessionDurationSeconds
            database.sessions().upsert(
                session.copy(
                    endedAtEpochMillis = at.toEpochMilli(),
                    elapsedDurationSeconds = Duration.between(
                        Instant.ofEpochMilli(session.startedAtEpochMillis), at,
                    ).seconds.coerceAtLeast(session.activeDurationSeconds),
                    pausedDurationSeconds = (
                        Duration.between(Instant.ofEpochMilli(session.startedAtEpochMillis), at)
                            .seconds.coerceAtLeast(session.activeDurationSeconds) -
                            session.activeDurationSeconds
                        ).coerceAtLeast(0),
                    status = if (completed) {
                        WalkingSessionStatus.COMPLETED
                    } else {
                        WalkingSessionStatus.DISCARDED
                    },
                    updatedAtEpochMillis = at.toEpochMilli(),
                ),
            )
        }
    }

    private fun allocate(
        delta: Long,
        previousAt: Instant?,
        at: Instant,
        zone: ZoneId,
        longGap: Boolean,
        matchingDetector: List<Instant>,
    ): Map<HourBucket, Long> {
        if (longGap) return emptyMap()
        if (previousAt == null || !previousAt.isBefore(at)) {
            return mapOf(HourBucket.of(at, zone) to delta)
        }
        if (matchingDetector.isNotEmpty()) {
            val result = matchingDetector.take(delta.toInt()).groupingBy { HourBucket.of(it, zone) }
                .eachCount().mapValues { it.value.toLong() }.toMutableMap()
            val remainder = delta - result.values.sum()
            if (remainder > 0) result[HourBucket.of(at, zone)] =
                (result[HourBucket.of(at, zone)] ?: 0) + remainder
            return result
        }
        val start = previousAt.atZone(zone)
        val end = at.atZone(zone)
        if (HourBucket.of(previousAt, zone) == HourBucket.of(at, zone)) {
            return mapOf(HourBucket.of(at, zone) to delta)
        }
        val buckets = mutableListOf<Pair<HourBucket, Long>>()
        var cursor = start
        while (cursor.isBefore(end)) {
            val boundary = cursor.truncatedTo(ChronoUnit.HOURS).plusHours(1)
            val segmentEnd = if (boundary.isBefore(end)) boundary else end
            buckets += HourBucket.of(cursor.toInstant(), zone) to
                Duration.between(cursor, segmentEnd).toMillis().coerceAtLeast(1)
            cursor = segmentEnd
        }
        var remaining = delta
        val totalMillis = buckets.sumOf { it.second }
        val result = linkedMapOf<HourBucket, Long>()
        buckets.forEachIndexed { index, (bucket, millis) ->
            val share = if (index == buckets.lastIndex) remaining else delta * millis / totalMillis
            result[bucket] = (result[bucket] ?: 0) + share
            remaining -= share
        }
        return result
    }

    private suspend fun upsertHour(
        bucket: HourBucket,
        addedSteps: Long,
        eventAt: Instant,
        profile: UserBodyProfile,
        durationQuality: DataQuality,
        addedDurationSeconds: Long,
        detectorEventCount: Int,
    ) {
        val old = database.hourly().byId(bucket.id)
        val steps = (old?.steps ?: 0) + addedSteps
        val stepLength = stepLengthEstimator.estimate(profile)
        val distance = steps * stepLength.meters
        val first = old?.firstActivityAtEpochMillis?.let(Instant::ofEpochMilli) ?: eventAt
        val last = eventAt
        val duration = ((old?.walkingDurationSeconds ?: 0) + addedDurationSeconds)
            .coerceIn(0, 3_600)
        val speed = WalkingSpeedCalculator.movingKmh(distance, duration)
        val calories = calorieEstimator.estimate(profile.weightKg, distance, duration, speed)
        val now = eventAt.toEpochMilli()
        database.hourly().upsert(
            HourlyActivityRecordEntity(
                id = bucket.id,
                localDate = bucket.date.toString(),
                hourOfDay = bucket.hour,
                zoneId = bucket.zone.id,
                utcOffsetSeconds = bucket.offsetSeconds,
                periodStartEpochMillis = bucket.start.toEpochMilli(),
                periodEndEpochMillis = bucket.end.toEpochMilli(),
                steps = steps,
                distanceMeters = distance,
                walkingDurationSeconds = duration,
                estimatedCaloriesKcal = calories?.kcal,
                averageWalkingSpeedKmh = speed,
                // Step Counter deltas are measured steps. Duration estimation must not
                // downgrade or otherwise alter the independent step-quality dimension.
                stepsQuality = mergeQuality(listOfNotNull(old?.stepsQuality, DataQuality.MEASURED)),
                distanceQuality = DataQuality.ESTIMATED,
                durationQuality = if (duration == 0L) DataQuality.UNKNOWN else durationQuality,
                caloriesQuality = calories?.quality ?: DataQuality.UNKNOWN,
                speedQuality = if (speed == null) DataQuality.UNKNOWN else DataQuality.ESTIMATED,
                firstActivityAtEpochMillis = first.toEpochMilli(),
                lastActivityAtEpochMillis = last.toEpochMilli(),
                sensorEventCount = (old?.sensorEventCount ?: 0) + detectorEventCount,
                recoveredSteps = old?.recoveredSteps ?: 0,
                estimatedSteps = old?.estimatedSteps ?: 0,
                appliedStepLengthMeters = stepLength.meters,
                appliedWeightKg = profile.weightKg ?: DistanceCalorieEstimator.DEFAULT_WEIGHT_KG,
                calorieFormulaVersion = 1,
                createdAtEpochMillis = old?.createdAtEpochMillis ?: now,
                updatedAtEpochMillis = now,
            ),
        )
    }

    private fun allocateByStepRatio(
        total: Long,
        steps: Map<HourBucket, Long>,
    ): Map<HourBucket, Long> {
        if (steps.isEmpty()) return emptyMap()
        val totalSteps = steps.values.sum().coerceAtLeast(1)
        var remaining = total
        val result = linkedMapOf<HourBucket, Long>()
        steps.entries.forEachIndexed { index, (bucket, bucketSteps) ->
            val share = if (index == steps.size - 1) remaining else total * bucketSteps / totalSteps
            result[bucket] = share
            remaining -= share
        }
        return result
    }

    private suspend fun repairImplausibleActivity(profile: UserBodyProfile, at: Instant) {
        val affected = linkedSetOf<Pair<LocalDate, ZoneId>>()
        database.hourly().all().forEach { hour ->
            if (hour.steps < WalkingDurationCalculator.MIN_STEPS_FOR_CADENCE_CHECK) return@forEach
            val oldDuration = hour.walkingDurationSeconds ?: 0
            val cadence = if (oldDuration > 0) hour.steps * 60.0 / oldDuration else Double.POSITIVE_INFINITY
            if (oldDuration > 0 && cadence in
                WalkingDurationCalculator.MIN_CADENCE..WalkingDurationCalculator.MAX_CADENCE
            ) return@forEach
            val duration = durationCalculator.estimateSeconds(hour.steps)
            val distance = hour.steps * hour.appliedStepLengthMeters
            val speed = WalkingSpeedCalculator.movingKmh(distance, duration)
            val calories = calorieEstimator.estimate(profile.weightKg, distance, duration, speed)
            database.hourly().upsert(
                hour.copy(
                    distanceMeters = distance,
                    walkingDurationSeconds = duration,
                    estimatedCaloriesKcal = calories?.kcal,
                    averageWalkingSpeedKmh = speed,
                    durationQuality = DataQuality.ESTIMATED,
                    caloriesQuality = calories?.quality ?: DataQuality.UNKNOWN,
                    speedQuality = if (speed == null) DataQuality.UNKNOWN else DataQuality.ESTIMATED,
                    updatedAtEpochMillis = at.toEpochMilli(),
                ),
            )
            affected += LocalDate.parse(hour.localDate) to ZoneId.of(hour.zoneId)
        }
        affected.forEach { (date, zone) -> rebuildDay(date, zone, profile, at) }
    }

    private companion object {
        const val ACTIVITY_REPAIR_VERSION = 1
    }

    private suspend fun rebuildDay(
        date: LocalDate,
        zone: ZoneId,
        profile: UserBodyProfile,
        at: Instant,
        addedExternalRecoveredSteps: Long = 0,
        addedUnallocatedMeasuredSteps: Long = 0,
    ) {
        val hours = database.hourly().forDate(date.toString(), zone.id)
        val old = database.daily().get(date.toString(), zone.id)
        val externalRecovered = (old?.externalRecoveredSteps ?: old?.unclassifiedSteps ?: 0) +
            addedExternalRecoveredSteps
        val unallocatedMeasured = (old?.unallocatedMeasuredSteps ?: 0) +
            addedUnallocatedMeasuredSteps
        // Daily steps are the sensor-derived source of truth. Recovery remains separate so
        // it can be explained and can never silently inflate measured activity.
        val steps = hours.sumOf { it.steps } + unallocatedMeasured
        val hourlyDistance = hours.mapNotNull { it.distanceMeters }.sum()
        val hourlyDuration = hours.mapNotNull { it.walkingDurationSeconds }.sum()
        val estimatedUnallocatedDistance = if (unallocatedMeasured > 0) {
            unallocatedMeasured * stepLengthEstimator.estimate(profile).meters
        } else 0.0
        val estimatedUnallocatedDuration = if (unallocatedMeasured > 0) {
            durationCalculator.estimateSeconds(
                unallocatedMeasured,
                learnedCadenceStepsPerMinute ?: WalkingDurationCalculator.DEFAULT_CADENCE,
            )
        } else 0L
        val distance = (hourlyDistance + estimatedUnallocatedDistance)
            .takeIf { steps > 0 }
        val duration = (hourlyDuration + estimatedUnallocatedDuration)
            .takeIf { steps > 0 }
        val calories = calorieEstimator.estimate(profile.weightKg, distance, duration, null)?.kcal
        val speed = WalkingSpeedCalculator.movingKmh(distance, duration)
        val now = at.toEpochMilli()
        database.daily().upsert(
            DailyActivityRecordEntity(
                id = "${date}|${zone.id}",
                localDate = date.toString(),
                zoneId = zone.id,
                steps = steps,
                // Retained as a schema-compatibility mirror through v9. New code reads the
                // explicit fields below and never mixes Counter gaps with external recovery.
                unclassifiedSteps = externalRecovered,
                unclassifiedStepsQuality = if (externalRecovered > 0) {
                    DataQuality.RECOVERED
                } else DataQuality.UNKNOWN,
                externalRecoveredSteps = externalRecovered,
                unallocatedMeasuredSteps = unallocatedMeasured,
                distanceMeters = distance,
                walkingDurationSeconds = duration,
                estimatedCaloriesKcal = calories,
                averageWalkingSpeedKmh = speed,
                stepsQuality = mergeQuality(
                    hours.map { it.stepsQuality } +
                        listOf(DataQuality.MEASURED).takeIf { unallocatedMeasured > 0 }.orEmpty(),
                ),
                distanceQuality = if (distance == null) DataQuality.UNKNOWN else DataQuality.ESTIMATED,
                durationQuality = mergeQuality(
                    hours.map { it.durationQuality } +
                        listOf(DataQuality.ESTIMATED).takeIf { unallocatedMeasured > 0 }.orEmpty(),
                ),
                caloriesQuality = if (calories == null) DataQuality.UNKNOWN else DataQuality.ESTIMATED,
                speedQuality = if (speed == null) DataQuality.UNKNOWN else DataQuality.ESTIMATED,
                activeHourCount = hours.count { it.steps > 0 },
                walkingSessionCount = database.sessions().countForDate(date.toString()),
                finalized = date.isBefore(at.atZone(zone).toLocalDate()),
                finalizedAtEpochMillis = if (date.isBefore(at.atZone(zone).toLocalDate())) now else null,
                createdAtEpochMillis = old?.createdAtEpochMillis ?: now,
                updatedAtEpochMillis = now,
            ),
        )
    }

    private suspend fun updateAutoSession(
        delta: Long,
        at: Instant,
        zone: ZoneId,
        profile: UserBodyProfile,
        durationQuality: DataQuality,
        trackingServiceSessionId: String?,
        addedDurationSeconds: Long,
        detectorCount: Int,
    ) {
        val old = database.sessions().active(false)
        val id = old?.id ?: UUID.randomUUID().toString()
        val steps = (old?.steps ?: 0) + delta
        val started = old?.startedAtEpochMillis ?: at.toEpochMilli()
        val elapsed = Duration.between(Instant.ofEpochMilli(started), at).seconds.coerceAtLeast(0)
        val active = ((old?.activeDurationSeconds ?: 0) + addedDurationSeconds).coerceAtLeast(0)
        val distance = steps * stepLengthEstimator.estimate(profile).meters
        val speed = WalkingSpeedCalculator.movingKmh(distance, active)
        val calories = calorieEstimator.estimate(profile.weightKg, distance, active, speed)?.kcal
        database.sessions().upsert(
            WalkingSessionEntity(
                id = id,
                localDate = at.atZone(zone).toLocalDate().toString(),
                zoneId = zone.id,
                startedAtEpochMillis = started,
                endedAtEpochMillis = null,
                steps = steps,
                distanceMeters = distance,
                activeDurationSeconds = active,
                elapsedDurationSeconds = elapsed,
                pausedDurationSeconds = (elapsed - active).coerceAtLeast(0),
                estimatedCaloriesKcal = calories,
                averageMovingSpeedKmh = speed,
                averageElapsedSpeedKmh = WalkingSpeedCalculator.movingKmh(distance, elapsed),
                sessionType = WalkingSessionType.AUTO_DETECTED,
                status = WalkingSessionStatus.ACTIVE,
                stepsQuality = mergeQuality(listOfNotNull(old?.stepsQuality, DataQuality.MEASURED)),
                distanceQuality = DataQuality.ESTIMATED,
                durationQuality = if (active > 0) durationQuality else DataQuality.UNKNOWN,
                caloriesQuality = if (calories == null) DataQuality.UNKNOWN else DataQuality.ESTIMATED,
                speedQuality = if (speed == null) DataQuality.UNKNOWN else DataQuality.ESTIMATED,
                trackingServiceSessionId = trackingServiceSessionId,
                lastWalkingEventAtEpochMillis = at.toEpochMilli(),
                pausedSinceEpochMillis = null,
                isManual = false,
                detectorEventCount = (old?.detectorEventCount ?: 0) + detectorCount,
                estimatedStepCount = old?.estimatedStepCount ?: 0,
                recoveredStepCount = old?.recoveredStepCount ?: 0,
                createdAtEpochMillis = old?.createdAtEpochMillis ?: at.toEpochMilli(),
                updatedAtEpochMillis = at.toEpochMilli(),
            ),
        )
    }

    suspend fun checkSessionTimeouts(at: Instant) = writer.withLock {
        database.withTransaction {
            val session = database.sessions().active(false) ?: return@withTransaction
            val last = session.lastWalkingEventAtEpochMillis?.let(Instant::ofEpochMilli)
                ?: return@withTransaction
            val inactive = Duration.between(last, at).seconds
            when {
                inactive >= policy.sessionEndGapSeconds -> finishSessionLocked(session, at)
                inactive > policy.activeGapThresholdSeconds &&
                    session.status == WalkingSessionStatus.ACTIVE -> database.sessions().upsert(
                        session.copy(
                            status = WalkingSessionStatus.PAUSED,
                            pausedSinceEpochMillis = last.plusSeconds(policy.activeGapThresholdSeconds)
                                .toEpochMilli(),
                            updatedAtEpochMillis = at.toEpochMilli(),
                        ),
                    )
            }
        }
    }

    private suspend fun finishSessionLocked(
        session: WalkingSessionEntity,
        at: Instant,
        forceCompleted: Boolean = false,
    ) {
        val elapsed = Duration.between(Instant.ofEpochMilli(session.startedAtEpochMillis), at)
            .seconds.coerceAtLeast(session.activeDurationSeconds)
        val completed = forceCompleted || session.steps >= policy.minimumSessionSteps ||
            session.activeDurationSeconds >= policy.minimumSessionDurationSeconds
        database.sessions().upsert(
            session.copy(
                endedAtEpochMillis = at.toEpochMilli(),
                elapsedDurationSeconds = elapsed,
                pausedDurationSeconds = (elapsed - session.activeDurationSeconds).coerceAtLeast(0),
                pausedSinceEpochMillis = null,
                status = if (completed) WalkingSessionStatus.COMPLETED else WalkingSessionStatus.DISCARDED,
                updatedAtEpochMillis = at.toEpochMilli(),
            ),
        )
    }

    private suspend fun updateManualSession(
        old: WalkingSessionEntity,
        delta: Long,
        at: Instant,
        profile: UserBodyProfile,
        durationQuality: DataQuality,
        addedDurationSeconds: Long,
        detectorCount: Int,
    ) {
        val steps = old.steps + delta
        val elapsed = Duration.between(Instant.ofEpochMilli(old.startedAtEpochMillis), at)
            .seconds.coerceAtLeast(0)
        val active = (old.activeDurationSeconds + addedDurationSeconds).coerceIn(0, elapsed)
        val distance = steps * stepLengthEstimator.estimate(profile).meters
        val speed = WalkingSpeedCalculator.movingKmh(distance, active)
        val calories = calorieEstimator.estimate(profile.weightKg, distance, active, speed)?.kcal
        database.sessions().upsert(
            old.copy(
                steps = steps,
                distanceMeters = distance,
                activeDurationSeconds = active,
                elapsedDurationSeconds = elapsed,
                pausedDurationSeconds = (elapsed - active).coerceAtLeast(0),
                estimatedCaloriesKcal = calories,
                averageMovingSpeedKmh = speed,
                averageElapsedSpeedKmh = WalkingSpeedCalculator.movingKmh(distance, elapsed),
                status = WalkingSessionStatus.ACTIVE,
                stepsQuality = mergeQuality(listOf(old.stepsQuality, DataQuality.MEASURED)),
                distanceQuality = DataQuality.ESTIMATED,
                durationQuality = if (active > 0) durationQuality else DataQuality.UNKNOWN,
                caloriesQuality = if (calories == null) DataQuality.UNKNOWN else DataQuality.ESTIMATED,
                speedQuality = if (speed == null) DataQuality.UNKNOWN else DataQuality.ESTIMATED,
                lastWalkingEventAtEpochMillis = at.toEpochMilli(),
                pausedSinceEpochMillis = null,
                detectorEventCount = old.detectorEventCount + detectorCount,
                estimatedStepCount = old.estimatedStepCount,
                recoveredStepCount = old.recoveredStepCount,
                updatedAtEpochMillis = at.toEpochMilli(),
            ),
        )
    }

    private suspend fun splitManualSessionAtDateBoundary(
        at: Instant,
        zoneId: ZoneId,
        profile: UserBodyProfile,
        trackingServiceSessionId: String?,
    ) {
        val old = database.sessions().active(true) ?: return
        val date = at.atZone(zoneId).toLocalDate()
        if (old.localDate == date.toString()) return
        val boundary = date.atStartOfDay(zoneId).toInstant()
        finishSessionLocked(old, boundary, forceCompleted = true)
        database.sessions().upsert(
            newManualSession(
                UUID.randomUUID().toString(),
                boundary,
                zoneId,
                trackingServiceSessionId ?: old.trackingServiceSessionId.orEmpty(),
                profile,
            ),
        )
    }

    private fun newManualSession(
        id: String,
        at: Instant,
        zoneId: ZoneId,
        trackingServiceSessionId: String,
        profile: UserBodyProfile,
    ) = WalkingSessionEntity(
        id = id,
        localDate = at.atZone(zoneId).toLocalDate().toString(),
        zoneId = zoneId.id,
        startedAtEpochMillis = at.toEpochMilli(),
        endedAtEpochMillis = null,
        steps = 0,
        distanceMeters = 0.0,
        activeDurationSeconds = 0,
        elapsedDurationSeconds = 0,
        pausedDurationSeconds = 0,
        estimatedCaloriesKcal = 0.0,
        averageMovingSpeedKmh = null,
        averageElapsedSpeedKmh = null,
        sessionType = WalkingSessionType.MANUAL_WALK,
        status = WalkingSessionStatus.ACTIVE,
        stepsQuality = DataQuality.UNKNOWN,
        distanceQuality = DataQuality.ESTIMATED,
        durationQuality = DataQuality.UNKNOWN,
        caloriesQuality = if (profile.weightKg == null) DataQuality.ESTIMATED else DataQuality.UNKNOWN,
        speedQuality = DataQuality.UNKNOWN,
        trackingServiceSessionId = trackingServiceSessionId,
        lastWalkingEventAtEpochMillis = null,
        pausedSinceEpochMillis = null,
        isManual = true,
        detectorEventCount = 0,
        estimatedStepCount = 0,
        recoveredStepCount = 0,
        createdAtEpochMillis = at.toEpochMilli(),
        updatedAtEpochMillis = at.toEpochMilli(),
    )

    private suspend fun updateProcessingSessionIds(at: Instant) {
        val old = database.processingState().get()
        database.processingState().upsert(
            ActivityProcessingStateEntity(
                lastCounterValue = old?.lastCounterValue,
                lastEventEpochMillis = old?.lastEventEpochMillis,
                lastZoneId = old?.lastZoneId,
                lastBootSessionId = old?.lastBootSessionId,
                activeAutoSessionId = database.sessions().active(false)?.id,
                activeManualSessionId = database.sessions().active(true)?.id,
                lastDetectorEventEpochMillis = old?.lastDetectorEventEpochMillis,
                lastWalkingEventEpochMillis = old?.lastWalkingEventEpochMillis,
                updatedAtEpochMillis = at.toEpochMilli(),
            ),
        )
    }
}

private data class HourBucket(
    val date: LocalDate,
    val hour: Int,
    val zone: ZoneId,
    val offsetSeconds: Int,
    val start: Instant,
    val end: Instant,
) {
    val id: String = "${date}|$hour|${zone.id}|$offsetSeconds"
    companion object {
        fun of(instant: Instant, zone: ZoneId): HourBucket {
            val local = instant.atZone(zone)
            val start = local.truncatedTo(ChronoUnit.HOURS)
            return HourBucket(
                date = local.toLocalDate(),
                hour = local.hour,
                zone = zone,
                offsetSeconds = local.offset.totalSeconds,
                start = start.toInstant(),
                end = start.plusHours(1).toInstant(),
            )
        }
    }
}
