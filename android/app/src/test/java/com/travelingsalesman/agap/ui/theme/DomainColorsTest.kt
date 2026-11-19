package com.travelingsalesman.agap.ui.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
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

    @Test
    fun emergencyAccentContentAndBodyTextMeetNormalTextContrast() {
        fun contrast(a: Color, b: Color): Float {
            val aLuminance = a.luminance()
            val bLuminance = b.luminance()
            return (maxOf(aLuminance, bLuminance) + 0.05f) /
                (minOf(aLuminance, bLuminance) + 0.05f)
        }
        EmergencyType.entries.forEach { type ->
            assertTrue("${type.label} content contrast", contrast(type.color, contentColorForAccent(type.color)) >= 4.5f)
        }
        listOf(
            PaletteTextPrimaryLight to PaletteBgLight,
            PaletteTextSecondaryLight to PaletteBgLight,
            PaletteTextPrimaryDark to PaletteSurfaceDark,
            PaletteTextSecondaryDark to PaletteSurfaceDark,
            PaletteRedLight to Color.White,
            PaletteRedDark to contentColorForAccent(PaletteRedDark)
        ).forEach { (foreground, background) ->
            assertTrue("Theme text contrast", contrast(foreground, background) >= 4.5f)
        }
    }
}
