package com.travelingsalesman.agap.core.bluetooth

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class BlePeerBeaconCodecTest {
    @Test
    fun presenceBeacon_roundTripsStablePeerId() {
        val peer = byteArrayOf(0x01, 0x23, 0x45, 0x67, 0x11, 0x22)
        val encoded = BlePeerBeaconCodec.encode(peer)

        assertEquals(BlePeerBeaconCodec.LENGTH, encoded.size)
        assertEquals("012345671122", BlePeerBeaconCodec.decodePeerIdHex(encoded))
    }

    @Test
    fun emergencyPacket_isNotMistakenForPresenceBeacon() {
        assertNull(BlePeerBeaconCodec.decodePeerIdHex(ByteArray(21)))
    }
}
