package com.travelingsalesman.agap.data.local

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.travelingsalesman.agap.BuildConfig
import com.travelingsalesman.agap.data.remote.dto.UserDto
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

import androidx.datastore.preferences.core.booleanPreferencesKey

import com.travelingsalesman.agap.core.util.DeviceIdProvider

private val Context.authDataStore: DataStore<Preferences> by preferencesDataStore(name = "agap_auth_prefs")

data class AppSession(
    val isAuthenticated: Boolean,
    val role: String?,
    val isGuest: Boolean
)

@Singleton
class AuthTokenManager internal constructor(
    private val preferences: DataStore<Preferences>,
    private val deviceIdProvider: DeviceIdProvider
) {
    @Inject
    constructor(
        @ApplicationContext context: Context,
        deviceIdProvider: DeviceIdProvider = DeviceIdProvider(context)
    ) : this(
        if (BuildConfig.HOSTED_BACKEND) {
            PreferenceDataStoreFactory.create {
                File(context.noBackupFilesDir, backendStorageName("agap_auth_prefs") + ".preferences_pb")
            }
        } else {
            context.authDataStore
        },
        deviceIdProvider
    )

    private val AUTH_TOKEN_KEY = stringPreferencesKey("auth_token")
    private val REFRESH_TOKEN_KEY = stringPreferencesKey("refresh_token")
    private val sessionMutex = Mutex()
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
    private val ACCOUNT_STATUS_KEY = stringPreferencesKey("account_status")
    private val IS_VERIFIED_KEY = booleanPreferencesKey("is_verified")
    private val IS_GUEST_KEY = booleanPreferencesKey("is_guest")

    // Read these together so navigation never combines a new token with an old role.
    val appSession: Flow<AppSession> = preferences.data.map { prefs ->
        val authenticated = !prefs[AUTH_TOKEN_KEY].isNullOrBlank()
        AppSession(authenticated, prefs[USER_ROLE_KEY], !authenticated && prefs[IS_GUEST_KEY] == true)
    }

    suspend fun enterGuestMode() {
        preferences.edit { prefs ->
            if (prefs[AUTH_TOKEN_KEY].isNullOrBlank()) prefs[IS_GUEST_KEY] = true
        }
    }

    suspend fun leaveGuestMode() {
        preferences.edit { it.remove(IS_GUEST_KEY) }
    }

    @Volatile
    var currentAuthToken: String? = null
        private set

    @Volatile
    var isAuthTokenLoaded: Boolean = false
        private set

    val authToken: Flow<String?> = preferences.data.map { prefs ->
        prefs[AUTH_TOKEN_KEY]
    }

    suspend fun getAuthToken(): String? {
        if (isAuthTokenLoaded) {
            return currentAuthToken
        }
        val token = preferences.data.first()[AUTH_TOKEN_KEY]
        currentAuthToken = token
        isAuthTokenLoaded = true
        return token
    }

    val userRole: Flow<String?> = preferences.data.map { prefs ->
        prefs[USER_ROLE_KEY]
    }

    val deviceId: Flow<String?> = preferences.data.map { prefs ->
        prefs[DEVICE_ID_KEY] ?: deviceIdProvider.getDeviceId()
    }

    val username: Flow<String?> = preferences.data.map { prefs ->
        prefs[USERNAME_KEY]
    }

    val userId: Flow<Long?> = preferences.data.map { prefs ->
        prefs[USER_ID_KEY]
    }

    val userDisplayId: Flow<String?> = preferences.data.map { prefs ->
        prefs[USER_DISPLAY_ID_KEY] ?: prefs[USER_ID_KEY]?.let { id ->
            val role = prefs[USER_ROLE_KEY] ?: "CITIZEN"
            com.travelingsalesman.agap.core.util.UserIdFormatter.formatUserId(role, id)
        }
    }

    val agencyId: Flow<Long?> = preferences.data.map { prefs ->
        prefs[AGENCY_ID_KEY]
    }

    val isDarkMode: Flow<Boolean> = preferences.data.map { prefs ->
        prefs[IS_DARK_MODE_KEY] ?: true // Default to true (Dark mode) matching mockups
    }

    val userFullName: Flow<String?> = preferences.data.map { prefs -> prefs[FULL_NAME_KEY] }
    val userEmail: Flow<String?> = preferences.data.map { prefs -> prefs[EMAIL_KEY] }
    val userPhone: Flow<String?> = preferences.data.map { prefs -> prefs[PHONE_KEY] }
    val userMedicalInfo: Flow<String?> = preferences.data.map { prefs -> prefs[MEDICAL_INFO_KEY] }
    val userNationalId: Flow<String?> = preferences.data.map { prefs -> prefs[NATIONAL_ID_KEY] }
    val userAccountStatus: Flow<String?> = preferences.data.map { prefs -> prefs[ACCOUNT_STATUS_KEY] }
    val userIsVerified: Flow<Boolean> = preferences.data.map { prefs -> prefs[IS_VERIFIED_KEY] ?: false }

    suspend fun setDarkMode(enabled: Boolean) {
        preferences.edit { prefs ->
            prefs[IS_DARK_MODE_KEY] = enabled
        }
    }

    suspend fun saveAuthSession(token: String, user: UserDto, refreshToken: String? = null) = sessionMutex.withLock {
        // Publish the token to the process-local network cache before DataStore IO
        // completes, so authenticated calls issued immediately after login do not
        // have to block an OkHttp thread reading preferences.
        currentAuthToken = token
        isAuthTokenLoaded = true
        preferences.edit { prefs ->
            prefs[AUTH_TOKEN_KEY] = token
            refreshToken?.let { prefs[REFRESH_TOKEN_KEY] = it } ?: prefs.remove(REFRESH_TOKEN_KEY)
            prefs.remove(IS_GUEST_KEY)
            prefs[USER_ID_KEY] = user.id
            prefs[USER_DISPLAY_ID_KEY] = user.displayId
            prefs[USERNAME_KEY] = user.username
            prefs[USER_ROLE_KEY] = user.role
            prefs[DEVICE_ID_KEY] = deviceIdProvider.getDeviceId()
            user.agencyId?.let { prefs[AGENCY_ID_KEY] = it } ?: prefs.remove(AGENCY_ID_KEY)
            user.name?.let { prefs[FULL_NAME_KEY] = it }
            user.email?.let { prefs[EMAIL_KEY] = it }
            user.phoneNumber?.let { prefs[PHONE_KEY] = it }
            user.medicalInfo?.let { prefs[MEDICAL_INFO_KEY] = it }
            user.nationalId?.let { prefs[NATIONAL_ID_KEY] = it }
            prefs[ACCOUNT_STATUS_KEY] = user.accountStatus
            prefs[IS_VERIFIED_KEY] = user.isVerified
        }
        Unit
    }

    suspend fun getRefreshToken(): String? = preferences.data.first()[REFRESH_TOKEN_KEY]

    suspend fun replaceSessionTokens(expectedToken: String, token: String, refreshToken: String): String? =
        sessionMutex.withLock {
            if (getAuthToken() != expectedToken) return@withLock null
            preferences.edit { prefs ->
                prefs[AUTH_TOKEN_KEY] = token
                prefs[REFRESH_TOKEN_KEY] = refreshToken
            }
            currentAuthToken = token
            token
        }

    suspend fun saveUserProfile(user: UserDto) {
        preferences.edit { prefs ->
            prefs[USER_ID_KEY] = user.id
            prefs[USER_DISPLAY_ID_KEY] = user.displayId
            prefs[USERNAME_KEY] = user.username
            prefs[USER_ROLE_KEY] = user.role
            user.name?.let { prefs[FULL_NAME_KEY] = it }
            user.email?.let { prefs[EMAIL_KEY] = it }
            user.phoneNumber?.let { prefs[PHONE_KEY] = it }
            user.medicalInfo?.let { prefs[MEDICAL_INFO_KEY] = it }
            user.nationalId?.let { prefs[NATIONAL_ID_KEY] = it }
            prefs[ACCOUNT_STATUS_KEY] = user.accountStatus
            prefs[IS_VERIFIED_KEY] = user.isVerified
        }
    }



    suspend fun clearAuthSession(expectedToken: String? = null) = sessionMutex.withLock {
        if (expectedToken != null && getAuthToken() != expectedToken) return@withLock
        currentAuthToken = null
        isAuthTokenLoaded = true
        preferences.edit { prefs ->
            val darkModeSetting = prefs[IS_DARK_MODE_KEY]
            prefs.clear()
            darkModeSetting?.let { prefs[IS_DARK_MODE_KEY] = it }
        }
        Unit
    }
}

