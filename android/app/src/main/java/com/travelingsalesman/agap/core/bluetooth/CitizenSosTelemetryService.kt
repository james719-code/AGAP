package com.travelingsalesman.agap.core.bluetooth

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
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
import com.travelingsalesman.agap.core.network.AgapApiService
import com.travelingsalesman.agap.core.network.NetworkMonitor
import com.travelingsalesman.agap.data.local.AuthTokenManager
import com.travelingsalesman.agap.data.remote.dto.LocationUpdatePayload
import com.travelingsalesman.agap.domain.model.LocationSource
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

import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import androidx.core.content.ContextCompat

@AndroidEntryPoint
class CitizenSosTelemetryService : Service() {

    @Inject
    lateinit var apiService: AgapApiService

    @Inject
    lateinit var networkMonitor: NetworkMonitor

    @Inject
    lateinit var relayScheduler: BleRelayScheduler

    @Inject
    lateinit var authTokenManager: AuthTokenManager

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val locationQueue = Channel<Location>(capacity = Channel.CONFLATED)

    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private lateinit var locationCallback: LocationCallback

    @Volatile
    private var activeIncidentId: String? = null

    @Volatile
    private var activeIncidentNumericId: Long? = null

    @Volatile
    private var sessionTokenBytes: ByteArray? = null

    private fun getOrCreateSessionToken(incidentId: String): ByteArray {
        val existing = sessionTokenBytes
        if (existing != null) return existing

        return try {
            val md = java.security.MessageDigest.getInstance("SHA-256")
            val hash = md.digest("SOS_SESSION_$incidentId".toByteArray(Charsets.UTF_8))
            val token = hash.copyOf(6)
            sessionTokenBytes = token
            token
        } catch (_: Exception) {
            val fallback = BleMeshPacketCodec.generateTransportToken()
            sessionTokenBytes = fallback
            fallback
        }
    }

    override fun onCreate() {
        super.onCreate()
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)

        locationCallback = object : LocationCallback() {
            override fun onLocationResult(result: LocationResult) {
                result.lastLocation?.let { location ->
                    locationQueue.trySend(location)
                }
            }
        }

        serviceScope.launch {
            for (location in locationQueue) {
                if (!isActive) break
                processLocationUpdate(location)
            }
        }

        startForegroundServiceNotification()
        startLocationUpdates()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val action = intent?.action
        if (action == ACTION_STOP_SOS_TRACKING) {
            Timber.i("CitizenSosTelemetryService: received stop intent, stopping tracking service")
            sessionTokenBytes = null
            stopSelf()
            return START_NOT_STICKY
        }

        intent?.getStringExtra(EXTRA_INCIDENT_ID)?.let { id ->
            if (activeIncidentId != id) {
                activeIncidentId = id
                activeIncidentNumericId = id.toLongOrNull()
                sessionTokenBytes = null
            }
        }

