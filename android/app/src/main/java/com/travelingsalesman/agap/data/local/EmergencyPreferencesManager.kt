package com.travelingsalesman.agap.data.local

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

private val Context.preferencesDataStore: DataStore<Preferences> by preferencesDataStore(name = "emergency_preferences")

data class EmergencyPreferences(
    val autoSendGps: Boolean = true,
    val enableBleFallback: Boolean = true,
    val vibrationAlert: Boolean = true,
    val silentMode: Boolean = false,
    val offlineSharing: Boolean = true
)

@Singleton
class EmergencyPreferencesManager @Inject constructor(
    @ApplicationContext private val context: Context
) {
    companion object {
        private val KEY_AUTO_SEND_GPS = booleanPreferencesKey("auto_send_gps")
        private val KEY_ENABLE_BLE_FALLBACK = booleanPreferencesKey("enable_ble_fallback")
        private val KEY_VIBRATION_ALERT = booleanPreferencesKey("vibration_alert")
        private val KEY_SILENT_MODE = booleanPreferencesKey("silent_mode")
        private val KEY_OFFLINE_SHARING = booleanPreferencesKey("offline_sharing")
    }

    val preferences: Flow<EmergencyPreferences> = context.preferencesDataStore.data.map { prefs ->
        EmergencyPreferences(
            autoSendGps = prefs[KEY_AUTO_SEND_GPS] ?: true,
            enableBleFallback = prefs[KEY_ENABLE_BLE_FALLBACK] ?: true,
            vibrationAlert = prefs[KEY_VIBRATION_ALERT] ?: true,
            silentMode = prefs[KEY_SILENT_MODE] ?: false,
            offlineSharing = prefs[KEY_OFFLINE_SHARING] ?: true
        )
    }

    suspend fun updateAutoSendGps(enabled: Boolean) {
        context.preferencesDataStore.edit { it[KEY_AUTO_SEND_GPS] = enabled }
    }

    suspend fun updateBleFallback(enabled: Boolean) {
        context.preferencesDataStore.edit { it[KEY_ENABLE_BLE_FALLBACK] = enabled }
    }

    suspend fun updateVibrationAlert(enabled: Boolean) {
        context.preferencesDataStore.edit { it[KEY_VIBRATION_ALERT] = enabled }
    }

    suspend fun updateSilentMode(enabled: Boolean) {
        context.preferencesDataStore.edit { it[KEY_SILENT_MODE] = enabled }
    }

    suspend fun updateOfflineSharing(enabled: Boolean) {
        context.preferencesDataStore.edit { it[KEY_OFFLINE_SHARING] = enabled }
    }
}
