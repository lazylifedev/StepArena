package com.lazyapps.steparena.game

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.lazyapps.steparena.R
import com.lazyapps.steparena.app.MainActivity
import com.lazyapps.steparena.core.database.StepArenaDatabase
import com.lazyapps.steparena.core.database.entity.GameNotificationEventEntity
import java.time.Clock
import java.time.Instant
import java.time.LocalTime

class GameNotificationDispatcher(
    private val context: Context,
    private val database: StepArenaDatabase,
    private val clock: Clock = Clock.systemDefaultZone(),
) {
    suspend fun dispatchPending(): Int {
        val preferences = context.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE)
        if (!preferences.getBoolean(KEY_ENABLED, false)) return 0
        if (QuietHours.isQuiet(LocalTime.now(clock))) return 0
        if (Build.VERSION.SDK_INT >= 33 &&
            context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) return 0

        createChannel()
        var delivered = 0
        database.gameNotificationEvents().pending(clock.millis()).forEach { event ->
            val claimed = database.gameNotificationEvents().markDelivered(event.id, clock.millis())
            if (claimed == 1) {
                NotificationManagerCompat.from(context).notify(event.id.hashCode(), notification(event))
                delivered++
            }
        }
        return delivered
    }

    private fun notification(event: GameNotificationEventEntity) =
        NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle(event.title)
            .setContentText(event.message)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(true)
            .setContentIntent(
                PendingIntent.getActivity(
                    context,
                    event.id.hashCode(),
                    Intent(context, MainActivity::class.java)
                        .putExtra(EXTRA_DESTINATION, event.destinationRoute)
                        .putExtra(EXTRA_EVENT_ID, event.id)
                        .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP),
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
                ),
            )
            .build()

    private fun createChannel() {
        if (Build.VERSION.SDK_INT >= 26) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "ゲーム結果",
                NotificationManager.IMPORTANCE_DEFAULT,
            ).apply {
                description = "対戦結果、昇格、実績、リーグ、シーズンのお知らせ"
                enableVibration(false)
            }
            context.getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        }
    }

    companion object {
        const val PREFERENCES = "game_notifications"
        const val KEY_ENABLED = "enabled"
        const val CHANNEL_ID = "game_results"
        const val EXTRA_DESTINATION = "game_destination"
        const val EXTRA_EVENT_ID = "game_notification_event_id"

        fun nextAllowedEpochMillis(clock: Clock): Long {
            val now = Instant.now(clock).atZone(clock.zone)
            return if (QuietHours.isQuiet(now.toLocalTime())) {
                val morning = if (now.toLocalTime() < LocalTime.of(8, 0)) {
                    now.toLocalDate()
                } else {
                    now.toLocalDate().plusDays(1)
                }
                morning.atTime(8, 0).atZone(clock.zone).toInstant().toEpochMilli()
            } else {
                clock.millis()
            }
        }
    }
}
