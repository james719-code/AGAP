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
import android.os.Looper
import androidx.core.app.NotificationCompat
import com.google.android.gms.location.*
import com.travelingsalesman.agap.MainActivity
import com.travelingsalesman.agap.data.repository.AuthRepository
import com.travelingsalesman.agap.data.repository.ResponderRepository
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.firstOrNull
import timber.log.Timber
import javax.inject.Inject

@AndroidEntryPoint
class ResponderTelemetryService : Service() {

    @Inject
    lateinit var responderRepository: ResponderRepository

    @Inject
    lateinit var authRepository: AuthRepository

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private lateinit var locationCallback: LocationCallback

    override fun onCreate() {
        super.onCreate()
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)
        
        locationCallback = object : LocationCallback() {
            override fun onLocationResult(locationResult: LocationResult) {
                locationResult.lastLocation?.let { location ->
                    updateBackendLocation(location)
                }
            }
        }

        startForegroundServiceNotification()
        startLocationUpdates()
    }

    private fun startLocationUpdates() {
        val locationRequest = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 15000)
            .setMinUpdateIntervalMillis(10000)
            .build()

        try {
            fusedLocationClient.requestLocationUpdates(
                locationRequest,
                locationCallback,
                Looper.getMainLooper()
            )
        } catch (unlikely: SecurityException) {
            Timber.e("Lost location permission. Could not request updates. $unlikely")
        }
    }

    private fun updateBackendLocation(location: android.location.Location) {
        serviceScope.launch {
            val role = authRepository.userRole.firstOrNull()
            if (role != "RESPONDER" && role != "ADMIN") {
                Timber.d("Telemetry: User is not a responder. Stopping service.")
                stopSelf()
                return@launch
            }

            val unitId = responderRepository.getMyResponderUnit().let { res ->
                if (res is com.travelingsalesman.agap.core.util.Result.Success) res.data.id else null
            } ?: authRepository.userId.firstOrNull() ?: return@launch

            val status = responderRepository.getMyResponderUnit().let { res ->
                if (res is com.travelingsalesman.agap.core.util.Result.Success) res.data.status else "IDLE"
            }

            responderRepository.updateLocation(
                unitId,
                location.latitude,
                location.longitude,
                status
            )
            Timber.d("Telemetry: Updated position for unit #$unitId to ${location.latitude}, ${location.longitude}")
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        super.onDestroy()
        fusedLocationClient.removeLocationUpdates(locationCallback)
        serviceScope.cancel()
    }

    private fun startForegroundServiceNotification() {
        val channelId = "agap_telemetry_channel"
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                channelId,
                "AGAP Responder Telemetry",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Keeps command center updated with your current field position"
            }
            val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            manager.createNotificationChannel(channel)
        }

        val pendingIntent = PendingIntent.getActivity(
            this, 0,
            Intent(this, MainActivity::class.java),
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) PendingIntent.FLAG_IMMUTABLE else 0
        )

        val notification: Notification = NotificationCompat.Builder(this, channelId)
            .setContentTitle("AGAP Telemetry Active")
            .setContentText("Transmitting field position to command center...")
            .setSmallIcon(android.R.drawable.stat_sys_upload)
            .setContentIntent(pendingIntent)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setOngoing(true)
            .build()

        startForeground(2001, notification)
    }
}
