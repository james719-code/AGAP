package com.travelingsalesman.agap.core.bluetooth

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.travelingsalesman.agap.MainActivity
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject

@AndroidEntryPoint
class BleMeshService : Service() {

    @Inject
    lateinit var bleMeshManager: BleMeshManager

    private val serviceScope = CoroutineScope(Dispatchers.Main + Job())

    override fun onCreate() {
        super.onCreate()
        startForegroundServiceNotification()
        bleMeshManager.startMeshServices()
        observeMeshStateForNotification()
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

    private fun observeMeshStateForNotification() {
        serviceScope.launch {
            bleMeshManager.meshState.collectLatest { state ->
                val contentText = when {
                    state.isAdvertising -> "Broadcasting P2P emergency SOS signal..."
                    state.isScanning -> "${state.nearbyDeviceCount} nearby peer(s) detected • Relay active"
                    !state.isEnabled -> "Bluetooth disabled • Relay paused"

                    else -> "BLE Mesh service active"
                }
                updateNotificationContent(contentText)
            }
        }
    }

    private fun buildPendingIntent(): PendingIntent {
        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val flags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        } else {
            PendingIntent.FLAG_UPDATE_CURRENT
        }
        return PendingIntent.getActivity(this, 0, intent, flags)
    }

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
            .setContentIntent(buildPendingIntent())
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setOngoing(true)
            .build()

        startForeground(1001, notification)
    }

    private fun updateNotificationContent(text: String) {
        val channelId = "agap_ble_mesh_channel"
        val notification: Notification = NotificationCompat.Builder(this, channelId)
            .setContentTitle("AGAP BLE Mesh Active")
            .setContentText(text)
            .setSmallIcon(android.R.drawable.stat_sys_data_bluetooth)
            .setContentIntent(buildPendingIntent())
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setOngoing(true)
            .build()

        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.notify(1001, notification)
    }
}

