package com.travelingsalesman.agap.core.network

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Process-wide view of the device's default Internet route.
 *
 * A network advertising [NetworkCapabilities.NET_CAPABILITY_INTERNET] is not
 * necessarily usable yet (for example, a captive portal can advertise it).
 * AGAP treats a route as online only after Android has also marked the active
 * default network as [NetworkCapabilities.NET_CAPABILITY_VALIDATED].
 */
@Singleton
class NetworkMonitor @Inject constructor(
    @ApplicationContext context: Context
) {

    private val connectivityManager =
        context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager

    @Volatile
    private var defaultNetwork: Network? = null

    private val _isOnline = MutableStateFlow(false)

    /**
     * Hot, immediately available connectivity state shared by all callers.
     *
     * This remains assignable to callers that previously consumed the public
     * value as a [kotlinx.coroutines.flow.Flow], while additionally exposing
     * [StateFlow.value] for synchronous startup decisions.
     */
    val isOnline: StateFlow<Boolean> = _isOnline.asStateFlow()

    /** Offline test requests are answered in-process, independently of Internet access. */
    fun canAccessDataService(): Boolean = com.travelingsalesman.agap.BuildConfig.OFFLINE_TEST || isOnlineNow()

    private val callback = object : ConnectivityManager.NetworkCallback() {
        override fun onAvailable(network: Network) {
            // registerDefaultNetworkCallback tracks only the system-selected
            // default route. Do not report online until the following
            // onCapabilitiesChanged callback confirms validation.
            val changedDefault = defaultNetwork != network
            defaultNetwork = network
            if (changedDefault) {
                _isOnline.value = false
            }
        }

        override fun onCapabilitiesChanged(
            network: Network,
            networkCapabilities: NetworkCapabilities
        ) {
            // Avoid consulting ConnectivityManager from a callback: Android
            // documents that those synchronous reads can race the callback.
            // The callback's capabilities are the ordered, current snapshot.
            if (network == defaultNetwork) {
                _isOnline.value = networkCapabilities.hasValidatedInternet()
            }
        }

        override fun onLost(network: Network) {
            // A non-default network disappearing must not mark the active
            // route offline. This also avoids false offline transitions during
            // Wi-Fi/cellular hand-off after a new default has arrived.
            if (network == defaultNetwork) {
                defaultNetwork = null
                _isOnline.value = false
            }
        }

        override fun onUnavailable() {
            defaultNetwork = null
            _isOnline.value = false
        }
    }

    init {
        isOnlineNow()
        try {
            // minSdk is 24, so this tracks the actual default route rather
            // than every Internet-capable network on the device.
            connectivityManager.registerDefaultNetworkCallback(callback)
        } catch (_: SecurityException) {
            // ACCESS_NETWORK_STATE is declared by the app, but fail safely if
            // a device policy prevents access to connectivity information.
            defaultNetwork = null
            _isOnline.value = false
        }
    }

    /**
     * Synchronously refresh and return the validated state of the current
     * default route. Useful before a network-first request is started.
     */
    fun isOnlineNow(): Boolean {
        return try {
            val activeNetwork = connectivityManager.activeNetwork
            defaultNetwork = activeNetwork
            hasValidatedInternet(activeNetwork).also { online ->
                _isOnline.value = online
            }
        } catch (_: SecurityException) {
            defaultNetwork = null
            _isOnline.value = false
            false
        }
    }

    private fun hasValidatedInternet(network: Network?): Boolean {
        val capabilities = network?.let(connectivityManager::getNetworkCapabilities)
        return capabilities.hasValidatedInternet()
    }

    private fun NetworkCapabilities?.hasValidatedInternet(): Boolean {
        if (com.travelingsalesman.agap.BuildConfig.OFFLINE_TEST) return false
        val capabilities = this ?: return false
        return capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
            capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
    }
}
