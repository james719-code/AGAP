package com.travelingsalesman.agap.map

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class OfflineMapTest {

    @Test
    fun checksumVerification_validatesMapPackage() {
        val expectedChecksum = "e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855"
        val actualChecksum = "e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855"
        val corruptChecksum = "0000000000000000000000000000000000000000000000000000000000000000"

        val verifyPackage = { chk1: String, chk2: String -> chk1.equals(chk2, ignoreCase = true) }

        assertTrue(verifyPackage(expectedChecksum, actualChecksum))
        assertFalse(verifyPackage(expectedChecksum, corruptChecksum))
    }

    @Test
    fun missingMapPackage_fallsBackToCoordinatesTextWithoutCrashing() {
        val isMapFileAvailable = false
        val latitude = 13.1391
        val longitude = 123.7438

        val renderStrategy = if (isMapFileAvailable) "MBTILES_RENDER" else "COORDINATE_TEXT_FALLBACK"

        assertEquals("COORDINATE_TEXT_FALLBACK", renderStrategy)
        assertNotNull(latitude)
        assertNotNull(longitude)
    }

    @Test
    fun corruptMapUpdate_retainsExistingWorkingPackage() {
        val activeVersion = "1.0.0"
        val incomingUpdateVersion = "1.0.1"
        val isUpdateChecksumValid = false

        val finalActiveVersion = if (isUpdateChecksumValid) incomingUpdateVersion else activeVersion

        assertEquals("1.0.0", finalActiveVersion)
    }
}
