package com.travelingsalesman.agap.data.repository

import com.travelingsalesman.agap.data.local.entity.IncidentEntity
import org.junit.Assert.assertEquals
import org.junit.Test

class IncidentMessagingRepositoryServerIdTest {

    @Test
    fun reconciledMeshIncident_usesStoredServerIdForOnlineMessagesAndReplies() {
        val incident = IncidentEntity(
            incidentId = "b9572ad7-2d45-4a37-8da8-79d7bfcb9b22",
            serverIncidentId = 417L,
            targetId = "BLE-BLE-BLE-BLE::e3a1",
            emergencyType = "MEDICAL",
            latitude = 13.14,
            longitude = 123.74,
            timestamp = 1L,
            status = "RECEIVED",
            packetHash = "a1b2c3d4e5f6"
        )

        assertEquals(417L, incident.messageServerId())
    }

    @Test
    fun cloudOnlyIncident_fallsBackToItsNumericLocalKey() {
        val incident = IncidentEntity(
            incidentId = "418",
            targetId = "UNASSIGNED",
            emergencyType = "FIRE",
            latitude = 13.14,
            longitude = 123.74,
            timestamp = 1L,
            status = "RECEIVED"
        )

        assertEquals(418L, incident.messageServerId())
    }
}
