package com.travelingsalesman.agap.core.notification

import android.Manifest
import android.annotation.SuppressLint
import android.app.Notification
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.travelingsalesman.agap.data.local.entity.AdminAlertEntity
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AdminAlertNotificationCoordinator @Inject constructor(
    @ApplicationContext private val context: Context
) {
    companion object {
        const val CHANNEL_ID = "agap_admin_alerts"
        private const val NOTIFICATION_ID_BASE = 0xB6A9
    }

    @SuppressLint("MissingPermission")
    fun showAlert(alert: AdminAlertEntity) {
        if (!canPostNotifications()) return

        val manager = NotificationManagerCompat.from(context)
        
        val isEmergency = alert.severity == "EMERGENCY" || alert.severity == "CRITICAL"

        val builder = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_sys_warning)
            .setContentTitle(alert.title)
            .setContentText(alert.body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(alert.body))
            .setPriority(if (isEmergency) NotificationCompat.PRIORITY_MAX else NotificationCompat.PRIORITY_HIGH)
            .setCategory(Notification.CATEGORY_ALARM)
            .setAutoCancel(true)
            .setDefaults(Notification.DEFAULT_ALL)

        if (isEmergency) {
            builder.setVibrate(longArrayOf(0, 1000, 500, 1000, 500, 1000))
        }

        runCatching {
            if (canPostNotifications()) {
                manager.notify(NOTIFICATION_ID_BASE + alert.id.toInt(), builder.build())
            }
        }
    }

    private fun canPostNotifications(): Boolean {
        return Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
    }
}
