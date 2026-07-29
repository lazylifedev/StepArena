package com.lazyapps.steparena.activity

import androidx.room.withTransaction
import com.lazyapps.steparena.core.database.StepArenaDatabase
import com.lazyapps.steparena.core.database.entity.ActivityProcessingStateEntity
import com.lazyapps.steparena.core.database.entity.DailyActivityRecordEntity
import com.lazyapps.steparena.core.database.entity.HourlyActivityRecordEntity
import com.lazyapps.steparena.core.database.entity.WalkingSessionEntity
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

class ActivityRepository(
    private val database: StepArenaDatabase,
    private val profileRepository: UserProfileRepository,
    private val stepLengthEstimator: StepLengthEstimator = DefaultStepLengthEstimator(),
    private val calorieEstimator: CalorieEstimator = DistanceCalorieEstimator(),
    private val policy: WalkingDetectionPolicy = WalkingDetectionPolicy(),
    private val durationCalculator: WalkingDurationCalculator =
        WalkingDurationCalculator(policy.activeGapThresholdSeconds),
) {
    private val writer = Mutex()
    private val detectorEvents = ArrayDeque<Instant>()

    fun observeToday(date: LocalDate, zoneId: ZoneId) =
        database.daily().observeDate(date.toString(), zoneId.id)
    fun observeHours(date: LocalDate, zoneId: ZoneId) =
        database.hourly().observeDate(date.toString(), zoneId.id)
    fun observeSessions() = database.sessions().observeAll()

    suspend fun recordDetector(at: Instant) = writer.withLock {
        detectorEvents.addLast(at)
        while (detectorEvents.firstOrNull()?.isBefore(at.minusSeconds(3_600)) == true) {
            detectorEvents.removeFirst()
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
    ) = writer.withLock {
        if (delta <= 0) return@withLock
        val profile = profileRepository.current()
        database.withTransaction {
            val processing = database.processingState().get()
            if (
                processing?.lastCounterValue == sensorValue &&
                processing.lastBootSessionId == bootSessionId
            ) return@withTransaction

            val previousAt = processing?.lastEventEpochMillis?.let(Instant::ofEpochMilli)
            val longGap = previousAt != null && Duration.between(previousAt, at).toHours() >= 2
            val quality = when {
                recovered || longGap -> DataQuality.RECOVERED
                detectorEvents.isEmpty() -> DataQuality.ESTIMATED
                else -> DataQuality.MIXED
            }
            val consumedDetectors = detectorEvents
                .filter { previousAt == null || !it.isBefore(previousAt) }
                .filter { !it.isAfter(at) }
                .take(delta.coerceAtMost(Int.MAX_VALUE.toLong()).toInt())
            val allocations = allocate(delta, previousAt, at, zoneId, longGap, consumedDetectors)
            val addedDuration = if (consumedDetectors.isNotEmpty()) {
                durationCalculator.fromDetectorEvents(consumedDetectors)
            } else {
                durationCalculator.fromCounterEvents(previousAt, at)
            }
            allocations.forEach { (bucket, steps) ->
                val bucketDuration = if (allocations.size == 1) addedDuration else 0
                upsertHour(bucket, steps, at, profile, quality, bucketDuration)
            }
            updateAutoSession(
                delta, at, zoneId, profile, quality, trackingServiceSessionId,
                addedDuration, consumedDetectors.size,
            )
            val affectedDays = allocations.keys.map { it.date to it.zone }.toSet()
            if (longGap) {
                rebuildDay(
                    at.atZone(zoneId).toLocalDate(),
                    zoneId,
                    profile,
                    at,
                    addedUnclassifiedSteps = delta,
                    addedQuality = DataQuality.RECOVERED,
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
                    lastDetectorEventEpochMillis = consumedDetectors.lastOrNull()?.toEpochMilli()
                        ?: processing?.lastDetectorEventEpochMillis,
                    lastWalkingEventEpochMillis = at.toEpochMilli(),
                    updatedAtEpochMillis = at.toEpochMilli(),
                ),
            )
            repeat(consumedDetectors.size) { detectorEvents.removeFirstOrNull() }
        }
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
        quality: DataQuality,
        addedDurationSeconds: Long,
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
                stepsQuality = mergeQuality(listOfNotNull(old?.stepsQuality, quality)),
                distanceQuality = DataQuality.ESTIMATED,
                durationQuality = if (duration == 0L) DataQuality.UNKNOWN else quality,
                caloriesQuality = calories?.quality ?: DataQuality.UNKNOWN,
                speedQuality = if (speed == null) DataQuality.UNKNOWN else DataQuality.ESTIMATED,
                firstActivityAtEpochMillis = first.toEpochMilli(),
                lastActivityAtEpochMillis = last.toEpochMilli(),
                sensorEventCount = (old?.sensorEventCount ?: 0) + 1,
                recoveredSteps = (old?.recoveredSteps ?: 0) +
                    if (quality == DataQuality.RECOVERED) addedSteps else 0,
                estimatedSteps = (old?.estimatedSteps ?: 0) +
                    if (quality == DataQuality.ESTIMATED || quality == DataQuality.MIXED) addedSteps else 0,
                appliedStepLengthMeters = stepLength.meters,
                appliedWeightKg = profile.weightKg ?: DistanceCalorieEstimator.DEFAULT_WEIGHT_KG,
                calorieFormulaVersion = 1,
                createdAtEpochMillis = old?.createdAtEpochMillis ?: now,
                updatedAtEpochMillis = now,
            ),
        )
    }

    private suspend fun rebuildDay(
        date: LocalDate,
        zone: ZoneId,
        profile: UserBodyProfile,
        at: Instant,
        addedUnclassifiedSteps: Long = 0,
        addedQuality: DataQuality? = null,
    ) {
        val hours = database.hourly().forDate(date.toString(), zone.id)
        val old = database.daily().get(date.toString(), zone.id)
        val unclassified = (old?.unclassifiedSteps ?: 0) + addedUnclassifiedSteps
        val unclassifiedQuality = mergeQuality(
            listOfNotNull(old?.unclassifiedStepsQuality, addedQuality)
                .filterNot { it == DataQuality.UNKNOWN },
        )
        val steps = hours.sumOf { it.steps } + unclassified
        val distance = hours.mapNotNull { it.distanceMeters }.takeIf { it.isNotEmpty() }?.sum()
        val duration = hours.mapNotNull { it.walkingDurationSeconds }.takeIf { it.isNotEmpty() }?.sum()
        val calories = calorieEstimator.estimate(profile.weightKg, distance, duration, null)?.kcal
        val speed = WalkingSpeedCalculator.movingKmh(distance, duration)
        val now = at.toEpochMilli()
        database.daily().upsert(
            DailyActivityRecordEntity(
                id = "${date}|${zone.id}",
                localDate = date.toString(),
                zoneId = zone.id,
                steps = steps,
                unclassifiedSteps = unclassified,
                distanceMeters = distance,
                walkingDurationSeconds = duration,
                estimatedCaloriesKcal = calories,
                averageWalkingSpeedKmh = speed,
                stepsQuality = mergeQuality(
                    hours.map { it.stepsQuality } +
                        listOf(unclassifiedQuality).filterNot { it == DataQuality.UNKNOWN },
                ),
                unclassifiedStepsQuality = unclassifiedQuality,
                distanceQuality = if (distance == null) DataQuality.UNKNOWN else DataQuality.ESTIMATED,
                durationQuality = mergeQuality(hours.map { it.durationQuality }),
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
        quality: DataQuality,
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
                stepsQuality = mergeQuality(listOfNotNull(old?.stepsQuality, quality)),
                distanceQuality = DataQuality.ESTIMATED,
                durationQuality = if (active >= 60) quality else DataQuality.UNKNOWN,
                caloriesQuality = if (calories == null) DataQuality.UNKNOWN else DataQuality.ESTIMATED,
                speedQuality = if (speed == null) DataQuality.UNKNOWN else DataQuality.ESTIMATED,
                trackingServiceSessionId = trackingServiceSessionId,
                lastWalkingEventAtEpochMillis = at.toEpochMilli(),
                pausedSinceEpochMillis = null,
                isManual = false,
                detectorEventCount = (old?.detectorEventCount ?: 0) + detectorCount,
                estimatedStepCount = (old?.estimatedStepCount ?: 0) +
                    if (quality == DataQuality.ESTIMATED || quality == DataQuality.MIXED) delta else 0,
                recoveredStepCount = (old?.recoveredStepCount ?: 0) +
                    if (quality == DataQuality.RECOVERED) delta else 0,
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

    private suspend fun finishSessionLocked(session: WalkingSessionEntity, at: Instant) {
        val elapsed = Duration.between(Instant.ofEpochMilli(session.startedAtEpochMillis), at)
            .seconds.coerceAtLeast(session.activeDurationSeconds)
        val completed = session.steps >= policy.minimumSessionSteps ||
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
