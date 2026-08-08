package com.travelingsalesman.agap.data.local

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.travelingsalesman.agap.BuildConfig
import com.travelingsalesman.agap.core.bluetooth.BleMeshLog
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

enum class TransmissionMode {
    AUTO,
    FORCE_BLE_MESH,
    FORCE_INTERNET
}

private val Context.serverDataStore: DataStore<Preferences> by preferencesDataStore(name = "server_config")

@Singleton
class ServerConfigManager @Inject constructor(
    @ApplicationContext private val context: Context
) {
    companion object {
        private val KEY_SERVER_URL = stringPreferencesKey("server_url")
        private val KEY_WS_URL = stringPreferencesKey("ws_url")
        private val KEY_TRANSMISSION_MODE = stringPreferencesKey("transmission_mode")

        /**
         * Fast process-local policy mirror for components such as OSMDroid that do
         * not use the app's OkHttp client. It is updated whenever mode is loaded or
         * changed. Network APIs still enforce the policy independently.
         */
        @Volatile
        var isBleOnlyActive: Boolean = false
            private set
    }

    @Volatile
    var currentServerUrl: String? = null
        private set

    @Volatile
    var currentTransmissionMode: TransmissionMode = TransmissionMode.AUTO
        private set

    val serverUrl: Flow<String> = context.serverDataStore.data.map { prefs ->
        val url = prefs[KEY_SERVER_URL] ?: BuildConfig.BASE_URL
        currentServerUrl = url
        url
    }

    val wsUrl: Flow<String> = context.serverDataStore.data.map { prefs ->
        prefs[KEY_WS_URL] ?: BuildConfig.WS_URL
    }

    val transmissionMode: Flow<TransmissionMode> = context.serverDataStore.data.map { prefs ->
        val mode = parseTransmissionMode(prefs[KEY_TRANSMISSION_MODE])
        currentTransmissionMode = mode
        isBleOnlyActive = mode == TransmissionMode.FORCE_BLE_MESH
        mode
    }

    suspend fun getTransmissionMode(): TransmissionMode {
        val mode = transmissionMode.first()
        currentTransmissionMode = mode
        return mode
    }

    suspend fun isInternetAllowed(): Boolean {
        return getTransmissionMode() != TransmissionMode.FORCE_BLE_MESH
    }

    suspend fun setServerUrl(url: String) {
        val cleanUrl = url.trim().removeSuffix("/")
        val apiPath = if (cleanUrl.endsWith("/api/v1")) cleanUrl else "$cleanUrl/api/v1"
        val wsPath = apiPath
            .replace("http://", "ws://")
            .replace("https://", "wss://")
            .replace("/api/v1", "/ws")

        currentServerUrl = apiPath
        context.serverDataStore.edit { prefs ->
            prefs[KEY_SERVER_URL] = apiPath
            prefs[KEY_WS_URL] = wsPath
        }
    }

    suspend fun setTransmissionMode(mode: TransmissionMode) {
        // Update the in-memory gate first so a request cannot slip through between
        // the user's tap and DataStore completing the write.
        val previous = currentTransmissionMode
        currentTransmissionMode = mode
        isBleOnlyActive = mode == TransmissionMode.FORCE_BLE_MESH
        BleMeshLog.i(
            "TRANSPORT_MODE_CHANGED",
            "from" to previous.name,
            "to" to mode.name,
            "internetAllowed" to (mode != TransmissionMode.FORCE_BLE_MESH)
        )
        context.serverDataStore.edit { prefs ->
            prefs[KEY_TRANSMISSION_MODE] = mode.name
        }
    }

    private fun parseTransmissionMode(raw: String?): TransmissionMode {
        return try {
            TransmissionMode.valueOf(raw ?: TransmissionMode.AUTO.name)
        } catch (_: Exception) {
            TransmissionMode.AUTO
        }
    }
}
