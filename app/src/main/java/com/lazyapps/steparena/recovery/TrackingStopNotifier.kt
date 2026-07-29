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
                "計測状態の警告",
                NotificationManager.IMPORTANCE_DEFAULT,
            ),
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
            "計測が停止した可能性があります。タップして状態を確認してください。"
        } else {
            "計測状態を12分以上確認できませんでした。"
        }
        NotificationManagerCompat.from(context).notify(
            NOTIFICATION_ID,
            NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(R.mipmap.ic_launcher)
                .setContentTitle("StepArenaの歩数計測を確認してください")
                .setContentText(text)
                .setStyle(NotificationCompat.BigTextStyle().bigText(text))
                .setContentIntent(open)
                .setAutoCancel(true)
                .addAction(0, "状態を確認", open)
                .addAction(0, "計測を再開", restart)
                .build(),
        )
    }
}
