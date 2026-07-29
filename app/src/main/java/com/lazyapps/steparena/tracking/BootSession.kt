package com.lazyapps.steparena.tracking

import android.os.SystemClock

object BootSession {
    fun current(nowEpochMillis: Long = System.currentTimeMillis(), elapsedMillis: Long = SystemClock.elapsedRealtime()): String {
        val bootEpochMinute = (nowEpochMillis - elapsedMillis) / 60_000L
        return "boot-$bootEpochMinute"
    }
}
