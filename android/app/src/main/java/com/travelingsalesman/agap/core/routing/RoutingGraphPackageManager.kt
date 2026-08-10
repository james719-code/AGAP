package com.travelingsalesman.agap.core.routing

import android.content.Context
import com.travelingsalesman.agap.core.bluetooth.BleMeshLog
import com.travelingsalesman.agap.data.local.ServerConfigManager
import com.travelingsalesman.agap.data.remote.RoutingGraphApiService
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.security.MessageDigest
import javax.inject.Inject
import javax.inject.Singleton

enum class RoutingPackageSyncResult {
    READY,
    UPDATED,
    UNAVAILABLE,
    NETWORK_BLOCKED,
    FAILED
}

/**
 * Downloads the server-preprocessed SQLite road graph as one package.
 *
 * This is intentionally separate from route calculation. Admin/Responder screens
 * warm this package while online, so an emergency route normally performs only:
 * memory/SQLite lookup -> spatial snap -> A*.
 */
@Singleton
class RoutingGraphPackageManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val api: RoutingGraphApiService,
    private val store: OfflineRoutingGraphStore
) {
    private val mutex = Mutex()
    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    suspend fun syncIfNeeded(
        allowNetwork: Boolean,
        forceMetadataCheck: Boolean = false
    ): RoutingPackageSyncResult = withContext(Dispatchers.IO) {
        if (!allowNetwork || ServerConfigManager.isBleOnlyActive) {
            return@withContext if (store.hasAnyCoverage()) {
                RoutingPackageSyncResult.READY
            } else {
                RoutingPackageSyncResult.NETWORK_BLOCKED
            }
        }

        mutex.withLock {
            val now = System.currentTimeMillis()
            val lastCheck = prefs.getLong(KEY_LAST_METADATA_CHECK, 0L)

            if (
                !forceMetadataCheck &&
                store.hasAnyCoverage() &&
                now - lastCheck < METADATA_RECHECK_INTERVAL_MS
            ) {
                return@withLock RoutingPackageSyncResult.READY
            }

            val metadataResponse = runCatching { api.metadata() }.getOrElse { error ->
                BleMeshLog.w(
                    "ROUTING_PACKAGE_METADATA_FAILED",
                    "error" to (error.localizedMessage ?: error.javaClass.simpleName)
                )
                return@withLock if (store.hasAnyCoverage()) {
                    RoutingPackageSyncResult.READY
                } else {
                    RoutingPackageSyncResult.FAILED
                }
            }

            prefs.edit().putLong(KEY_LAST_METADATA_CHECK, now).apply()

            if (!metadataResponse.isSuccessful) {
                return@withLock if (store.hasAnyCoverage()) {
                    RoutingPackageSyncResult.READY
                } else {
                    RoutingPackageSyncResult.FAILED
                }
            }

            val metadata = metadataResponse.body()
            if (metadata == null || !metadata.available || metadata.sha256.isBlank()) {
                return@withLock if (store.hasAnyCoverage()) {
                    RoutingPackageSyncResult.READY
                } else {
                    RoutingPackageSyncResult.UNAVAILABLE
                }
            }

            val installedSha = prefs.getString(KEY_INSTALLED_SHA256, null)
            if (
                store.hasAnyCoverage() &&
                installedSha.equals(metadata.sha256, ignoreCase = true)
            ) {
                return@withLock RoutingPackageSyncResult.READY
            }

            val downloadResponse = runCatching { api.download() }.getOrElse { error ->
                BleMeshLog.w(
                    "ROUTING_PACKAGE_DOWNLOAD_FAILED",
                    "error" to (error.localizedMessage ?: error.javaClass.simpleName)
                )
                return@withLock if (store.hasAnyCoverage()) {
                    RoutingPackageSyncResult.READY
                } else {
                    RoutingPackageSyncResult.FAILED
                }
            }

            val body = downloadResponse.body()
            if (!downloadResponse.isSuccessful || body == null) {
                return@withLock if (store.hasAnyCoverage()) {
                    RoutingPackageSyncResult.READY
                } else {
                    RoutingPackageSyncResult.FAILED
                }
            }

            val tempFile = File(context.cacheDir, ROUTING_PACKAGE_TEMP_FILE)
            tempFile.delete()

            val actualSha = try {
                val digest = MessageDigest.getInstance("SHA-256")
                body.byteStream().use { input ->
                    FileOutputStream(tempFile).use { output ->
                        val buffer = ByteArray(64 * 1024)
                        while (true) {
                            val count = input.read(buffer)
                            if (count < 0) break
                            if (count == 0) continue
                            digest.update(buffer, 0, count)
                            output.write(buffer, 0, count)
                        }
                        output.fd.sync()
                    }
                }
                digest.digest().joinToString("") { byte -> "%02x".format(byte) }
            } catch (error: Exception) {
                tempFile.delete()
                BleMeshLog.w(
                    "ROUTING_PACKAGE_WRITE_FAILED",
                    "error" to (error.localizedMessage ?: error.javaClass.simpleName)
                )
                return@withLock if (store.hasAnyCoverage()) {
                    RoutingPackageSyncResult.READY
                } else {
                    RoutingPackageSyncResult.FAILED
                }
            } finally {
                body.close()
            }

            if (!actualSha.equals(metadata.sha256, ignoreCase = true)) {
                tempFile.delete()
                BleMeshLog.w(
                    "ROUTING_PACKAGE_SHA_MISMATCH",
                    "expected" to metadata.sha256.take(12),
                    "actual" to actualSha.take(12)
                )
                return@withLock if (store.hasAnyCoverage()) {
                    RoutingPackageSyncResult.READY
                } else {
                    RoutingPackageSyncResult.FAILED
                }
            }

            if (!store.installPrebuiltDatabase(tempFile)) {
                tempFile.delete()
                return@withLock if (store.hasAnyCoverage()) {
                    RoutingPackageSyncResult.READY
                } else {
                    RoutingPackageSyncResult.FAILED
                }
            }

            prefs.edit()
                .putString(KEY_INSTALLED_SHA256, metadata.sha256)
                .putString(KEY_INSTALLED_MODIFIED_AT, metadata.modifiedAt)
                .apply()

            RoutingPackageSyncResult.UPDATED
        }
    }

    companion object {
        private const val PREFS_NAME = "agap_routing_package"
        private const val KEY_INSTALLED_SHA256 = "installed_sha256"
        private const val KEY_INSTALLED_MODIFIED_AT = "installed_modified_at"
        private const val KEY_LAST_METADATA_CHECK = "last_metadata_check"
        private const val ROUTING_PACKAGE_TEMP_FILE = "deployment-routing.db.download"
        private const val METADATA_RECHECK_INTERVAL_MS = 6L * 60L * 60L * 1000L
    }
}
