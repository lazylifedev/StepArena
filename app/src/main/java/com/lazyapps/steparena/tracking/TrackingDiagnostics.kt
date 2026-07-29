package com.lazyapps.steparena.tracking

import android.Manifest
import android.app.NotificationManager
import android.content.Context
import android.content.pm.PackageManager
import android.hardware.Sensor
import android.hardware.SensorManager
import android.os.Build
import android.os.PowerManager
import androidx.core.content.ContextCompat

data class TrackingDiagnostics(
    val activityPermissionGranted: Boolean,
    val notificationPermissionGranted: Boolean,
    val stepSensorAvailable: Boolean,
    val batteryOptimizationIgnored: Boolean,
)

fun Context.readTrackingDiagnostics(): TrackingDiagnostics {
    val sensors = getSystemService(SensorManager::class.java)
    val notifications = getSystemService(NotificationManager::class.java)
    val power = getSystemService(PowerManager::class.java)
    return TrackingDiagnostics(
        activityPermissionGranted = ContextCompat.checkSelfPermission(
            this, Manifest.permission.ACTIVITY_RECOGNITION,
        ) == PackageManager.PERMISSION_GRANTED,
        notificationPermissionGranted = if (Build.VERSION.SDK_INT >= 33) {
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) ==
                PackageManager.PERMISSION_GRANTED
        } else notifications.areNotificationsEnabled(),
        stepSensorAvailable = sensors.getDefaultSensor(Sensor.TYPE_STEP_COUNTER) != null,
        batteryOptimizationIgnored = power.isIgnoringBatteryOptimizations(packageName),
    )
}
