package com.lazyapps.steparena.tracking

import android.content.Context
import android.os.SystemClock
import android.provider.Settings

object BootSession {
    fun current(context: Context): String {
        val bootCount = runCatching {
            Settings.Global.getInt(context.contentResolver, Settings.Global.BOOT_COUNT)
        }.getOrNull()
        return bootCount?.let { "boot-count-$it" } ?: current()
    }

    fun current(nowEpochMillis: Long = System.currentTimeMillis(), elapsedMillis: Long = SystemClock.elapsedRealtime()): String {
        val bootEpochMinute = (nowEpochMillis - elapsedMillis) / 60_000L
        return "boot-$bootEpochMinute"
    }
}
