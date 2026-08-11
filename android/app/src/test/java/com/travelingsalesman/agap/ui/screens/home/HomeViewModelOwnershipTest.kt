package com.travelingsalesman.agap.ui.screens.home

import com.travelingsalesman.agap.data.local.entity.IncidentEntity
import com.travelingsalesman.agap.data.local.entity.LocalIncidentReportEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class HomeViewModelOwnershipTest {

    @Test
    fun `does not select an arbitrary active incident without a local SOS`() {
        val selection = resolveCitizenOwnedSos(
            reports = emptyList(),
            incidents = listOf(incident(id = "44", status = "EN_ROUTE"))
        )

        assertNull(selection)
    }

    @Test
    fun `selects the matching active incident for this device's persisted SOS`() {
        val selection = resolveCitizenOwnedSos(
            reports = listOf(report(serverIncidentId = 44L, observedAt = 2_000L)),
            incidents = listOf(incident(id = "44", status = "EN_ROUTE"))
        )

        requireNotNull(selection)
        assertEquals("44", selection.trackingId)
        assertEquals("44", selection.incident?.incidentId)
    }

    @Test
    fun `latest terminal SOS does not restart an older tracking session`() {
        val selection = resolveCitizenOwnedSos(
            reports = listOf(
                report(serverIncidentId = 44L, observedAt = 1_000L),
                report(serverIncidentId = 45L, observedAt = 2_000L)
            ),
            incidents = listOf(
                incident(id = "44", status = "EN_ROUTE"),
                incident(id = "45", status = "RESOLVED")
            )
        )

        assertNull(selection)
    }

    @Test
    fun `pending local SOS uses only its persisted transport token`() {
        val localReport = report(
            serverIncidentId = null,
            syncState = "QUEUED_OFFLINE",
            transportToken = "local-token",
            observedAt = 2_000L
        )

        val selection = resolveCitizenOwnedSos(
            reports = listOf(localReport),
            incidents = listOf(incident(id = "99", status = "EN_ROUTE"))
        )

        requireNotNull(selection)
        assertEquals("local-token", selection.trackingId)
        assertNull(selection.incident)
    }

    @Test
    fun `detailed reports do not start live SOS tracking`() {
        val selection = resolveCitizenOwnedSos(
            reports = listOf(
                report(
                    serverIncidentId = 44L,
                    submissionType = "DETAILED"
                )
            ),
            incidents = listOf(incident(id = "44", status = "EN_ROUTE"))
        )

        assertNull(selection)
    }

    private fun report(
        serverIncidentId: Long?,
        syncState: String = "SYNCED",
        transportToken: String = "token-44",
        submissionType: String = "QUICK_SOS",
        observedAt: Long = 1_000L
    ) = LocalIncidentReportEntity(
        reportUuid = "report-$observedAt-$transportToken",
        transportToken = transportToken,
        incidentUuid = null,
        submissionType = submissionType,
        observedTimestampMs = observedAt,
        syncState = syncState,
        serverIncidentId = serverIncidentId,
        logicalId = transportToken
    )

    private fun incident(
        id: String,
        status: String
    ) = IncidentEntity(
        incidentId = id,
        targetId = "TEST",
        emergencyType = "MEDICAL",
        latitude = 13.14,
        longitude = 123.73,
        timestamp = 1_000L,
        status = status,
        serverIncidentId = id.toLongOrNull()
    )
}
