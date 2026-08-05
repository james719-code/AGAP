package com.travelingsalesman.agap.core.util

import java.util.UUID

object TargetIdEncoder {
    fun encodeTargetId(
        region: String = "05",
        province: String = "05",
        municipality: String = "12",
        barangay: String = "01",
        zone: String = "04",
        uuid: String = UUID.randomUUID().toString()
    ): String {
        val address = "$region-$province-$municipality-$barangay-$zone"
        return "$address::$uuid"
    }

    fun parseCodedAddress(targetId: String): String {
        return targetId.substringBefore("::")
    }

    fun parseUuid(targetId: String): String {
        return targetId.substringAfter("::", "")
    }
}
