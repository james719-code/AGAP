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

    fun compactAddressId(targetId: String): UShort {
        val codedAddress = parseCodedAddress(targetId)
        var hash = 0
        codedAddress.forEach { ch ->
            hash = ((hash * 31) + ch.code) and 0xFFFF
        }
        return hash.toUShort()
    }

    fun encodeRelayTargetId(addressId: UShort, packetUuid: String): String {
        val address = "BLE-%05d".format(addressId.toInt())
        return "$address::$packetUuid"
    }
}
