package com.travelingsalesman.agap.ui.theme

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CarCrash
import androidx.compose.material.icons.outlined.LocalFireDepartment
import androidx.compose.material.icons.outlined.LocalHospital
import androidx.compose.material.icons.outlined.Shield
import androidx.compose.material.icons.outlined.Warning
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import java.util.Locale

/**
 * Canonical presentation for incident emergency types.
 *
 * Agency classifications deliberately do not belong here: an agency's type
 * (for example, DRRMO or RESCUE) is a separate taxonomy from an incident's
 * emergency type.
 */
enum class EmergencyType(
    val label: String,
    val color: Color,
    val icon: ImageVector
) {
    MEDICAL("Medical", BlueMedical, Icons.Outlined.LocalHospital),
    FIRE("Fire", RedPrimary, Icons.Outlined.LocalFireDepartment),
    ACCIDENT("Accident", AmberWarning, Icons.Outlined.CarCrash),
    CRIME("Crime", PurpleCrime, Icons.Outlined.Shield),
    DISASTER("Disaster", PurpleDisaster, Icons.Outlined.Warning);

    companion object {
        /**
         * Resolves both catalog codes and legacy/free-form incident labels to
         * the canonical emergency type used for presentation.
         */
        fun fromCode(code: String?): EmergencyType {
            val normalized = code?.trim()?.uppercase(Locale.ROOT).orEmpty()

            return entries.firstOrNull { it.name == normalized }
                ?: when {
                    normalized.contains("MEDICAL") -> MEDICAL
                    normalized.contains("FIRE") -> FIRE
                    normalized.contains("CRIME") ||
                        normalized.contains("POLICE") ||
                        normalized.contains("SECURITY") -> CRIME
                    normalized.contains("ACCIDENT") ||
                        normalized.contains("TRAFFIC") ||
                        normalized.contains("VEHICULAR") -> ACCIDENT
                    normalized.contains("DISASTER") ||
                        normalized.contains("NATURAL") ||
                        normalized.contains("CALAMITY") -> DISASTER
                    else -> MEDICAL
                }
        }
    }
}

/**
 * Canonical presentation for agency classifications.
 *
 * This intentionally remains separate from [EmergencyType]: an agency may
 * handle several emergency types, and an emergency type may be handled by
 * several agency classifications.
 */
enum class AgencyType(
    val label: String,
    val color: Color
) {
    MEDICAL("Medical", BlueMedical),
    FIRE("Fire", RedPrimary),
    POLICE("Police", BluePolice),
    RESCUE("Rescue", AmberWarning),
    DRRMO("DRRMO", GreenOnline),
    VOLUNTEER("Volunteer", PurpleVolunteer);

    companion object {
        fun fromCode(code: String?): AgencyType? = when (
            code?.trim()?.uppercase(Locale.ROOT)
        ) {
            "MEDICAL", "HEALTH" -> MEDICAL
            "FIRE", "BFP" -> FIRE
            "POLICE", "PNP", "SECURITY" -> POLICE
            "RESCUE" -> RESCUE
            "DRRMO" -> DRRMO
            "VOLUNTEER" -> VOLUNTEER
            else -> null
        }
    }
}
