package com.travelingsalesman.agap.core.bluetooth

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.bluetooth.BluetoothAdapter
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.travelingsalesman.agap.MainActivity
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class BleMeshService : Service() {

    @Inject
    lateinit var bleMeshManager: BleMeshManager

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var bluetoothReceiverRegistered = false

    private val bluetoothStateReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action != BluetoothAdapter.ACTION_STATE_CHANGED) return
            when (intent.getIntExtra(BluetoothAdapter.EXTRA_STATE, BluetoothAdapter.ERROR)) {
                BluetoothAdapter.STATE_ON -> {
                    BleMeshLog.i("SERVICE_BLUETOOTH_ON")
                    bleMeshManager.onBluetoothStateChanged(true)
                }

                BluetoothAdapter.STATE_OFF,
                BluetoothAdapter.STATE_TURNING_OFF -> {
                    BleMeshLog.i("SERVICE_BLUETOOTH_OFF")
                    bleMeshManager.onBluetoothStateChanged(false)
                }
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        startForegroundServiceNotification()
        registerBluetoothStateReceiver()
        bleMeshManager.startMeshServices()
        observeMeshStateForNotification()
        BleMeshLog.i("SERVICE_STARTED", "sdk" to Build.VERSION.SDK_INT)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // START_STICKY can recreate the service without calling the Activity again.
        BleMeshLog.d("SERVICE_START_COMMAND", "startId" to startId, "flags" to flags)
        bleMeshManager.startMeshServices()
        return START_STICKY
    }

    override fun onDestroy() {
        if (bluetoothReceiverRegistered) {
            runCatching { unregisterReceiver(bluetoothStateReceiver) }
            bluetoothReceiverRegistered = false
        }
        bleMeshManager.stopMeshServices()
        serviceScope.cancel()
        BleMeshLog.i("SERVICE_STOPPED")
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun registerBluetoothStateReceiver() {
        if (bluetoothReceiverRegistered) return
        ContextCompat.registerReceiver(
            this,
            bluetoothStateReceiver,
            IntentFilter(BluetoothAdapter.ACTION_STATE_CHANGED),
            ContextCompat.RECEIVER_NOT_EXPORTED
        )
        bluetoothReceiverRegistered = true
        BleMeshLog.d("SERVICE_BT_RECEIVER_REGISTERED")
    }

    private fun observeMeshStateForNotification() {
        serviceScope.launch {
            bleMeshManager.meshState.collectLatest { state ->
                val contentText = when {
                    !state.isSupported -> "Bluetooth LE is not supported on this device"
                    !state.isEnabled -> "Bluetooth disabled • BLE relay paused"
                    state.isTransferring -> "Sending queued packet by GATT • ${state.nearbyDeviceCount} peer(s) nearby"
                    state.isScanning && state.isAdvertising -> "Scanning + GATT ready • ${state.nearbyDeviceCount} AGAP peer(s) nearby"
                    state.isScanning -> "Scanning for AGAP peers • ${state.nearbyDeviceCount} peer(s) nearby"
                    state.lastError != null -> state.lastError
                    else -> "BLE GATT mesh service active"
                }
                updateNotificationContent(contentText)
            }
        }
    }

    private fun buildPendingIntent(): PendingIntent {
        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val flags = PendingIntent.FLAG_UPDATE_CURRENT or
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) PendingIntent.FLAG_IMMUTABLE else 0
        return PendingIntent.getActivity(this, 0, intent, flags)
    }

    private fun startForegroundServiceNotification() {
        val channelId = CHANNEL_ID
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                channelId,
                "AGAP BLE Mesh Relay Service",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Runs BLE peer discovery and reliable GATT emergency relaying"
            }
            (getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager)
                .createNotificationChannel(channel)
        }

        startForeground(
            NOTIFICATION_ID,
            buildNotification("Scanning for AGAP peers and preparing GATT relay…")
        )
    }

    private fun updateNotificationContent(text: String) {
        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.notify(NOTIFICATION_ID, buildNotification(text))
    }

    private fun buildNotification(text: String): Notification {
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("AGAP BLE Mesh Active")
            .setContentText(text)
            .setSmallIcon(android.R.drawable.stat_sys_data_bluetooth)
            .setContentIntent(buildPendingIntent())
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setOnlyAlertOnce(true)
            .setOngoing(true)
            .build()
    }

    companion object {
        private const val CHANNEL_ID = "agap_ble_mesh_channel"
        private const val NOTIFICATION_ID = 1001
    }
}
