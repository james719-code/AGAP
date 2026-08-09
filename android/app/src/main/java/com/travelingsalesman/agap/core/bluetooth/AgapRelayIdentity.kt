package com.travelingsalesman.agap.core.bluetooth

import android.content.Context
import java.security.MessageDigest
import java.util.UUID

/**
 * Installation-scoped relay identity shared by Nearby Connections and raw BLE.
 * Keeping one identity lets the transport router de-duplicate the same physical
 * AGAP node even when it is visible through both transports.
 */
internal object AgapRelayIdentity {
    private const val PREFS_NAME = "agap_ble_mesh_v3"
    private const val PREF_LOCAL_PEER_ID = "local_peer_id_hex"
    private val PEER_ID_REGEX = Regex("^[0-9a-f]{12}$")

    fun getOrCreate(context: Context): String {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val existing = prefs.getString(PREF_LOCAL_PEER_ID, null)
        if (existing != null && PEER_ID_REGEX.matches(existing)) return existing

        val generated = MessageDigest.getInstance("SHA-256")
            .digest(UUID.randomUUID().toString().toByteArray(Charsets.UTF_8))
            .copyOfRange(0, 6)
            .joinToString("") { "%02x".format(it) }

        // commit() is intentional: identity must be durable before either transport
        // advertises it, otherwise a crash could expose two node IDs for one install.
        if (!prefs.edit().putString(PREF_LOCAL_PEER_ID, generated).commit()) {
            BleMeshLog.w("RELAY_ID_PERSIST_FAILED")
        }
        return generated
    }
}
