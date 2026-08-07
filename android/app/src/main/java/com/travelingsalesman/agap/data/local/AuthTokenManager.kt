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
    private val USER_DISPLAY_ID_KEY = stringPreferencesKey("user_display_id")
    private val USERNAME_KEY = stringPreferencesKey("username")
    private val USER_ROLE_KEY = stringPreferencesKey("user_role")
    private val DEVICE_ID_KEY = stringPreferencesKey("device_id")
    private val AGENCY_ID_KEY = longPreferencesKey("agency_id")
    private val IS_DARK_MODE_KEY = booleanPreferencesKey("is_dark_mode")
    private val FULL_NAME_KEY = stringPreferencesKey("full_name")
    private val EMAIL_KEY = stringPreferencesKey("email")
    private val PHONE_KEY = stringPreferencesKey("phone")
    private val MEDICAL_INFO_KEY = stringPreferencesKey("medical_info")
    private val NATIONAL_ID_KEY = stringPreferencesKey("national_id")
    private val IS_VERIFIED_KEY = booleanPreferencesKey("is_verified")

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

    val userDisplayId: Flow<String?> = context.authDataStore.data.map { prefs ->
        prefs[USER_DISPLAY_ID_KEY] ?: prefs[USER_ID_KEY]?.let { id ->
            val role = prefs[USER_ROLE_KEY] ?: "CITIZEN"
            com.travelingsalesman.agap.core.util.UserIdFormatter.formatUserId(role, id)
        }
    }

    val agencyId: Flow<Long?> = context.authDataStore.data.map { prefs ->
        prefs[AGENCY_ID_KEY]
    }

    val isDarkMode: Flow<Boolean> = context.authDataStore.data.map { prefs ->
        prefs[IS_DARK_MODE_KEY] ?: true // Default to true (Dark mode) matching mockups
    }

    val userFullName: Flow<String?> = context.authDataStore.data.map { prefs -> prefs[FULL_NAME_KEY] }
    val userEmail: Flow<String?> = context.authDataStore.data.map { prefs -> prefs[EMAIL_KEY] }
    val userPhone: Flow<String?> = context.authDataStore.data.map { prefs -> prefs[PHONE_KEY] }
    val userMedicalInfo: Flow<String?> = context.authDataStore.data.map { prefs -> prefs[MEDICAL_INFO_KEY] }
    val userNationalId: Flow<String?> = context.authDataStore.data.map { prefs -> prefs[NATIONAL_ID_KEY] }
    val userIsVerified: Flow<Boolean> = context.authDataStore.data.map { prefs -> prefs[IS_VERIFIED_KEY] ?: false }

    suspend fun setDarkMode(enabled: Boolean) {
        context.authDataStore.edit { prefs ->
            prefs[IS_DARK_MODE_KEY] = enabled
        }
    }

    suspend fun saveAuthSession(token: String, user: UserDto) {
        context.authDataStore.edit { prefs ->
            prefs[AUTH_TOKEN_KEY] = token
            prefs[USER_ID_KEY] = user.id
            prefs[USER_DISPLAY_ID_KEY] = user.displayId
            prefs[USERNAME_KEY] = user.username
            prefs[USER_ROLE_KEY] = user.role
            user.deviceId?.let { prefs[DEVICE_ID_KEY] = it }
            user.agencyId?.let { prefs[AGENCY_ID_KEY] = it } ?: prefs.remove(AGENCY_ID_KEY)
            user.name?.let { prefs[FULL_NAME_KEY] = it }
            user.email?.let { prefs[EMAIL_KEY] = it }
            user.phoneNumber?.let { prefs[PHONE_KEY] = it }
            user.medicalInfo?.let { prefs[MEDICAL_INFO_KEY] = it }
            user.nationalId?.let { prefs[NATIONAL_ID_KEY] = it }
            prefs[IS_VERIFIED_KEY] = user.isVerified
        }
    }

    suspend fun saveUserProfile(user: UserDto) {
        context.authDataStore.edit { prefs ->
            prefs[USER_ID_KEY] = user.id
            prefs[USER_DISPLAY_ID_KEY] = user.displayId
            prefs[USERNAME_KEY] = user.username
            prefs[USER_ROLE_KEY] = user.role
            user.name?.let { prefs[FULL_NAME_KEY] = it }
            user.email?.let { prefs[EMAIL_KEY] = it }
            user.phoneNumber?.let { prefs[PHONE_KEY] = it }
            user.medicalInfo?.let { prefs[MEDICAL_INFO_KEY] = it }
            user.nationalId?.let { prefs[NATIONAL_ID_KEY] = it }
            prefs[IS_VERIFIED_KEY] = user.isVerified
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