        return START_STICKY
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
            Timber.i("CitizenSosTelemetryService: Started high-accuracy location tracking for active SOS")
        } catch (unlikely: SecurityException) {
            Timber.e(unlikely, "CitizenSosTelemetryService: Location permission unavailable")
        }
    }

    private suspend fun processLocationUpdate(location: Location) {
        val incidentIdStr = activeIncidentId ?: return
        val isOnline = networkMonitor.isOnline.firstOrNull() ?: false

        if (isOnline && activeIncidentNumericId != null) {
            try {
                val numId = activeIncidentNumericId!!
                val response = apiService.updateIncidentLocation(numId, LocationUpdatePayload(
                    latitude = location.latitude,
                    longitude = location.longitude,
                    altitude = location.altitude,
                    gpsAccuracy = location.accuracy.toDouble()
                ))
                if (response.isSuccessful) {
                    Timber.d("CitizenSosTelemetryService: Uploaded online location update for incident #%d (%.5f, %.5f)", numId, location.latitude, location.longitude)
                    return
                }
            } catch (e: Exception) {
                Timber.w(e, "CitizenSosTelemetryService: Online location update failed, falling back to BLE mesh broadcast")
            }
        }

        // Offline / Mesh fallback location broadcast
        broadcastMeshLocationPacket(incidentIdStr, location)
    }

    private suspend fun broadcastMeshLocationPacket(incidentId: String, location: Location) {
        try {
            val deviceId = authTokenManager.deviceId.firstOrNull() ?: "citizen-unknown"
            val tokenBytes = getOrCreateSessionToken(incidentId)
            val timeSlotInSeconds = ((System.currentTimeMillis() / 1000L) % 65536L).toInt()
            val emergencyBytes = BleMeshPacketCodec.encodeEmergencyPacket(
                packetType = BleMeshPacketType.SOS,
                emergencyType = "LOCATION_UPDATE",
                immediateDanger = true,
                subjectRelation = "SELF",
                hopCount = 0,
                locationSource = LocationSource.GPS,
                transportToken = tokenBytes,
                latitude = location.latitude,
                longitude = location.longitude,
                horizontalAccuracyMeters = location.accuracy.toInt().coerceIn(0, 255),
                creationTimeSlot = timeSlotInSeconds,
                originDeviceId = deviceId
            )

            BleMeshPacketCodec.decode(emergencyBytes)?.let { packet ->
                relayScheduler.enqueueForRelay(packet, emergencyBytes, isOriginLocal = true)
                Timber.d("CitizenSosTelemetryService: Enqueued mesh location update (%.5f, %.5f)", location.latitude, location.longitude)
            }
        } catch (e: Exception) {
            Timber.e(e, "CitizenSosTelemetryService: Failed to encode/enqueue BLE location update")
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        locationQueue.close()
        try {
            fusedLocationClient.removeLocationUpdates(locationCallback)
        } catch (_: Exception) {}
        serviceScope.cancel()
        Timber.i("CitizenSosTelemetryService: Destroyed SOS tracking service")
        super.onDestroy()
    }

    private fun startForegroundServiceNotification() {
        val channelId = "agap_citizen_sos_channel"
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                channelId,
                "AGAP SOS Tracking",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Notifies when real-time SOS location tracking is active for emergency responders"
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
            .setContentTitle("🚨 AGAP SOS Live Tracking Active")
            .setContentText("Sharing live GPS position with emergency responders")
            .setSmallIcon(android.R.drawable.stat_sys_upload)
            .setContentIntent(pendingIntent)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .build()

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                startForeground(
                    CITIZEN_SOS_NOTIFICATION_ID,
                    notification,
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION
                )
            } else {
                startForeground(CITIZEN_SOS_NOTIFICATION_ID, notification)
            }
        } catch (e: Exception) {
            Timber.e(e, "CitizenSosTelemetryService: Unable to start foreground notification due to missing permission or FGS restriction")
            stopSelf()
        }
    }

    companion object {
        const val CITIZEN_SOS_NOTIFICATION_ID = 2002
        const val LOCATION_INTERVAL_MS = 5_000L
        const val LOCATION_MIN_INTERVAL_MS = 3_000L
        const val LOCATION_MIN_DISTANCE_METERS = 3f

        const val EXTRA_INCIDENT_ID = "extra_incident_id"
        const val ACTION_START_SOS_TRACKING = "com.travelingsalesman.agap.action.START_SOS_TRACKING"
        const val ACTION_STOP_SOS_TRACKING = "com.travelingsalesman.agap.action.STOP_SOS_TRACKING"

        fun startTracking(context: Context, incidentId: String?) {
            if (com.travelingsalesman.agap.BuildConfig.OFFLINE_TEST) return
            val hasCoarse = ContextCompat.checkSelfPermission(context, android.Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
            val hasFine = ContextCompat.checkSelfPermission(context, android.Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
            if (!hasCoarse && !hasFine) {
                Timber.w("CitizenSosTelemetryService: Location permission not granted. Deferring tracking start.")
                return
            }

            val intent = Intent(context, CitizenSosTelemetryService::class.java).apply {
                action = ACTION_START_SOS_TRACKING
                incidentId?.let { putExtra(EXTRA_INCIDENT_ID, it) }
            }
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    context.startForegroundService(intent)
                } else {
                    context.startService(intent)
                }
            } catch (e: Exception) {
                Timber.e(e, "CitizenSosTelemetryService: Failed to start foreground tracking service")
            }
        }

        fun stopTracking(context: Context) {
            if (com.travelingsalesman.agap.BuildConfig.OFFLINE_TEST) return
            val intent = Intent(context, CitizenSosTelemetryService::class.java).apply {
                action = ACTION_STOP_SOS_TRACKING
            }
            try {
                context.startService(intent)
            } catch (e: Exception) {
                Timber.e(e, "CitizenSosTelemetryService: Failed to stop tracking service")
            }
        }
    }
}

