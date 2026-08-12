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
    var currentTransmissionMode: TransmissionMode = TransmissionMode.AUTO
        private set

    @Volatile
    var isTransmissionModeLoaded: Boolean = false
        private set

    val transmissionMode: Flow<TransmissionMode> = context.serverDataStore.data.map { prefs ->
        val mode = parseTransmissionMode(prefs[KEY_TRANSMISSION_MODE])
        currentTransmissionMode = mode
        isTransmissionModeLoaded = true
        isBleOnlyActive = mode == TransmissionMode.FORCE_BLE_MESH
        mode
    }

    suspend fun getTransmissionMode(): TransmissionMode {
        if (isTransmissionModeLoaded) {
            return currentTransmissionMode
        }
        val mode = transmissionMode.first()
        currentTransmissionMode = mode
        isTransmissionModeLoaded = true
        return mode
    }

    fun getServerUrl(): String {
        return BuildConfig.BASE_URL
    }

    fun getWsUrl(): String {
        return BuildConfig.WS_URL
    }

    suspend fun isInternetAllowed(): Boolean {
        return getTransmissionMode() != TransmissionMode.FORCE_BLE_MESH
    }

    suspend fun resetBleOnlyToAuto(reason: String) {
        if (getTransmissionMode() == TransmissionMode.FORCE_BLE_MESH) {
            BleMeshLog.i(
                "TRANSPORT_BLE_ONLY_RESET_FOR_AUTH",
                "reason" to reason
            )
            setTransmissionMode(TransmissionMode.AUTO)
        }
    }

    suspend fun setTransmissionMode(mode: TransmissionMode) {
        // Update the in-memory gate first so a request cannot slip through between
        // the user's tap and DataStore completing the write.
        val previous = currentTransmissionMode
        currentTransmissionMode = mode
        isTransmissionModeLoaded = true
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
