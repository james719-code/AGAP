package com.travelingsalesman.agap.data.local

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.travelingsalesman.agap.data.remote.dto.UserDto
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

import androidx.datastore.preferences.core.booleanPreferencesKey

private val Context.authDataStore: DataStore<Preferences> by preferencesDataStore(name = "agap_auth_prefs")

@Singleton
class AuthTokenManager @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val AUTH_TOKEN_KEY = stringPreferencesKey("auth_token")
    private val USER_ID_KEY = longPreferencesKey("user_id")
    private val USERNAME_KEY = stringPreferencesKey("username")
    private val USER_ROLE_KEY = stringPreferencesKey("user_role")
    private val DEVICE_ID_KEY = stringPreferencesKey("device_id")
    private val AGENCY_ID_KEY = longPreferencesKey("agency_id")
    private val IS_DARK_MODE_KEY = booleanPreferencesKey("is_dark_mode")

    val authToken: Flow<String?> = context.authDataStore.data.map { prefs ->
        prefs[AUTH_TOKEN_KEY]
    }

    val userRole: Flow<String?> = context.authDataStore.data.map { prefs ->
        prefs[USER_ROLE_KEY]
    }

    val username: Flow<String?> = context.authDataStore.data.map { prefs ->
        prefs[USERNAME_KEY]
    }

    val userId: Flow<Long?> = context.authDataStore.data.map { prefs ->
        prefs[USER_ID_KEY]
    }

    val agencyId: Flow<Long?> = context.authDataStore.data.map { prefs ->
        prefs[AGENCY_ID_KEY]
    }

    val isDarkMode: Flow<Boolean> = context.authDataStore.data.map { prefs ->
        prefs[IS_DARK_MODE_KEY] ?: true // Default to true (Dark mode) matching mockups
    }

    suspend fun setDarkMode(enabled: Boolean) {
        context.authDataStore.edit { prefs ->
            prefs[IS_DARK_MODE_KEY] = enabled
        }
    }

    suspend fun saveAuthSession(token: String, user: UserDto) {
        context.authDataStore.edit { prefs ->
            prefs[AUTH_TOKEN_KEY] = token
            prefs[USER_ID_KEY] = user.id
            prefs[USERNAME_KEY] = user.username
            prefs[USER_ROLE_KEY] = user.role
            user.deviceId?.let { prefs[DEVICE_ID_KEY] = it }
            user.agencyId?.let { prefs[AGENCY_ID_KEY] = it } ?: prefs.remove(AGENCY_ID_KEY)
        }
    }

    suspend fun clearAuthSession() {
        context.authDataStore.edit { prefs ->
            val darkModeSetting = prefs[IS_DARK_MODE_KEY]
            prefs.clear()
            darkModeSetting?.let { prefs[IS_DARK_MODE_KEY] = it }
        }
    }
}

