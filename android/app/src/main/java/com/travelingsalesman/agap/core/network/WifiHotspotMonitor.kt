package com.travelingsalesman.agap.core.network

import android.content.Context
import android.net.wifi.WifiManager
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import timber.log.Timber
import java.lang.reflect.Method
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class WifiHotspotMonitor @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val _isHotspotActive = MutableStateFlow(false)
    val isHotspotActive: StateFlow<Boolean> = _isHotspotActive.asStateFlow()

    private val scope =
        CoroutineScope(SupervisorJob() + Dispatchers.IO)

    init {
        startMonitoring()
    }

    private fun startMonitoring() {
        scope.launch {
            while (isActive) {
                val active = checkIsHotspotActive()
                if (_isHotspotActive.value != active) {
                    _isHotspotActive.value = active
                }
                delay(HOTSPOT_POLL_INTERVAL_MS)
            }
        }
    }

    fun checkIsHotspotActive(): Boolean {
        return try {
            val wifiManager = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as? WifiManager
                ?: return false
            val method: Method = wifiManager.javaClass.getDeclaredMethod("isWifiApEnabled")
            method.isAccessible = true
            (method.invoke(wifiManager) as? Boolean) ?: false
        } catch (e: Exception) {
            try {
                val wifiManager = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as? WifiManager
                    ?: return false
                val method: Method = wifiManager.javaClass.getDeclaredMethod("getWifiApState")
                method.isAccessible = true
                val state = method.invoke(wifiManager) as? Int
                state == 13 // 13 = WIFI_AP_STATE_ENABLED
            } catch (ex: Exception) {
                Timber.d("WifiHotspotMonitor: reflection failed to query AP state: ${ex.message}")
                false
            }
        }
    }

    companion object {
        // Hidden/reflection-based AP state checks are relatively expensive and
        // the UI does not need sub-second hotspot telemetry.
        private const val HOTSPOT_POLL_INTERVAL_MS = 15_000L
    }

}
