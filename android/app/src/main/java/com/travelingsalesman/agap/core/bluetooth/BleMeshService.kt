package com.travelingsalesman.agap.core.bluetooth

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import dagger.hilt.android.AndroidEntryPoint
import timber.log.Timber
import javax.inject.Inject

@AndroidEntryPoint
class BleMeshService : Service() {

    @Inject
    lateinit var bleMeshManager: BleMeshManager

    override fun onCreate() {
        super.onCreate()
        startForegroundServiceNotification()
        bleMeshManager.startMeshServices()
        Timber.i("BleMeshService: Foreground BLE Mesh Service started.")
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        return START_STICKY
    }

    override fun onDestroy() {
        super.onDestroy()
        bleMeshManager.stopScanning()
        bleMeshManager.stopAdvertising()
        Timber.i("BleMeshService: Foreground BLE Mesh Service stopped.")
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun startForegroundServiceNotification() {
        val channelId = "agap_ble_mesh_channel"
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                channelId,
                "AGAP BLE Mesh Relay Service",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Runs background BLE scanning and P2P distress relaying"
            }
            val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            manager.createNotificationChannel(channel)
        }

        val notification: Notification = NotificationCompat.Builder(this, channelId)
            .setContentTitle("AGAP BLE Mesh Active")
            .setContentText("Scanning for nearby distress signals & relaying emergency telemetry...")
            .setSmallIcon(android.R.drawable.stat_sys_data_bluetooth)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setOngoing(true)
            .build()

        startForeground(1001, notification)
    }
}
