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
import android.content.pm.PackageManager
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
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
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
        // onStartCommand() is the single transport-start entry point. Android
        // invokes it after onCreate(), including sticky service recreation.
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
            bleMeshManager.meshState
                .map(::meshNotificationText)
                .distinctUntilChanged()
                .collect { contentText ->
                    // Scan callbacks can update mesh state many times per second.
                    // Only touch SystemUI when the user-visible notification text
                    // actually changes.
                    updateNotificationContent(contentText)
                }
        }
    }

    private fun meshNotificationText(state: BleMeshState): String = when {
        !state.isSupported && !state.nearbyTransportAvailable -> "Nearby relay transports unavailable"
        state.isTransferring && state.activeTransport == "NEARBY" ->
            "Relaying by Nearby P2P cluster • ${state.nearbyDeviceCount} peer(s) nearby"
        state.isTransferring ->
            "Relaying by native BLE/GATT • ${state.nearbyDeviceCount} peer(s) nearby"
        state.nearbyTransportActive && state.isScanning ->
            "Nearby preferred + BLE fallback • ${state.nearbyDeviceCount} peer(s) nearby"
        !state.isEnabled -> "Bluetooth disabled • native BLE fallback paused"
        state.isScanning -> "Native BLE fallback active • ${state.nearbyDeviceCount} peer(s) nearby"
        state.lastError != null -> state.lastError
        else -> "AGAP emergency relay service active"
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
                "AGAP Emergency Relay Service",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Runs Nearby P2P relay with native BLE/GATT fallback"
            }
            (getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager)
                .createNotificationChannel(channel)
        }

        startForeground(
            NOTIFICATION_ID,
            buildNotification("Starting Nearby relay with native BLE fallback…")
        )
    }

    private fun updateNotificationContent(text: String) {
        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.notify(NOTIFICATION_ID, buildNotification(text))
    }

    private fun buildNotification(text: String): Notification {
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("AGAP Relay Network Active")
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

        /** Starts the relay service from either the app UI or a system broadcast. */
        fun start(context: Context) {
            val serviceIntent = Intent(context, BleMeshService::class.java)
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    ContextCompat.startForegroundService(context, serviceIntent)
                } else {
                    context.startService(serviceIntent)
                }
                BleMeshLog.i("SERVICE_START_REQUESTED")
            } catch (e: Exception) {
                // A device manufacturer or a future platform version can still reject a
                // background service start. Keep the receiver from crashing the process.
                BleMeshLog.e("SERVICE_START_REJECTED", "error" to (e.message ?: e.javaClass.simpleName))
            }
        }

        fun hasRequiredPermissions(context: Context): Boolean {
            fun has(permission: String) = ContextCompat.checkSelfPermission(
                context,
                permission
            ) == PackageManager.PERMISSION_GRANTED

            return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                has(android.Manifest.permission.BLUETOOTH_SCAN) &&
                    has(android.Manifest.permission.BLUETOOTH_CONNECT) &&
                    has(android.Manifest.permission.BLUETOOTH_ADVERTISE) &&
                    has(android.Manifest.permission.ACCESS_FINE_LOCATION)
            } else {
                has(android.Manifest.permission.ACCESS_FINE_LOCATION)
            }
        }
    }
}
