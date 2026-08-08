package com.travelingsalesman.agap.core.map

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class MapPackageManagerTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    @Test
    fun calculateSha256_returnsCorrectHash() {
        val file = tempFolder.newFile("test.txt")
        file.writeText("hello world")

        val manager = MapPackageManager()

        val hash = manager.calculateSha256(file)
        // SHA-256 of "hello world"
        assertEquals("b94d27b9934d3e08a52e52d7da7dabfac484efe37a5380ee9088f7ace2efcde9", hash)
    }

    @Test
    fun verifyAndActivateMap_rejectsCorruptMapAndPreservesExisting() {
        val offlineDir = tempFolder.newFolder("offline_map")
        val activeFile = File(offlineDir, "deployment.mbtiles")
        activeFile.writeText("valid active map")

        val tempFile = File(offlineDir, "deployment.mbtiles.tmp")
        tempFile.writeText("corrupt map payload")

        val manager = MapPackageManager()

        val activated = manager.verifyAndActivateMap(
            tempFile,
            expectedSha256 = "0000000000000000000000000000000000000000000000000000000000000000",
            offlineDir = offlineDir
        )

        assertFalse(activated)
        assertFalse(tempFile.exists())
        assertTrue(activeFile.exists())
        assertEquals("valid active map", activeFile.readText())
    }

    @Test
    fun verifyAndActivateMap_atomicallyReplacesWhenValid() {
        val offlineDir = tempFolder.newFolder("offline_map")
        val activeFile = File(offlineDir, "deployment.mbtiles")
        activeFile.writeText("old map")

        val tempFile = File(offlineDir, "deployment.mbtiles.tmp")
        tempFile.writeText("new map payload")

        val manager = MapPackageManager()

        val expectedHash = manager.calculateSha256(tempFile)
        val activated = manager.verifyAndActivateMap(
            tempFile,
            expectedSha256 = expectedHash,
            offlineDir = offlineDir
        )

        assertTrue(activated)
        assertFalse(tempFile.exists())
        assertTrue(activeFile.exists())
        assertEquals("new map payload", activeFile.readText())
    }
}
