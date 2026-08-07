package com.travelingsalesman.agap.core.bluetooth

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
}
