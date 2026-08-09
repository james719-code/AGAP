package com.travelingsalesman.agap.core.bluetooth

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class NearbyMeshFrameCodecTest {
    @Test
    fun dataFrame_roundTrips() {
        val payload = byteArrayOf(0x01, 0x02, 0x03, 0x7f)
        val encoded = NearbyMeshFrameCodec.encodeData(123456789L, payload)
        val decoded = NearbyMeshFrameCodec.decode(encoded) as NearbyMeshFrameCodec.Frame.Data

        assertEquals(123456789L, decoded.transferId)
        assertArrayEquals(payload, decoded.agapPayload)
    }

    @Test
    fun ackFrame_roundTripsAcceptedFlag() {
        val accepted = NearbyMeshFrameCodec.decode(
            NearbyMeshFrameCodec.encodeAck(99L, true)
        ) as NearbyMeshFrameCodec.Frame.Ack
        val rejected = NearbyMeshFrameCodec.decode(
            NearbyMeshFrameCodec.encodeAck(100L, false)
        ) as NearbyMeshFrameCodec.Frame.Ack

        assertEquals(99L, accepted.transferId)
        assertTrue(accepted.accepted)
        assertEquals(100L, rejected.transferId)
        assertFalse(rejected.accepted)
    }

    @Test
    fun malformedFrame_isRejected() {
        assertNull(NearbyMeshFrameCodec.decode(byteArrayOf(0x41, 0x47, 0x01)))
        assertNull(NearbyMeshFrameCodec.decode(ByteArray(12)))
    }
}
