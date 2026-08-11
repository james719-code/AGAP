package com.travelingsalesman.agap.ui.theme

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Test

class DomainColorsTest {

    @Test
    fun emergencyTypeNormalizesCatalogAndLegacyCodes() {
        assertEquals(EmergencyType.MEDICAL, EmergencyType.fromCode("medical"))
        assertEquals(EmergencyType.FIRE, EmergencyType.fromCode("Fire incident"))
        assertEquals(EmergencyType.ACCIDENT, EmergencyType.fromCode("vehicular traffic accident"))
        assertEquals(EmergencyType.CRIME, EmergencyType.fromCode("POLICE"))
        assertEquals(EmergencyType.CRIME, EmergencyType.fromCode("security concern"))
        assertEquals(EmergencyType.DISASTER, EmergencyType.fromCode("Natural calamity"))
        assertEquals(EmergencyType.MEDICAL, EmergencyType.fromCode(null))
    }

    @Test
    fun disasterPresentationIsDistinctFromCrime() {
        assertEquals("Disaster", EmergencyType.DISASTER.label)
        assertEquals(PurpleDisaster, EmergencyType.DISASTER.color)
        assertNotEquals(EmergencyType.CRIME.color, EmergencyType.DISASTER.color)
    }

    @Test
    fun agencyPresentationIsKeptInItsOwnTaxonomy() {
        assertEquals(AgencyType.MEDICAL, AgencyType.fromCode("HEALTH"))
        assertEquals(AgencyType.FIRE, AgencyType.fromCode("BFP"))
        assertEquals(AgencyType.POLICE, AgencyType.fromCode("PNP"))
        assertEquals(AgencyType.DRRMO, AgencyType.fromCode("DRRMO"))
        assertNull(AgencyType.fromCode("UNKNOWN"))
    }
}
