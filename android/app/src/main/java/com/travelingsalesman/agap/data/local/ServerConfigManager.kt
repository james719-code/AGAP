package com.travelingsalesman.agap.data.local

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.travelingsalesman.agap.BuildConfig
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
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
    }

    val serverUrl: Flow<String> = context.serverDataStore.data.map { prefs ->
        prefs[KEY_SERVER_URL] ?: BuildConfig.BASE_URL
    }

    val wsUrl: Flow<String> = context.serverDataStore.data.map { prefs ->
        prefs[KEY_WS_URL] ?: BuildConfig.WS_URL
    }

    val transmissionMode: Flow<TransmissionMode> = context.serverDataStore.data.map { prefs ->
        val raw = prefs[KEY_TRANSMISSION_MODE] ?: TransmissionMode.AUTO.name
        try {
            TransmissionMode.valueOf(raw)
        } catch (e: Exception) {
            TransmissionMode.AUTO
        }
    }

    suspend fun setServerUrl(url: String) {
        val cleanUrl = url.trim().removeSuffix("/")
        val apiPath = if (cleanUrl.endsWith("/api/v1")) cleanUrl else "$cleanUrl/api/v1"
        val wsPath = apiPath.replace("http://", "ws://").replace("https://", "wss://").replace("/api/v1", "/ws")

        context.serverDataStore.edit { prefs ->
            prefs[KEY_SERVER_URL] = apiPath
            prefs[KEY_WS_URL] = wsPath
        }
    }

    suspend fun setTransmissionMode(mode: TransmissionMode) {
        context.serverDataStore.edit { prefs ->
            prefs[KEY_TRANSMISSION_MODE] = mode.name
        }
    }
}
