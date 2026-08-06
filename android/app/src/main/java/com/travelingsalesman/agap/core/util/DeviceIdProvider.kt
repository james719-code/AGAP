package com.travelingsalesman.agap.core.util

import android.content.Context
import android.provider.Settings
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DeviceIdProvider @Inject constructor(
    @ApplicationContext private val context: Context
) {
    fun getDeviceId(): String {
        return try {
            val androidId = Settings.Secure.getString(context.contentResolver, Settings.Secure.ANDROID_ID)
            if (!androidId.isNullOrBlank() && androidId != "9774d56d682e549c") {
                "DEV-ANDROID-$androidId"
            } else {
                getOrGenerateUuidDeviceId()
            }
        } catch (e: Exception) {
            getOrGenerateUuidDeviceId()
        }
    }

    private fun getOrGenerateUuidDeviceId(): String {
        val prefs = context.getSharedPreferences("agap_device_prefs", Context.MODE_PRIVATE)
        var id = prefs.getString("device_id", null)
        if (id.isNullOrBlank()) {
            id = "DEV-" + UUID.randomUUID().toString().take(8).uppercase()
            prefs.edit().putString("device_id", id).apply()
        }
        return id
    }
}
