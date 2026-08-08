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
    @ApplicationContext private val context: Context
) {
    fun getOfflineMapDir(): File {
        val dir = File(context.filesDir, "offline_map")
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
        return try {
            val assetManager = context.assets
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

    fun getActiveMapMetadata(): MapPackageMetadata {
        val file = getDeploymentMapFile()
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
