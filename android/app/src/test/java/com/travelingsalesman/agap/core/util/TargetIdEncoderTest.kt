package com.travelingsalesman.agap.core.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TargetIdEncoderTest {
    @Test
    fun compactAddressId_isStableForSameCodedAddress() {
        val first = TargetIdEncoder.compactAddressId("05-05-12-01-04::first")
        val second = TargetIdEncoder.compactAddressId("05-05-12-01-04::second")

        assertEquals(first, second)
    }

    @Test
    fun compactAddressId_changesForDifferentCodedAddress() {
        val first = TargetIdEncoder.compactAddressId("05-05-12-01-04::packet")
        val second = TargetIdEncoder.compactAddressId("05-05-12-01-05::packet")

        assertNotEquals(first, second)
    }

    @Test
    fun encodeRelayTargetId_preservesCompactAddressAndPacketUuid() {
        val targetId = TargetIdEncoder.encodeRelayTargetId(42u, "abcdef")

        assertEquals("BLE-00042::abcdef", targetId)
        assertTrue(targetId.startsWith("BLE-"))
    }
}
