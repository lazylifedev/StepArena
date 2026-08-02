package com.lazyapps.steparena.recovery

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
import androidx.core.content.ContextCompat
import com.lazyapps.steparena.R
import com.lazyapps.steparena.app.MainActivity
import com.lazyapps.steparena.service.tracking.StepTrackingService

object TrackingStopNotifier {
    private const val CHANNEL_ID = "tracking_health_alerts"
    private const val NOTIFICATION_ID = 2204

    fun notify(context: Context, severe: Boolean) {
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) return
        val manager = context.getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(
            NotificationChannel(
                CHANNEL_ID,
                context.getString(R.string.notification_channel_tracking_status),
                NotificationManager.IMPORTANCE_DEFAULT,
            ).apply {
                description = context.getString(R.string.notification_channel_tracking_status_description)
            },
        )
        val open = PendingIntent.getActivity(
            context,
            2204,
            Intent(context, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val restart = PendingIntent.getForegroundService(
            context,
            2205,
            Intent(context, StepTrackingService::class.java)
                .setAction(StepTrackingService.ACTION_START),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val text = if (severe) {
            context.getString(R.string.notification_tracking_stopped_severe)
        } else {
            context.getString(R.string.notification_tracking_stopped_delayed)
        }
        NotificationManagerCompat.from(context).notify(
            NOTIFICATION_ID,
            NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(R.mipmap.ic_launcher)
                .setContentTitle(context.getString(R.string.notification_tracking_stopped_title))
                .setContentText(text)
                .setStyle(NotificationCompat.BigTextStyle().bigText(text))
                .setContentIntent(open)
                .setAutoCancel(true)
                .addAction(0, context.getString(R.string.notification_action_check_status), open)
                .addAction(0, context.getString(R.string.notification_action_restart_tracking), restart)
                .build(),
        )
    }
}
