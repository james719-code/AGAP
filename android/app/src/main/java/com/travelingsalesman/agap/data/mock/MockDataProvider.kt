package com.travelingsalesman.agap.data.mock

import com.travelingsalesman.agap.data.local.entity.IncidentEntity
import com.travelingsalesman.agap.data.remote.dto.AgencyDto
import com.travelingsalesman.agap.data.remote.dto.ResponderUnitDto
import com.travelingsalesman.agap.data.remote.dto.UserDto

/**
 * Clean Mock Data Provider for UI demonstration & offline testing.
 * 
 * TO REMOVE / DISABLE MOCK DATA:
 * Set [ENABLE_MOCK_DATA] = false, or delete this file when full production backend integration is complete.
 */
object MockDataProvider {

    /** Master toggle for UI demo mock fallback data */
    const val ENABLE_MOCK_DATA = false

    val sampleUser = UserDto(
        id = 1L,
        username = "Officer Juan Dela Cruz",
        role = "RESPONDER",
        deviceId = "DEV-ALBAY-001",
        agencyId = 101L
    )

    val sampleCitizenUser = UserDto(
        id = 2L,
        username = "Maria Santos",
        role = "CITIZEN",
        deviceId = "DEV-ALBAY-002",
        agencyId = null
    )

    val sampleAgency = AgencyDto(
        id = 101L,
        code = "CDRRMO-LEG",
        name = "Legazpi City CDRRMO Emergency Command",
        type = "RESCUE",
        regionCode = "5",
        provinceCode = "ALBAY",
        muniCode = "LEGAZPI",
        barangayCode = "CENTRAL",
        contactNumber = "+63 (52) 742-1234",
        latitude = 13.1391,
        longitude = 123.7438
    )

    val sampleIncidents = listOf(
        IncidentEntity(
            incidentId = "INC-2026-001",
            targetId = "05-05-12-01-01::Peñaranda-Park-Node",
            emergencyType = "MEDICAL",
            latitude = 13.1391,
            longitude = 123.7438,
            timestamp = System.currentTimeMillis() - 1000 * 60 * 15, // 15 mins ago
            status = "DISPATCHED"
        ),
        IncidentEntity(
            incidentId = "INC-2026-002",
            targetId = "05-05-12-01-04::Rizal-St-Intersect",
            emergencyType = "ACCIDENT",
            latitude = 13.1415,
            longitude = 123.7380,
            timestamp = System.currentTimeMillis() - 1000 * 60 * 45, // 45 mins ago
            status = "PENDING"
        ),
        IncidentEntity(
            incidentId = "INC-2026-003",
            targetId = "05-05-12-02-09::Daraga-Station",
            emergencyType = "FIRE",
            latitude = 13.1492,
            longitude = 123.7126,
            timestamp = System.currentTimeMillis() - 1000 * 60 * 120, // 2 hours ago
            status = "ACKNOWLEDGED"
        ),
        IncidentEntity(
            incidentId = "INC-2026-004",
            targetId = "05-05-12-01-12::Port-District-Hub",
            emergencyType = "CRIME",
            latitude = 13.1428,
            longitude = 123.7545,
            timestamp = System.currentTimeMillis() - 1000 * 60 * 240, // 4 hours ago
            status = "RESOLVED"
        )
    )

    val sampleResponders = listOf(
        ResponderUnitDto(
            id = 1L,
            agencyId = 101L,
            targetId = "RES-UNIT-01",
            unitCallsign = "RESCUE-ALPHA-1",
            deviceId = "DEV-ALBAY-001",
            status = "DISPATCHED",
            currentLat = 13.1398,
            currentLon = 123.7442,
            lastSeenAt = "2026-08-05 22:30:00"
        ),
        ResponderUnitDto(
            id = 2L,
            agencyId = 101L,
            targetId = "RES-UNIT-02",
            unitCallsign = "BFP-ENGINE-101",
            deviceId = "DEV-ALBAY-002",
            status = "DISPATCHED",
            currentLat = 13.1485,
            currentLon = 123.7130,
            lastSeenAt = "2026-08-05 22:28:15"
        ),
        ResponderUnitDto(
            id = 3L,
            agencyId = 101L,
            targetId = "RES-UNIT-03",
            unitCallsign = "PNP-PATROL-402",
            deviceId = "DEV-ALBAY-003",
            status = "IDLE",
            currentLat = 13.1410,
            currentLon = 123.7375,
            lastSeenAt = "2026-08-05 22:35:10"
        ),
        ResponderUnitDto(
            id = 4L,
            agencyId = 101L,
            targetId = "RES-UNIT-04",
            unitCallsign = "RED-CROSS-MEDIC",
            deviceId = "DEV-ALBAY-004",
            status = "IDLE",
            currentLat = 13.1435,
            currentLon = 123.7530,
            lastSeenAt = "2026-08-05 22:33:45"
        )
    )
}
