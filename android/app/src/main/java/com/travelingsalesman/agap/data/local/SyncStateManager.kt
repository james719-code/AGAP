package com.travelingsalesman.agap.data.local

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton

private val Context.syncStateDataStore: DataStore<Preferences> by preferencesDataStore(name = backendStorageName("agap_sync_state"))

@Singleton
class SyncStateManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val authTokenManager: AuthTokenManager
) {
    private val LAST_CHANGES_SYNC_KEY = stringPreferencesKey("last_changes_sync")
    private val LAST_CHANGES_USER_KEY = stringPreferencesKey("last_changes_user")

    val lastChangesSync: Flow<String> = context.syncStateDataStore.data.map { prefs ->
        val user = authTokenManager.userId.first()?.toString()
        if (prefs[LAST_CHANGES_USER_KEY] == user) prefs[LAST_CHANGES_SYNC_KEY] ?: Instant.EPOCH.toString()
        else Instant.EPOCH.toString()
    }

    suspend fun setLastChangesSync(value: String, userId: Long) {
        context.syncStateDataStore.edit { prefs ->
            prefs[LAST_CHANGES_SYNC_KEY] = value
            prefs[LAST_CHANGES_USER_KEY] = userId.toString()
        }
    }
}
