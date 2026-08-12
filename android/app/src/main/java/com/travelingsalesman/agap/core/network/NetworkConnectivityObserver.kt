package com.travelingsalesman.agap.core.network

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

interface ConnectivityObserver {
    fun observe(): Flow<Status>

    enum class Status {
        Available, Unavailable, Losing, Lost
    }
}

/**
 * Compatibility facade for existing background-sync callers.
 *
 * NetworkMonitor owns the single ConnectivityManager callback so UI, workers,
 * and this observer all agree on a validated default Internet route.
 */
@Singleton
class NetworkConnectivityObserver @Inject constructor(
    private val networkMonitor: NetworkMonitor
) : ConnectivityObserver {

    override fun observe(): Flow<ConnectivityObserver.Status> {
        return networkMonitor.isOnline
            .map { online ->
                if (online) {
                    ConnectivityObserver.Status.Available
                } else {
                    ConnectivityObserver.Status.Unavailable
                }
            }
            .distinctUntilChanged()
    }

    fun isNetworkAvailable(): Boolean = networkMonitor.isOnlineNow()
}
