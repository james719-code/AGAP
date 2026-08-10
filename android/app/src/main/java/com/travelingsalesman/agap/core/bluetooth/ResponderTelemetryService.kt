package com.travelingsalesman.agap.core.bluetooth

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.pm.ServiceInfo
import android.content.Context
import android.content.Intent
import android.location.Location
import android.os.Build
import android.os.IBinder
import android.os.Looper
import androidx.core.app.NotificationCompat
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.travelingsalesman.agap.MainActivity
import com.travelingsalesman.agap.core.util.Result
import com.travelingsalesman.agap.data.repository.AuthRepository
import com.travelingsalesman.agap.data.repository.ResponderRepository
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject

@AndroidEntryPoint
class ResponderTelemetryService : Service() {

    @Inject
    lateinit var responderRepository: ResponderRepository

    @Inject
    lateinit var authRepository: AuthRepository

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val locationUploadQueue = Channel<Location>(capacity = Channel.CONFLATED)

    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private lateinit var locationCallback: LocationCallback

    @Volatile
    private var cachedUnitId: Long? = null

    @Volatile
    private var cachedUnitStatus: String = DEFAULT_UNIT_STATUS

    @Volatile
    private var unitContextExpiresAtMs: Long = 0L

    override fun onCreate() {
        super.onCreate()
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)

        locationCallback = object : LocationCallback() {
            override fun onLocationResult(locationResult: LocationResult) {
                locationResult.lastLocation?.let { location ->
                    // CONFLATED means slow/flaky networking can never build an unbounded
                    // backlog of stale GPS points. The newest position replaces the old one.
                    locationUploadQueue.trySend(location)
                }
            }
        }

        serviceScope.launch {
            for (location in locationUploadQueue) {
                if (!isActive) break
                uploadLatestLocation(location)
            }
        }

        startForegroundServiceNotification()
        startLocationUpdates()
    }

    private fun startLocationUpdates() {
        val locationRequest = LocationRequest.Builder(
            Priority.PRIORITY_HIGH_ACCURACY,
            LOCATION_INTERVAL_MS
        )
            .setMinUpdateIntervalMillis(LOCATION_MIN_INTERVAL_MS)
            .setMinUpdateDistanceMeters(LOCATION_MIN_DISTANCE_METERS)
            .build()

        try {
            fusedLocationClient.requestLocationUpdates(
                locationRequest,
                locationCallback,
                Looper.getMainLooper()
            )
        } catch (unlikely: SecurityException) {
            Timber.e(unlikely, "Telemetry: location permission unavailable")
        }
    }

    private suspend fun uploadLatestLocation(location: Location) {
        val role = authRepository.userRole.firstOrNull()
        if (role != "RESPONDER" && role != "ADMIN") {
            Timber.d("Telemetry: current account no longer requires responder telemetry; stopping")
            stopSelf()
            return
        }

        val (unitId, status) = resolveUnitContext() ?: return
        when (
            val result = responderRepository.updateLocation(
                unitId,
                location.latitude,
                location.longitude,
                status
            )
        ) {
            is Result.Success -> {
                Timber.d(
                    "Telemetry: updated unit #%d to %.5f, %.5f",
                    unitId,
                    location.latitude,
                    location.longitude
                )
            }

            is Result.Error -> {
                Timber.w("Telemetry: location upload failed: %s", result.message)
            }

            else -> Unit
        }
    }

    /**
     * Unit identity/status changes far less often than GPS location. Cache it so a 15-second
     * location cadence does not perform getMyResponderUnit() before every upload (the previous
     * implementation performed it twice per location update).
     */
    private suspend fun resolveUnitContext(): Pair<Long, String>? {
        val now = System.currentTimeMillis()
        val cachedId = cachedUnitId
        if (cachedId != null && now < unitContextExpiresAtMs) {
            return cachedId to cachedUnitStatus
        }

        return when (val unit = responderRepository.getMyResponderUnit()) {
            is Result.Success -> {
                cachedUnitId = unit.data.id
                cachedUnitStatus = unit.data.status.ifBlank { DEFAULT_UNIT_STATUS }
                unitContextExpiresAtMs = now + UNIT_CONTEXT_CACHE_MS
                unit.data.id to cachedUnitStatus
            }

            is Result.Error -> {
                // Keep telemetry useful during temporary API degradation. A logged-in responder
                // still has a durable user id that can be used as the legacy fallback identifier.
                val fallbackId = cachedUnitId ?: authRepository.userId.firstOrNull()
                if (fallbackId == null) {
                    Timber.w("Telemetry: unable to resolve responder unit: %s", unit.message)
                    null
                } else {
                    cachedUnitId = fallbackId
                    unitContextExpiresAtMs = now + UNIT_CONTEXT_FAILURE_CACHE_MS
                    fallbackId to cachedUnitStatus
                }
            }

            else -> null
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int = START_STICKY

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        locationUploadQueue.close()
        fusedLocationClient.removeLocationUpdates(locationCallback)
        serviceScope.cancel()
        super.onDestroy()
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
            this,
            0,
            Intent(this, MainActivity::class.java),
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                PendingIntent.FLAG_IMMUTABLE
            } else {
                0
            }
        )

        val notification: Notification = NotificationCompat.Builder(this, channelId)
            .setContentTitle("AGAP Telemetry Active")
            .setContentText("Sharing field position with command center")
            .setSmallIcon(android.R.drawable.stat_sys_upload)
            .setContentIntent(pendingIntent)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setOnlyAlertOnce(true)
            .setOngoing(true)
            .build()

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                startForeground(
                    TELEMETRY_NOTIFICATION_ID,
                    notification,
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION
                )
            } else {
                startForeground(TELEMETRY_NOTIFICATION_ID, notification)
            }
        } catch (e: Exception) {
            Timber.e(e, "ResponderTelemetryService: SecurityException or error starting foreground service notification")
            stopSelf()
        }
    }

    private companion object {
        const val TELEMETRY_NOTIFICATION_ID = 2001
        const val LOCATION_INTERVAL_MS = 15_000L
        const val LOCATION_MIN_INTERVAL_MS = 10_000L
        const val LOCATION_MIN_DISTANCE_METERS = 5f
        const val UNIT_CONTEXT_CACHE_MS = 2 * 60_000L
        const val UNIT_CONTEXT_FAILURE_CACHE_MS = 30_000L
        const val DEFAULT_UNIT_STATUS = "IDLE"
    }
}

