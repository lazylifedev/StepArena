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
) {
    private val writer = Mutex()
    private val detectorEvents = ArrayDeque<Instant>()

    fun observeToday(date: LocalDate) = database.daily().observeDate(date.toString())
    fun observeHours(date: LocalDate) = database.hourly().observeDate(date.toString())
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
            val allocations = allocate(delta, previousAt, at, zoneId, longGap)
            allocations.forEach { (bucket, steps) ->
                upsertHour(bucket, steps, at, profile, quality)
            }
            updateSession(delta, at, zoneId, profile, quality, trackingServiceSessionId)
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
                    updatedAtEpochMillis = at.toEpochMilli(),
                ),
            )
            detectorEvents.clear()
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
    ): Map<HourBucket, Long> {
        if (longGap) return emptyMap()
        if (previousAt == null || !previousAt.isBefore(at)) {
            return mapOf(HourBucket.of(at, zone) to delta)
        }
        val matchingDetector = detectorEvents.filter { !it.isBefore(previousAt) && !it.isAfter(at) }
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
    ) {
        val old = database.hourly().byId(bucket.id)
        val steps = (old?.steps ?: 0) + addedSteps
        val stepLength = stepLengthEstimator.estimate(profile)
        val distance = steps * stepLength.meters
        val first = old?.firstActivityAtEpochMillis?.let(Instant::ofEpochMilli) ?: eventAt
        val last = eventAt
        val duration = if (Duration.between(first, last).seconds >= 0) {
            Duration.between(first, last).seconds.coerceAtMost(3_600)
        } else null
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
                durationQuality = if (duration == null) DataQuality.UNKNOWN else quality,
                caloriesQuality = calories?.quality ?: DataQuality.UNKNOWN,
                speedQuality = if (speed == null) DataQuality.UNKNOWN else DataQuality.ESTIMATED,
                firstActivityAtEpochMillis = first.toEpochMilli(),
                lastActivityAtEpochMillis = last.toEpochMilli(),
                sensorEventCount = (old?.sensorEventCount ?: 0) + 1,
                recoveredSteps = (old?.recoveredSteps ?: 0) +
                    if (quality == DataQuality.RECOVERED) addedSteps else 0,
                estimatedSteps = (old?.estimatedSteps ?: 0) +
                    if (quality == DataQuality.ESTIMATED || quality == DataQuality.MIXED) addedSteps else 0,
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
                    hours.map { it.stepsQuality } + listOfNotNull(addedQuality),
                ),
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

    private suspend fun updateSession(
        delta: Long,
        at: Instant,
        zone: ZoneId,
        profile: UserBodyProfile,
        quality: DataQuality,
        trackingServiceSessionId: String?,
    ) {
        // A service session is a stable idempotency key and a session view, not another step source.
        val id = trackingServiceSessionId ?: return
        val old = database.sessions().get(id)
        val steps = (old?.steps ?: 0) + delta
        val started = old?.startedAtEpochMillis ?: at.toEpochMilli()
        val elapsed = Duration.between(Instant.ofEpochMilli(started), at).seconds.coerceAtLeast(0)
        val active = if (old == null) 0 else {
            (old.activeDurationSeconds + Duration.between(
                Instant.ofEpochMilli(old.updatedAtEpochMillis), at,
            ).seconds.coerceIn(0, policy.activeGapThresholdSeconds))
        }
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
                sessionType = old?.sessionType ?: WalkingSessionType.MANUAL_WALK,
                status = WalkingSessionStatus.ACTIVE,
                stepsQuality = mergeQuality(listOfNotNull(old?.stepsQuality, quality)),
                distanceQuality = DataQuality.ESTIMATED,
                durationQuality = if (active >= 60) quality else DataQuality.UNKNOWN,
                caloriesQuality = if (calories == null) DataQuality.UNKNOWN else DataQuality.ESTIMATED,
                speedQuality = if (speed == null) DataQuality.UNKNOWN else DataQuality.ESTIMATED,
                trackingServiceSessionId = trackingServiceSessionId,
                createdAtEpochMillis = old?.createdAtEpochMillis ?: at.toEpochMilli(),
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
