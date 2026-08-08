package com.travelingsalesman.agap.core.bluetooth

import javax.inject.Inject
import javax.inject.Singleton

enum class UserRole {
    CITIZEN,
    RESPONDER,
    ADMIN;

    companion object {
        fun fromString(role: String?): UserRole {
            return when (role?.uppercase()) {
                "ADMIN" -> ADMIN
                "RESPONDER" -> RESPONDER
                else -> CITIZEN
            }
        }
    }
}

@Singleton
class BleVisibilityPolicy @Inject constructor() {

    fun shouldShowInUi(
        userRole: UserRole,
        isOwnReport: Boolean,
        emergencyType: String,
        responderAgencyType: String? = null
    ): Boolean {
        return when (userRole) {
            UserRole.CITIZEN -> isOwnReport
            UserRole.ADMIN -> true
            UserRole.RESPONDER -> {
                if (isOwnReport) return true
                if (responderAgencyType == null) return true
                return isRelevantToAgency(emergencyType, responderAgencyType)
            }
        }
    }

    private fun isRelevantToAgency(emergencyType: String, responderAgencyType: String): Boolean {
        return when (emergencyType.uppercase()) {
            "FIRE" -> responderAgencyType.uppercase() in listOf("FIRE", "BFP", "RESCUE", "DRRMO")
            "MEDICAL" -> responderAgencyType.uppercase() in listOf("MEDICAL", "HEALTH", "RESCUE", "DRRMO")
            "CRIME" -> responderAgencyType.uppercase() in listOf("POLICE", "PNP", "SECURITY")
            "ACCIDENT" -> responderAgencyType.uppercase() in listOf("RESCUE", "POLICE", "MEDICAL", "DRRMO")
            "DISASTER" -> true
            else -> true
        }
    }
}
