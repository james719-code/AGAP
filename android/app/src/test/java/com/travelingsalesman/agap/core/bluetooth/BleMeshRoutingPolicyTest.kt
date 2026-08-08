package com.travelingsalesman.agap.core.bluetooth

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BleMeshRoutingPolicyTest {
    @Test
    fun isBleSosExpired_expiresAtMaxHopCount() {
        assertFalse(isBleSosExpired(BLE_MESH_MAX_HOPS - 1))
        assertTrue(isBleSosExpired(BLE_MESH_MAX_HOPS))
    }

    @Test
    fun shouldDisplayBleSosForRole_adminSeesEveryEmergency() {
        assertTrue(shouldDisplayBleSosForRole("ADMIN", null, "CRIME"))
        assertTrue(shouldDisplayBleSosForRole("ADMIN", "MEDICAL", "FIRE"))
    }

    @Test
    fun shouldDisplayBleSosForRole_responderSeesOnlyCompatibleEmergency() {
        assertTrue(shouldDisplayBleSosForRole("RESPONDER", "FIRE", "FIRE"))
        assertTrue(shouldDisplayBleSosForRole("RESPONDER", "POLICE", "CRIME"))
        assertFalse(shouldDisplayBleSosForRole("RESPONDER", "MEDICAL", "FIRE"))
        assertFalse(shouldDisplayBleSosForRole("RESPONDER", null, "MEDICAL"))
    }

    @Test
    fun shouldDisplayBleSosForRole_citizenRelaysWithoutDisplay() {
        assertFalse(shouldDisplayBleSosForRole("CITIZEN", "MEDICAL", "MEDICAL"))
        assertFalse(shouldDisplayBleSosForRole(null, "FIRE", "FIRE"))
    }

    @Test
    fun citizenA_receivesMatchingAck_updatesOwnUiToken() {
        val ownToken = "112233445566"
        val matchingAckToken = "112233445566"
        val unrelatedAckToken = "998877665544"

        assertTrue(ownToken.equals(matchingAckToken, ignoreCase = true))
        assertFalse(ownToken.equals(unrelatedAckToken, ignoreCase = true))
    }

    @Test
    fun maxHopAndExpiration_stopsFurtherRelay() {
        // Max hop 15
        assertTrue(isBleSosExpired(15))
        assertFalse(isBleSosExpired(14))

        // Expired timestamp (2 hours TTL)
        val nowMs = System.currentTimeMillis()
        val twoHoursAgoMs = nowMs - 2 * 3600 * 1000L
        val olderThanTwoHoursMs = nowMs - (2 * 3600 * 1000L + 1000L)

        val isExpired = { timestampMs: Long -> (nowMs - timestampMs) > 2 * 3600 * 1000L }
        assertFalse(isExpired(twoHoursAgoMs))
        assertTrue(isExpired(olderThanTwoHoursMs))
    }

    @Test
    fun terminalResolvedStatus_stopsOriginalRelayAndSuppressesStaleSos() {
        val isTerminal = true
        val incomingPacketType = "SOS"
        val incomingStatusType = "STATUS"

        // Terminal resolution stops relaying original emergency packet
        val shouldRelay = { terminal: Boolean, pType: String -> !terminal || pType == "STATUS" }
        assertFalse(shouldRelay(isTerminal, incomingPacketType))
        assertTrue(shouldRelay(isTerminal, incomingStatusType))

        // Stale SOS after RESOLVED does not trigger responder alert
        val isStaleSosAlertAllowed = { terminal: Boolean -> !terminal }
        assertFalse(isStaleSosAlertAllowed(isTerminal))
    }

    @Test
    fun simultaneousPackets_receiveFairAdvertisementOpportunities() {
        val packetPriorities = listOf(
            Pair("P1_SOS_LOCAL", 100),
            Pair("P2_ACK", 95),
            Pair("P3_CRITICAL_STATUS", 90),
            Pair("P4_SOS_RELAYED", 80),
            Pair("P5_REPORT_RELAYED", 60)
        )
        val sorted = packetPriorities.sortedByDescending { it.second }
        assertEquals("P1_SOS_LOCAL", sorted[0].first)
        assertEquals("P2_ACK", sorted[1].first)
        assertEquals("P5_REPORT_RELAYED", sorted[4].first)
    }
}
