package com.travelingsalesman.agap.core.bluetooth

import com.travelingsalesman.agap.domain.model.LocationSource
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class MeshHexTest {
    @Test
    fun encoding_matchesPreviousFormatForEveryByteAndPreservesInput() {
        val bytes = ByteArray(256) { it.toByte() }
        val original = bytes.copyOf()
        val expected = bytes.joinToString("") { "%02x".format(it) }
        assertEquals(expected, bytes.toMeshHex())
        assertArrayEquals(original, bytes)
        assertEquals("", byteArrayOf().toMeshHex())
    }

    @Test
    fun decoding_roundTripsAllBytesInBothCases() {
        val bytes = ByteArray(256) { it.toByte() }
        val hex = bytes.toMeshHex()
        assertArrayEquals(bytes, hex.meshHexToBytesOrNull())
        assertArrayEquals(bytes, hex.uppercase().meshHexToBytesOrNull())
        assertArrayEquals(byteArrayOf(0, 15, 0x80.toByte(), 0xff.toByte()), "000F80fF".meshHexToBytesOrNull())
    }

    @Test
    fun decoding_rejectsMalformedAndNonAsciiPayloads() {
        for (value in listOf("", "0", "abc", "0g", "-1", "+1", " 01", "01 ", "0x01", "00\n00", "００", "١٢")) {
            assertNull(value, value.meshHexToBytesOrNull())
        }
    }

    @Test
    fun encoding_sliceKeepsBeaconIdentityWithoutCopyingHeader() {
        val bytes = byteArrayOf(0x47, 0x50, 1, 0, 15, 0x80.toByte(), 0xff.toByte(), 0x12, 0x34)
        assertEquals("000f80ff1234", bytes.toMeshHex(3, 9))
        assertEquals("", bytes.toMeshHex(3, 3))
        assertEquals("1234", bytes.toMeshHex(7))
        assertEquals("000f80ff1234", BlePeerBeaconCodec.decodePeerIdHex(bytes))
    }

    @Test
    fun emergencyEncoding_preservesExactWireBytesAndCrc() {
        val encoded = BleMeshPacketCodec.encodeEmergencyPacket(
            packetType = BleMeshPacketType.SOS,
            emergencyType = "MEDICAL",
            immediateDanger = true,
            subjectRelation = "SELF",
            hopCount = 0,
            locationSource = LocationSource.GPS,
            transportToken = "a73c91f2084d".meshHexToBytesOrNull()!!,
            latitude = 13.1391234,
            longitude = 123.7438567,
            horizontalAccuracyMeters = 5,
            creationTimeSlot = 1000
        )
        assertEquals("8300a73c91f2084d07d4df0249c1d0670503e86d76", encoded.toMeshHex())
    }

    @Test
    fun acknowledgementEncoding_preservesExactWireBytesAndCrc() {
        val encoded = BleMeshPacketCodec.encodeResponsePacket(
            packetType = BleMeshPacketType.ACK,
            hopCount = 1,
            transportToken = "a73c91f2084d".meshHexToBytesOrNull()!!,
            statusCode = 2,
            creationTimeSlot = 1005
        )
        assertEquals("a012a73c91f2084d0203ed3fd5", encoded.toMeshHex())
        val decoded = requireNotNull(BleMeshPacketCodec.decode(encoded))
        assertEquals(1, decoded.hopCount)
        assertEquals(2, decoded.statusCode)
    }

    @Test(expected = IllegalArgumentException::class)
    fun encoding_rejectsInvalidSlice() {
        byteArrayOf(1, 2, 3).toMeshHex(1, 4)
    }
}
