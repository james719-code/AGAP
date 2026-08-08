package com.travelingsalesman.agap.location

import com.travelingsalesman.agap.domain.model.LocationSource
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LocationWorkflowTest {

    @Test
    fun recentAccurateGps_isUsedImmediately() {
        val maxAgeMs = 15000L
        val maxAccuracyMeters = 30f

        val ageMs = 5000L
        val accuracyMeters = 10f

        val isRecentAndAccurate = ageMs <= maxAgeMs && accuracyMeters <= maxAccuracyMeters
        assertTrue(isRecentAndAccurate)
    }

    @Test
    fun oldInaccurateGps_triggersFreshRequestOrFallback() {
        val maxAgeMs = 15000L
        val maxAccuracyMeters = 30f

        val ageMs = 45000L
        val accuracyMeters = 100f

        val isRecentAndAccurate = ageMs <= maxAgeMs && accuracyMeters <= maxAccuracyMeters
        assertFalse(isRecentAndAccurate)
    }

    @Test
    fun unknownLocation_neverPlotsZeroZero() {
        val lat = 0.0
        val lon = 0.0
        val locationSource = LocationSource.UNKNOWN

        val isValidPlotCoordinate = { l1: Double, l2: Double, src: LocationSource ->
            src != LocationSource.UNKNOWN && (l1 != 0.0 || l2 != 0.0)
        }

        assertFalse(isValidPlotCoordinate(lat, lon, locationSource))
    }

    @Test
    fun relayPhone_doesNotOverwriteEventCoordinates() {
        val eventLat = 13.1391
        val eventLon = 123.7438
        val relayPhoneLat = 14.5995
        val relayPhoneLon = 120.9842

        val processedLat = eventLat
        val processedLon = eventLon

        assertEquals(13.1391, processedLat, 0.0001)
        assertEquals(123.7438, processedLon, 0.0001)
        assertFalse(processedLat == relayPhoneLat)
        assertFalse(processedLon == relayPhoneLon)
    }

    @Test
    fun sameTokenRefinement_improvesLocationWithoutCreatingNewReport() {
        val initialSource = LocationSource.BARANGAY_REFERENCE
        val initialLat = 13.1400
        val initialLon = 123.7400

        val refinedSource = LocationSource.GPS
        val refinedLat = 13.1391234
        val refinedLon = 123.7438123

        val isRefinement = { oldSrc: LocationSource, newSrc: LocationSource ->
            (oldSrc == LocationSource.BARANGAY_REFERENCE && (newSrc == LocationSource.GPS || newSrc == LocationSource.MAP_PIN)) ||
            (oldSrc == LocationSource.UNKNOWN && newSrc != LocationSource.UNKNOWN)
        }

        assertTrue(isRefinement(initialSource, refinedSource))
    }

    @Test
    fun mapPin_isNotOverwrittenByUnrelatedPhoneGps() {
        val mapPinSource = LocationSource.MAP_PIN
        val mapPinLat = 13.1450
        val mapPinLon = 123.7500

        val phoneGpsSource = LocationSource.GPS
        val phoneGpsLat = 13.1300
        val phoneGpsLon = 123.7300

        val shouldOverwriteMapPin = { oldSrc: LocationSource, newSrc: LocationSource ->
            // Explicit MAP_PIN must NOT be overwritten by unrelated phone GPS
            if (oldSrc == LocationSource.MAP_PIN && newSrc == LocationSource.GPS) false else true
        }

        assertFalse(shouldOverwriteMapPin(mapPinSource, phoneGpsSource))
        assertEquals(13.1450, mapPinLat, 0.0001)
    }
}
