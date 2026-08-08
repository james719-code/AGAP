package com.travelingsalesman.agap.domain.model

enum class LocationSource(val code: Int) {
    GPS(0),
    MAP_PIN(1),
    BARANGAY_REFERENCE(2),
    UNKNOWN(3);

    companion object {
        fun fromCode(code: Int): LocationSource {
            return entries.firstOrNull { it.code == code } ?: UNKNOWN
        }

        fun fromString(value: String?): LocationSource {
            if (value == null) return UNKNOWN
            return try {
                valueOf(value.uppercase())
            } catch (e: Exception) {
                UNKNOWN
            }
        }
    }
}
