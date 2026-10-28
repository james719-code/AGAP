package com.travelingsalesman.agap.location

import android.annotation.SuppressLint
import android.content.Context
import android.location.Location
import android.os.Looper
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.Priority
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.firstOrNull
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class LocationRepository @Inject constructor(
    private val fusedLocationClient: FusedLocationProviderClient,
    @ApplicationContext private val context: Context
) {
    private val _currentLocation = MutableStateFlow<Location?>(null)
    val currentLocation: StateFlow<Location?> = _currentLocation.asStateFlow()

    private fun offlineTestLocation() = Location("offline-test").apply {
        latitude = 13.1391
        longitude = 123.7438
        accuracy = 5f
        time = System.currentTimeMillis()
        elapsedRealtimeNanos = android.os.SystemClock.elapsedRealtimeNanos()
    }

    @SuppressLint("MissingPermission")
    fun getLocationUpdates(intervalMs: Long = 5000L): Flow<Location> = callbackFlow {
        if (com.travelingsalesman.agap.BuildConfig.OFFLINE_TEST) {
            val sample = offlineTestLocation()
            _currentLocation.value = sample
            trySend(sample)
            awaitClose { }
            return@callbackFlow
        }
        try {
            fusedLocationClient.lastLocation.addOnSuccessListener { loc ->
                if (loc != null) {
                    _currentLocation.value = loc
                    trySend(loc)
                }
            }
        } catch (_: Exception) {}

        val locationRequest = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, intervalMs)
            .setMinUpdateIntervalMillis(intervalMs / 2)
            .build()

        val callback = object : LocationCallback() {
            override fun onLocationResult(result: LocationResult) {
                for (location in result.locations) {
                    _currentLocation.value = location
                    trySend(location)
                }
            }
        }

        fusedLocationClient.requestLocationUpdates(
            locationRequest,
            callback,
            Looper.getMainLooper()
        )

        awaitClose {
            fusedLocationClient.removeLocationUpdates(callback)
        }
    }

    @SuppressLint("MissingPermission")
    suspend fun getLastKnownLocation(): Location? = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
        if (com.travelingsalesman.agap.BuildConfig.OFFLINE_TEST) return@withContext offlineTestLocation()
        try {
            val cached = currentLocation.value
            if (cached != null && (System.currentTimeMillis() - cached.time) < 15000L) {
                return@withContext cached
            }
            com.google.android.gms.tasks.Tasks.await(fusedLocationClient.lastLocation) ?: cached
        } catch (_: Exception) {
            currentLocation.value
        }
    }

    @SuppressLint("MissingPermission")
    suspend fun getBoundedLocationFix(timeoutMs: Long = 3000L): EmergencyLocationFix {
        val cached = getLastKnownLocation()
        if (cached != null && cached.accuracy <= 30f && (System.currentTimeMillis() - cached.time) <= 10000L) {
            return EmergencyLocationFix(
                source = com.travelingsalesman.agap.domain.model.LocationSource.GPS,
                latitude = cached.latitude,
                longitude = cached.longitude,
                accuracyMeters = cached.accuracy,
                timestampMillis = cached.time
            )
        }

        return try {
            val freshLoc = kotlinx.coroutines.withTimeoutOrNull(timeoutMs) {
                getLocationUpdates(1000L).firstOrNull()
            } ?: cached

            if (freshLoc != null) {
                EmergencyLocationFix(
                    source = com.travelingsalesman.agap.domain.model.LocationSource.GPS,
                    latitude = freshLoc.latitude,
                    longitude = freshLoc.longitude,
                    accuracyMeters = freshLoc.accuracy,
                    timestampMillis = freshLoc.time
                )
            } else {
                EmergencyLocationFix(
                    source = com.travelingsalesman.agap.domain.model.LocationSource.UNKNOWN,
                    latitude = null,
                    longitude = null,
                    accuracyMeters = null,
                    timestampMillis = System.currentTimeMillis()
                )
            }
        } catch (_: Exception) {
            EmergencyLocationFix(
                source = com.travelingsalesman.agap.domain.model.LocationSource.UNKNOWN,
                latitude = null,
                longitude = null,
                accuracyMeters = null,
                timestampMillis = System.currentTimeMillis()
            )
        }
    }
}

