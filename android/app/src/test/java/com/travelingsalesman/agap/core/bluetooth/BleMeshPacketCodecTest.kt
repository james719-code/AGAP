package com.travelingsalesman.agap.core.bluetooth

import com.travelingsalesman.agap.domain.model.LocationSource
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class BleMeshPacketCodecTest {
    @Test
    fun encodeDecode_roundTripEmergencyPacketV2() {
        val token = byteArrayOf(0x12, 0x34, 0x56, 0x78, 0x9A.toByte(), 0xBC.toByte())

        val encoded = BleMeshPacketCodec.encodeEmergencyPacket(
            packetType = BleMeshPacketType.SOS,
            emergencyType = "FIRE",
            immediateDanger = true,
            subjectRelation = "OTHER_PERSON",
            hopCount = 3,
            locationSource = LocationSource.MAP_PIN,
            transportToken = token,
            latitude = 13.1391,
            longitude = 123.7438,
            horizontalAccuracyMeters = 255
        )

        assertEquals(21, encoded.size)

        val decoded = BleMeshPacketCodec.decode(encoded)
        assertNotNull(decoded)
        requireNotNull(decoded)

        assertEquals(2, decoded.protocolVersion.toInt())
        assertEquals(BleMeshPacketType.SOS, decoded.packetType)
        assertEquals("FIRE", decoded.emergencyType)
        assertEquals(true, decoded.immediateDanger)
        assertEquals("OTHER_PERSON", decoded.subjectRelation)
        assertEquals(3, decoded.hopCount)
        assertEquals(LocationSource.MAP_PIN, decoded.locationSource)
        assertEquals("123456789abc", decoded.transportTokenHex)
        assertEquals(13.1391, decoded.latitude, 0.0000001)
        assertEquals(123.7438, decoded.longitude, 0.0000001)
        assertEquals(255, decoded.horizontalAccuracyMeters)
    }

    @Test
    fun encodeDecode_roundTripResponsePacketV2() {
        val token = byteArrayOf(0xAA.toByte(), 0xBB.toByte(), 0xCC.toByte(), 0xDD.toByte(), 0xEE.toByte(), 0xFF.toByte())

        val encoded = BleMeshPacketCodec.encodeResponsePacket(
            packetType = BleMeshPacketType.ACK,
            hopCount = 2,
            transportToken = token,
            statusCode = 1
        )

        assertEquals(13, encoded.size)

        val decoded = BleMeshPacketCodec.decode(encoded)
        assertNotNull(decoded)
        requireNotNull(decoded)

        assertEquals(BleMeshPacketType.ACK, decoded.packetType)
        assertEquals(2, decoded.hopCount)
        assertEquals("aabbccddeeff", decoded.transportTokenHex)
        assertEquals(1, decoded.statusCode)
    }

    @Test
    fun decode_rejectsCorruptCrc() {
        val token = byteArrayOf(1, 2, 3, 4, 5, 6)
        val encoded = BleMeshPacketCodec.encodeEmergencyPacket(
            packetType = BleMeshPacketType.SOS,
            emergencyType = "MEDICAL",
            immediateDanger = false,
            subjectRelation = "SELF",
            hopCount = 0,
            locationSource = LocationSource.GPS,
            transportToken = token,
            latitude = 13.0,
            longitude = 123.0
        )

        encoded[10] = (encoded[10].toInt() xor 0xFF).toByte()

        assertNull(BleMeshPacketCodec.decode(encoded))
    }

    @Test
    fun unknownEmergencyCode_decodesToUnknown() {
        val token = byteArrayOf(1, 1, 1, 1, 1, 1)
        val encoded = BleMeshPacketCodec.encodeEmergencyPacket(
            packetType = BleMeshPacketType.INCIDENT_REPORT,
            emergencyType = "UNKNOWN",
            immediateDanger = false,
            subjectRelation = "PUBLIC_OR_UNKNOWN",
            hopCount = 1,
            locationSource = LocationSource.UNKNOWN,
            transportToken = token,
            latitude = 0.0,
            longitude = 0.0
        )

        val decoded = requireNotNull(BleMeshPacketCodec.decode(encoded))
        assertEquals("UNKNOWN", decoded.emergencyType)
        assertEquals(LocationSource.UNKNOWN, decoded.locationSource)
    }

    @Test
    fun e7Coordinates_roundTripQuantizationErrorWithinBounds() {
        val lat = 13.14159265
        val lon = 123.74381928
        val token = byteArrayOf(1, 2, 3, 4, 5, 6)

        val encoded = BleMeshPacketCodec.encodeEmergencyPacket(
            packetType = BleMeshPacketType.SOS,
            emergencyType = "ACCIDENT",
            immediateDanger = false,
            subjectRelation = "SELF",
            hopCount = 0,
            locationSource = LocationSource.GPS,
            transportToken = token,
            latitude = lat,
            longitude = lon
        )

        val decoded = requireNotNull(BleMeshPacketCodec.decode(encoded))
        assertEquals(lat, decoded.latitude, 0.0000001)
        assertEquals(lon, decoded.longitude, 0.0000001)

    }

    @Test
    fun decode_legacyV1Header_returnsDecodedPacket() {
        val legacyBytes = byteArrayOf(
            'A'.code.toByte(), 'G'.code.toByte(), 1, 0x10, 0x20, 0x30, 0x40, 0x50, 0x60, 3, 2
        )

        val decoded = BleMeshPacketCodec.decode(legacyBytes)
        assertNotNull(decoded)
        requireNotNull(decoded)
        assertEquals(1, decoded.protocolVersion.toInt())
        assertEquals("FIRE", decoded.emergencyType)
        assertEquals("102030405060", decoded.transportTokenHex)
    }

    @Test
    fun encodeDecode_subjectRelationsAndTypes() {
        val token = byteArrayOf(1, 2, 3, 4, 5, 6)

        // SOS SELF
        val packetSelf = BleMeshPacketCodec.decode(
            BleMeshPacketCodec.encodeEmergencyPacket(
                packetType = BleMeshPacketType.SOS, emergencyType = "MEDICAL", immediateDanger = true,
                subjectRelation = "SELF", hopCount = 0, locationSource = LocationSource.GPS, transportToken = token
            )
        )
        assertNotNull(packetSelf)
        assertEquals("SELF", packetSelf?.subjectRelation)

        // SOS OTHER_PERSON
        val packetOther = BleMeshPacketCodec.decode(
            BleMeshPacketCodec.encodeEmergencyPacket(
                packetType = BleMeshPacketType.SOS, emergencyType = "ACCIDENT", immediateDanger = true,
                subjectRelation = "OTHER_PERSON", hopCount = 0, locationSource = LocationSource.GPS, transportToken = token
            )
        )
        assertEquals("OTHER_PERSON", packetOther?.subjectRelation)

        // INCIDENT_REPORT OTHER_PERSON
        val packetIncOther = BleMeshPacketCodec.decode(
            BleMeshPacketCodec.encodeEmergencyPacket(
                packetType = BleMeshPacketType.INCIDENT_REPORT, emergencyType = "FIRE", immediateDanger = false,
                subjectRelation = "OTHER_PERSON", hopCount = 0, locationSource = LocationSource.MAP_PIN, transportToken = token
            )
        )
        assertEquals(BleMeshPacketType.INCIDENT_REPORT, packetIncOther?.packetType)
        assertEquals("OTHER_PERSON", packetIncOther?.subjectRelation)

        // INCIDENT_REPORT PUBLIC_OR_UNKNOWN
        val packetIncPublic = BleMeshPacketCodec.decode(
            BleMeshPacketCodec.encodeEmergencyPacket(
                packetType = BleMeshPacketType.INCIDENT_REPORT, emergencyType = "DISASTER", immediateDanger = false,
                subjectRelation = "PUBLIC_OR_UNKNOWN", hopCount = 0, locationSource = LocationSource.BARANGAY_REFERENCE, transportToken = token
            )
        )
        assertEquals("PUBLIC_OR_UNKNOWN", packetIncPublic?.subjectRelation)
    }

    @Test
    fun encodeDecode_locationSourcesAndAccuracy() {
        val token = byteArrayOf(1, 2, 3, 4, 5, 6)

        // GPS & Accuracy 3
        val gpsAcc = BleMeshPacketCodec.decode(
            BleMeshPacketCodec.encodeEmergencyPacket(
                packetType = BleMeshPacketType.SOS, emergencyType = "CRIME", immediateDanger = true,
                subjectRelation = "SELF", hopCount = 0, locationSource = LocationSource.GPS,
                transportToken = token, horizontalAccuracyMeters = 3
            )
        )
        assertEquals(LocationSource.GPS, gpsAcc?.locationSource)
        assertEquals(3, gpsAcc?.horizontalAccuracyMeters)

        // MAP_PIN & Accuracy 254
        val mapPinAcc = BleMeshPacketCodec.decode(
            BleMeshPacketCodec.encodeEmergencyPacket(
                packetType = BleMeshPacketType.SOS, emergencyType = "CRIME", immediateDanger = true,
                subjectRelation = "SELF", hopCount = 0, locationSource = LocationSource.MAP_PIN,
                transportToken = token, horizontalAccuracyMeters = 254
            )
        )
        assertEquals(LocationSource.MAP_PIN, mapPinAcc?.locationSource)
        assertEquals(254, mapPinAcc?.horizontalAccuracyMeters)

        // BARANGAY_REFERENCE & Accuracy 255 (Unknown)
        val brgyAcc = BleMeshPacketCodec.decode(
            BleMeshPacketCodec.encodeEmergencyPacket(
                packetType = BleMeshPacketType.SOS, emergencyType = "CRIME", immediateDanger = true,
                subjectRelation = "SELF", hopCount = 0, locationSource = LocationSource.BARANGAY_REFERENCE,
                transportToken = token, horizontalAccuracyMeters = 255
            )
        )
        assertEquals(LocationSource.BARANGAY_REFERENCE, brgyAcc?.locationSource)
        assertEquals(255, brgyAcc?.horizontalAccuracyMeters)

        // UNKNOWN Location Source
        val unkLoc = BleMeshPacketCodec.decode(
            BleMeshPacketCodec.encodeEmergencyPacket(
                packetType = BleMeshPacketType.SOS, emergencyType = "CRIME", immediateDanger = true,
                subjectRelation = "SELF", hopCount = 0, locationSource = LocationSource.UNKNOWN,
                transportToken = token, horizontalAccuracyMeters = 255
            )
        )
        assertEquals(LocationSource.UNKNOWN, unkLoc?.locationSource)
    }

    @Test
    fun encodeDecode_hopCountExtremes() {
        val token = byteArrayOf(1, 2, 3, 4, 5, 6)

        // Hop 0
        val hop0 = BleMeshPacketCodec.decode(
            BleMeshPacketCodec.encodeEmergencyPacket(
                packetType = BleMeshPacketType.SOS, emergencyType = "MEDICAL", immediateDanger = false,
                subjectRelation = "SELF", hopCount = 0, locationSource = LocationSource.GPS, transportToken = token
            )
        )
        assertEquals(0, hop0?.hopCount)

        // Hop 15
        val hop15 = BleMeshPacketCodec.decode(
            BleMeshPacketCodec.encodeEmergencyPacket(
                packetType = BleMeshPacketType.SOS, emergencyType = "MEDICAL", immediateDanger = false,
                subjectRelation = "SELF", hopCount = 15, locationSource = LocationSource.GPS, transportToken = token
            )
        )
        assertEquals(15, hop15?.hopCount)
    }

    @Test
    fun encodeDecode_positiveAndNegativeCoordinatesRoundTrip() {
        val token = byteArrayOf(10, 20, 30, 40, 50, 60)

        // Positive E7 coordinates (Legazpi PH)
        val posDec = BleMeshPacketCodec.decode(
            BleMeshPacketCodec.encodeEmergencyPacket(
                packetType = BleMeshPacketType.SOS, emergencyType = "MEDICAL", immediateDanger = false,
                subjectRelation = "SELF", hopCount = 0, locationSource = LocationSource.GPS, transportToken = token,
                latitude = 13.1391234, longitude = 123.7438123
            )
        )
        assertEquals(13.1391234, posDec!!.latitude, 0.0000001)
        assertEquals(123.7438123, posDec.longitude, 0.0000001)

        // Negative E7 coordinates (Southern / Western hemisphere test)
        val negDec = BleMeshPacketCodec.decode(
            BleMeshPacketCodec.encodeEmergencyPacket(
                packetType = BleMeshPacketType.SOS, emergencyType = "MEDICAL", immediateDanger = false,
                subjectRelation = "SELF", hopCount = 0, locationSource = LocationSource.GPS, transportToken = token,
                latitude = -33.8688123, longitude = -151.2092987
            )
        )
        assertEquals(-33.8688123, negDec!!.latitude, 0.0000001)
        assertEquals(-151.2092987, negDec.longitude, 0.0000001)
    }

    @Test
    fun decode_unsupportedVersion_returnsNull() {
        val token = byteArrayOf(1, 2, 3, 4, 5, 6)
        val encoded = BleMeshPacketCodec.encodeEmergencyPacket(
            packetType = BleMeshPacketType.SOS, emergencyType = "MEDICAL", immediateDanger = false,
            subjectRelation = "SELF", hopCount = 0, locationSource = LocationSource.GPS, transportToken = token
        )

        // Change version bits (top 2 bits of byte 0) to version 3 (11 in binary)
        encoded[0] = ((encoded[0].toInt() and 0x3F) or (3 shl 6)).toByte()

        assertNull(BleMeshPacketCodec.decode(encoded))
    }

    @Test
    fun tokenAndCreationTimePreservation() {
        val token = byteArrayOf(0x01, 0x23, 0x45, 0x67, 0x89.toByte(), 0xAB.toByte())
        val creationSlot = (System.currentTimeMillis() / 1000 % 65536).toInt()

        val encoded = BleMeshPacketCodec.encodeEmergencyPacket(
            packetType = BleMeshPacketType.SOS, emergencyType = "DISASTER", immediateDanger = true,
            subjectRelation = "SELF", hopCount = 5, locationSource = LocationSource.GPS,
            transportToken = token, creationTimeSlot = creationSlot
        )

        val decoded = requireNotNull(BleMeshPacketCodec.decode(encoded))
        assertEquals("0123456789ab", decoded.transportTokenHex)
        assertEquals(creationSlot, decoded.creationTimeSlot)
    }
}

