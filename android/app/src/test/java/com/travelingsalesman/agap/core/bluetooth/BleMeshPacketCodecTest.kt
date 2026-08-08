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
}
