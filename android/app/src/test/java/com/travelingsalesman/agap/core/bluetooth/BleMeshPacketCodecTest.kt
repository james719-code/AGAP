package com.travelingsalesman.agap.core.bluetooth

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class BleMeshPacketCodecTest {
    @Test
    fun encodeDecode_roundTripSosPacket() {
        val compactId = byteArrayOf(0x01, 0x23, 0x45, 0x67, 0x10, 0x20, 0x30, 0x40)

        val encoded = BleMeshPacketCodec.encode(
            packetType = BleMeshPacketType.SOS,
            emergencyType = "FIRE",
            hopCount = 3,
            compactId = compactId,
            latitude = 13.1391,
            longitude = 123.7438,
            addressId = 42u
        )

        assertEquals(BleMeshPacketCodec.ENCODED_LENGTH, encoded.size)

        val decoded = BleMeshPacketCodec.decode(encoded)
        assertNotNull(decoded)
        requireNotNull(decoded)
        assertEquals(BleMeshPacketCodec.PROTOCOL_VERSION, decoded.protocolVersion)
        assertEquals(BleMeshPacketType.SOS, decoded.packetType)
        assertEquals("FIRE", decoded.emergencyType)
        assertEquals(3, decoded.hopCount)
        assertEquals("0123456710203040", decoded.packetUuid)
        assertEquals(13.1391, decoded.latitude, 0.001)
        assertEquals(123.7438, decoded.longitude, 0.001)
        assertEquals(42, decoded.addressId.toInt())
    }

    @Test
    fun decode_rejectsCorruptChecksum() {
        val encoded = BleMeshPacketCodec.encode(
            packetType = BleMeshPacketType.SOS,
            emergencyType = "MEDICAL",
            hopCount = 1,
            compactId = byteArrayOf(1, 2, 3),
            latitude = 13.0,
            longitude = 123.0
        )

        encoded[6] = (encoded[6].toInt() xor 0x7F).toByte()

        assertNull(BleMeshPacketCodec.decode(encoded))
    }

    @Test
    fun encode_clampsCoordinatesToPhilippinesBounds() {
        val encoded = BleMeshPacketCodec.encode(
            packetType = BleMeshPacketType.SOS,
            emergencyType = "CRIME",
            hopCount = 0,
            compactId = byteArrayOf(9),
            latitude = 1.0,
            longitude = 140.0
        )

        val decoded = requireNotNull(BleMeshPacketCodec.decode(encoded))
        assertEquals(4.0, decoded.latitude, 0.001)
        assertEquals(127.0, decoded.longitude, 0.001)
    }

    @Test
    fun encodeDecode_roundTripAcknowledgmentPacketWithMessageCode() {
        val compactId = byteArrayOf(0x10, 0x11, 0x12, 0x13, 0x20, 0x21, 0x22, 0x23)

        val encoded = BleMeshPacketCodec.encode(
            packetType = BleMeshPacketType.ACK,
            emergencyType = "MEDICAL",
            hopCount = 1,
            compactId = compactId,
            latitude = 13.14,
            longitude = 123.74,
            addressId = 999u,
            messageCode = 2u
        )

        assertEquals(BleMeshPacketCodec.ENCODED_LENGTH, encoded.size)

        val decoded = requireNotNull(BleMeshPacketCodec.decode(encoded))
        assertEquals(BleMeshPacketType.ACK, decoded.packetType)
        assertEquals("MEDICAL", decoded.emergencyType)
        assertEquals("1011121320212223", decoded.packetUuid)
        assertEquals(0, decoded.addressId.toInt())
        assertEquals(2, decoded.messageCode.toInt())
    }
}
