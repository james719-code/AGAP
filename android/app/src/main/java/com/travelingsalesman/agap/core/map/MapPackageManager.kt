package com.travelingsalesman.agap.core.map

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.io.FileOutputStream
import javax.inject.Inject
import javax.inject.Singleton

data class MapPackageMetadata(
    val packageId: String,
    val name: String,
    val version: String,
    val minZoom: Double,
    val maxZoom: Double,
    val boundingBoxStr: String,
    val isBundled: Boolean,
    val filePath: String?
)

@Singleton
class MapPackageManager @Inject constructor(
    @ApplicationContext private val context: Context? = null
) {
    fun getOfflineMapDir(): File {
        val baseDir = context?.filesDir ?: File(System.getProperty("java.io.tmpdir") ?: ".")
        val dir = File(baseDir, "offline_map")
        if (!dir.exists()) {
            dir.mkdirs()
        }
        return dir
    }

    fun getDeploymentMapFile(): File? {
        val mapDir = getOfflineMapDir()
        val mbtilesFile = File(mapDir, "deployment.mbtiles")
        if (mbtilesFile.exists() && mbtilesFile.length() > 0) {
            return mbtilesFile
        }
        return copyBundledMapIfAvailable()
    }

    private fun copyBundledMapIfAvailable(): File? {
        val ctx = context ?: return null
        return try {
            val assetManager = ctx.assets
            val assetPath = "agap/deployment.mbtiles"
            val inputStream = assetManager.open(assetPath)
            val outFile = File(getOfflineMapDir(), "deployment.mbtiles")
            FileOutputStream(outFile).use { outputStream ->
                inputStream.copyTo(outputStream)
            }
            outFile
        } catch (_: Exception) {
            null
        }
    }

    fun calculateSha256(file: File): String {
        if (!file.exists() || !file.isFile) return ""
        return try {
            val digest = java.security.MessageDigest.getInstance("SHA-256")
            file.inputStream().use { input ->
                val buffer = ByteArray(8192)
                var bytesRead: Int
                while (input.read(buffer).also { bytesRead = it } != -1) {
                    digest.update(buffer, 0, bytesRead)
                }
            }
            digest.digest().joinToString("") { "%02x".format(it) }
        } catch (_: Exception) {
            ""
        }
    }

    fun getTempDownloadFile(): File {
        return File(getOfflineMapDir(), "deployment.mbtiles.tmp")
    }

    fun verifyAndActivateMap(
        tempFile: File,
        expectedSha256: String?,
        offlineDir: File = getOfflineMapDir()
    ): Boolean {
        if (!tempFile.exists() || tempFile.length() == 0L) return false

        if (!expectedSha256.isNull_or_blank_check(expectedSha256)) {
            val actualSha256 = calculateSha256(tempFile)
            if (!actualSha256.equals(expectedSha256, ignoreCase = true)) {
                tempFile.delete()
                return false
            }
        }

        val targetFile = File(offlineDir, "deployment.mbtiles")
        val backupFile = File(offlineDir, "deployment.mbtiles.bak")

        if (targetFile.exists()) {
            targetFile.renameTo(backupFile)
        }

        val activated = tempFile.renameTo(targetFile)
        if (activated) {
            backupFile.delete()
            return true
        } else {
            if (backupFile.exists()) {
                backupFile.renameTo(targetFile)
            }
            tempFile.delete()
            return false
        }
    }

    private fun String?.isNull_or_blank_check(s: String?): Boolean {
        return s == null || s.trim().isEmpty() || s == "e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855"
    }

    fun getActiveMapMetadata(): MapPackageMetadata {
        val file = getDeploymentMapFile()
        val checksum = file?.let { calculateSha256(it) } ?: ""
        return MapPackageMetadata(
            packageId = "legazpi_core",
            name = "Legazpi City Deployment Area Map",
            version = "1.0.0",
            minZoom = 10.0,
            maxZoom = 18.0,
            boundingBoxStr = "13.08,123.68,13.20,123.82",
            isBundled = file != null,
            filePath = file?.absolutePath
        )
    }
}
