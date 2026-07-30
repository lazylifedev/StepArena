package com.lazyapps.steparena.core.time

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.stateIn

data class LocalDay(val date: LocalDate, val zoneId: ZoneId)

fun localDayAt(instant: Instant, zoneId: ZoneId): LocalDay =
    LocalDay(instant.atZone(zoneId).toLocalDate(), zoneId)

fun millisUntilNextLocalDay(instant: Instant, zoneId: ZoneId): Long {
    val nextStart = instant.atZone(zoneId).toLocalDate().plusDays(1).atStartOfDay(zoneId).toInstant()
    return Duration.between(instant, nextStart).toMillis().coerceAtLeast(1)
}

fun currentLocalDayFlow(
    clock: Clock,
    zoneId: () -> ZoneId = ZoneId::systemDefault,
    changes: Flow<Unit>,
    waitForNextBoundary: suspend (Long) -> Unit = { delay(it) },
): Flow<LocalDay> {
    val boundaries = flow {
        while (true) {
            emit(Unit)
            val zone = zoneId()
            waitForNextBoundary(millisUntilNextLocalDay(clock.instant(), zone) + 1)
        }
    }
    return merge(changes.onStart { emit(Unit) }, boundaries)
        .map { localDayAt(clock.instant(), zoneId()) }
        .distinctUntilChanged()
}

class CurrentLocalDayProvider(
    context: Context,
    clock: Clock,
    scope: CoroutineScope,
    zoneId: () -> ZoneId = ZoneId::systemDefault,
) {
    private val systemChanges = callbackFlow {
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                trySend(Unit)
            }
        }
        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_DATE_CHANGED)
            addAction(Intent.ACTION_TIME_CHANGED)
            addAction(Intent.ACTION_TIMEZONE_CHANGED)
        }
        context.registerReceiver(receiver, filter)
        awaitClose { context.unregisterReceiver(receiver) }
    }

    val current: StateFlow<LocalDay> = currentLocalDayFlow(clock, zoneId, systemChanges)
        .stateIn(
            scope,
            SharingStarted.Eagerly,
            localDayAt(clock.instant(), zoneId()),
        )
}
