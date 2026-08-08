package com.travelingsalesman.agap.core.bluetooth

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class BleGattTransportAckCodecTest {
    @Test
    fun acceptedAck_roundTripsToken() {
        val encoded = BleGattTransportAckCodec.encode("abcdef123456", accepted = true)
        assertNotNull(encoded)
        val decoded = BleGattTransportAckCodec.decode(requireNotNull(encoded))
        assertNotNull(decoded)
        assertTrue(requireNotNull(decoded).accepted)
        assertEquals("abcdef123456", decoded.tokenHex)
    }

    @Test
    fun rejectedAck_roundTripsToken() {
        val decoded = BleGattTransportAckCodec.decode(
            requireNotNull(BleGattTransportAckCodec.encode("001122aabbcc", accepted = false))
        )
        assertNotNull(decoded)
        assertFalse(requireNotNull(decoded).accepted)
        assertEquals("001122aabbcc", decoded.tokenHex)
    }

    @Test
    fun invalidAck_isRejected() {
        assertNull(BleGattTransportAckCodec.decode(byteArrayOf(1, 2, 3)))
        assertNull(BleGattTransportAckCodec.encode("not-a-token", accepted = true))
    }
}
