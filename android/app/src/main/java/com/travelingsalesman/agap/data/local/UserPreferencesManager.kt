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

private val Context.userPrefsDataStore: DataStore<Preferences> by preferencesDataStore(name = "agap_user_prefs")

@Singleton
class UserPreferencesManager @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val AUTO_SEND_GPS_KEY = booleanPreferencesKey("auto_send_gps")
    private val ENABLE_BLE_FALLBACK_KEY = booleanPreferencesKey("enable_ble_fallback")
    private val VIBRATION_ALERT_KEY = booleanPreferencesKey("vibration_alert")
    private val OFFLINE_SHARING_KEY = booleanPreferencesKey("offline_sharing")

    val autoSendGps: Flow<Boolean> = context.userPrefsDataStore.data.map { prefs ->
        prefs[AUTO_SEND_GPS_KEY] ?: true
    }

    val enableBleFallback: Flow<Boolean> = context.userPrefsDataStore.data.map { prefs ->
        prefs[ENABLE_BLE_FALLBACK_KEY] ?: true
    }

    val vibrationAlert: Flow<Boolean> = context.userPrefsDataStore.data.map { prefs ->
        prefs[VIBRATION_ALERT_KEY] ?: true
    }

    val offlineSharing: Flow<Boolean> = context.userPrefsDataStore.data.map { prefs ->
        prefs[OFFLINE_SHARING_KEY] ?: true
    }

    suspend fun setAutoSendGps(enabled: Boolean) {
        context.userPrefsDataStore.edit { prefs ->
            prefs[AUTO_SEND_GPS_KEY] = enabled
        }
    }

    suspend fun setEnableBleFallback(enabled: Boolean) {
        context.userPrefsDataStore.edit { prefs ->
            prefs[ENABLE_BLE_FALLBACK_KEY] = enabled
        }
    }

    suspend fun setVibrationAlert(enabled: Boolean) {
        context.userPrefsDataStore.edit { prefs ->
            prefs[VIBRATION_ALERT_KEY] = enabled
        }
    }

    suspend fun setOfflineSharing(enabled: Boolean) {
        context.userPrefsDataStore.edit { prefs ->
            prefs[OFFLINE_SHARING_KEY] = enabled
        }
    }
}
