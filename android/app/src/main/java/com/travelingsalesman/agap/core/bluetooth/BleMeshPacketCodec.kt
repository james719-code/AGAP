package com.travelingsalesman.agap.core.bluetooth

import java.nio.ByteBuffer

enum class BleMeshPacketType(val code: Byte) {
    BEACON(1),
    SOS(2),
    ACK(3),
    STATUS(4);

    companion object {
        fun fromCode(code: Byte): BleMeshPacketType? = entries.firstOrNull { it.code == code }
    }
}

data class BleMeshPacket(
    val protocolVersion: Byte,
    val packetType: BleMeshPacketType,
    val emergencyType: String,
    val hopCount: Int,
    val compactId: ByteArray,
    val latitude: Double,
    val longitude: Double,
    val addressId: UShort = 0u,
    val messageCode: UShort = 0u
) {
    val packetUuid: String = compactId.joinToString("") { "%02x".format(it) }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is BleMeshPacket) return false
        return protocolVersion == other.protocolVersion &&
            packetType == other.packetType &&
            emergencyType == other.emergencyType &&
            hopCount == other.hopCount &&
            compactId.contentEquals(other.compactId) &&
            latitude == other.latitude &&
            longitude == other.longitude &&
            addressId == other.addressId &&
            messageCode == other.messageCode
    }

    override fun hashCode(): Int {
        var result = protocolVersion.toInt()
        result = 31 * result + packetType.hashCode()
        result = 31 * result + emergencyType.hashCode()
        result = 31 * result + hopCount
        result = 31 * result + compactId.contentHashCode()
        result = 31 * result + latitude.hashCode()
        result = 31 * result + longitude.hashCode()
        result = 31 * result + addressId.hashCode()
        result = 31 * result + messageCode.hashCode()
        return result
    }
}

object BleMeshPacketCodec {
    private val header = byteArrayOf(0x41, 0x47)
    const val PROTOCOL_VERSION: Byte = 1
    const val ENCODED_LENGTH = 21

    fun encode(
        packetType: BleMeshPacketType,
        emergencyType: String,
        hopCount: Int,
        compactId: ByteArray,
        latitude: Double,
        longitude: Double,
        addressId: UShort = 0u,
        messageCode: UShort = 0u
    ): ByteArray {
        val payload = ByteBuffer.allocate(ENCODED_LENGTH - 1)
        payload.put(header)
        payload.put(PROTOCOL_VERSION)
        payload.put(packetType.code)
        payload.put(emergencyTypeToByte(emergencyType))
        payload.put((hopCount and 0x0F).toByte())
        payload.put(ByteArray(8) { index -> compactId.getOrElse(index) { 0 } })
        payload.putShort(encodeLat(latitude))
        payload.putShort(encodeLon(longitude))
        val trailingCode = if (packetType == BleMeshPacketType.ACK || packetType == BleMeshPacketType.STATUS) {
            messageCode
        } else {
            addressId
        }
        payload.putShort(trailingCode.toShort())

        val body = payload.array()
        return body + checksum(body)
    }

    fun decode(data: ByteArray): BleMeshPacket? {
        if (data.size < ENCODED_LENGTH) return null
        val body = data.copyOfRange(0, ENCODED_LENGTH - 1)
        if (checksum(body) != data[ENCODED_LENGTH - 1]) return null
        if (body[0] != header[0] || body[1] != header[1]) return null
        val version = body[2]
        if (version != PROTOCOL_VERSION) return null
        val packetType = BleMeshPacketType.fromCode(body[3]) ?: return null
        val emergencyType = byteToEmergencyType(body[4])
        val hopCount = body[5].toInt() and 0x0F
        val compactId = body.copyOfRange(6, 14)
        val latShort = ByteBuffer.wrap(body, 14, 2).short
        val lonShort = ByteBuffer.wrap(body, 16, 2).short
        val trailingCode = (ByteBuffer.wrap(body, 18, 2).short.toInt() and 0xFFFF).toUShort()

        return BleMeshPacket(
            protocolVersion = version,
            packetType = packetType,
            emergencyType = emergencyType,
            hopCount = hopCount,
            compactId = compactId,
            latitude = decodeLat(latShort),
            longitude = decodeLon(lonShort),
            addressId = if (packetType == BleMeshPacketType.SOS) trailingCode else 0u,
            messageCode = if (packetType == BleMeshPacketType.ACK || packetType == BleMeshPacketType.STATUS) trailingCode else 0u
        )
    }

    private fun checksum(body: ByteArray): Byte {
        var sum = 0
        body.forEach { sum = (sum + (it.toInt() and 0xFF)) and 0xFF }
        return sum.toByte()
    }

    private fun emergencyTypeToByte(type: String): Byte = when (type.uppercase()) {
        "MEDICAL" -> 1
        "ACCIDENT" -> 2
        "FIRE" -> 3
        "DISASTER" -> 4
        "CRIME" -> 5
        else -> 0
    }

    private fun byteToEmergencyType(type: Byte): String = when (type.toInt() and 0xFF) {
        1 -> "MEDICAL"
        2 -> "ACCIDENT"
        3 -> "FIRE"
        4 -> "DISASTER"
        5 -> "CRIME"
        else -> "MEDICAL"
    }

    private fun encodeLat(lat: Double): Short {
        val clamped = lat.coerceIn(4.0, 21.0)
        return ((clamped - 4.0) * 3000).toInt().toShort()
    }

    private fun decodeLat(latShort: Short): Double {
        val raw = latShort.toInt() and 0xFFFF
        return 4.0 + (raw / 3000.0)
    }

    private fun encodeLon(lon: Double): Short {
        val clamped = lon.coerceIn(116.0, 127.0)
        return ((clamped - 116.0) * 3000).toInt().toShort()
    }

    private fun decodeLon(lonShort: Short): Double {
        val raw = lonShort.toInt() and 0xFFFF
        return 116.0 + (raw / 3000.0)
    }
}
