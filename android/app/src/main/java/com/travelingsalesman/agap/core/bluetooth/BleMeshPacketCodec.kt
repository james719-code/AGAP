package com.travelingsalesman.agap.core.bluetooth

import com.travelingsalesman.agap.domain.model.LocationSource
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.security.SecureRandom

enum class BleMeshPacketType(val code: Byte) {
    SOS(0),
    INCIDENT_REPORT(1),
    ACK(2),
    STATUS(3),
    BEACON(4);

    companion object {
        fun fromCode(code: Byte): BleMeshPacketType? = entries.firstOrNull { it.code == code }
    }
}

data class BleMeshPacket(
    val protocolVersion: Byte,
    val packetType: BleMeshPacketType,
    val emergencyType: String,
    val immediateDanger: Boolean = false,
    val subjectRelation: String = "SELF",
    val hopCount: Int = 0,
    val locationSource: LocationSource = LocationSource.GPS,
    val transportToken: ByteArray = ByteArray(6),
    val latitude: Double = 0.0,
    val longitude: Double = 0.0,
    val horizontalAccuracyMeters: Int = 255,
    val creationTimeSlot: Int = 0,
    val statusCode: Int = 0
) {
    val transportTokenHex: String = transportToken.joinToString("") { "%02x".format(it) }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is BleMeshPacket) return false
        return protocolVersion == other.protocolVersion &&
            packetType == other.packetType &&
            emergencyType == other.emergencyType &&
            immediateDanger == other.immediateDanger &&
            subjectRelation == other.subjectRelation &&
            hopCount == other.hopCount &&
            locationSource == other.locationSource &&
            transportToken.contentEquals(other.transportToken) &&
            latitude == other.latitude &&
            longitude == other.longitude &&
            horizontalAccuracyMeters == other.horizontalAccuracyMeters &&
            creationTimeSlot == other.creationTimeSlot &&
            statusCode == other.statusCode
    }

    override fun hashCode(): Int {
        var result = protocolVersion.toInt()
        result = 31 * result + packetType.hashCode()
        result = 31 * result + emergencyType.hashCode()
        result = 31 * result + immediateDanger.hashCode()
        result = 31 * result + subjectRelation.hashCode()
        result = 31 * result + hopCount
        result = 31 * result + locationSource.hashCode()
        result = 31 * result + transportToken.contentHashCode()
        result = 31 * result + latitude.hashCode()
        result = 31 * result + longitude.hashCode()
        result = 31 * result + horizontalAccuracyMeters
        result = 31 * result + creationTimeSlot
        result = 31 * result + statusCode
        return result
    }
}

object BleMeshPacketCodec {
    const val PROTOCOL_VERSION_V2: Byte = 2
    const val EMERGENCY_PACKET_LENGTH = 21
    const val RESPONSE_PACKET_LENGTH = 13

    private val rng = SecureRandom()

    fun generateTransportToken(): ByteArray {
        val token = ByteArray(6)
        rng.nextBytes(token)
        return token
    }

    fun crc16(data: ByteArray, off: Int = 0, len: Int = data.size): Int {
        var crc = 0xFFFF
        for (i in off until (off + len)) {
            val b = data[i].toInt() and 0xFF
            crc = crc xor (b shl 8)
            for (j in 0 until 8) {
                crc = if ((crc and 0x8000) != 0) {
                    (crc shl 1) xor 0x1021
                } else {
                    crc shl 1
                }
                crc = crc and 0xFFFF
            }
        }
        return crc and 0xFFFF
    }

    fun encodeEmergencyPacket(
        packetType: BleMeshPacketType,
        emergencyType: String,
        immediateDanger: Boolean,
        subjectRelation: String,
        hopCount: Int,
        locationSource: LocationSource,
        transportToken: ByteArray,
        latitude: Double,
        longitude: Double,
        horizontalAccuracyMeters: Int = 255,
        creationTimeSlot: Int = (System.currentTimeMillis() / 60000L % 65536L).toInt()
    ): ByteArray {
        val buf = ByteBuffer.allocate(EMERGENCY_PACKET_LENGTH).order(ByteOrder.BIG_ENDIAN)

        val ver = 0x02 shl 6
        val pType = (packetType.code.toInt() and 0x03) shl 4
        val eCode = (emergencyTypeToCode(emergencyType) and 0x07) shl 1
        val danger = if (immediateDanger) 1 else 0
        val byte0 = (ver or pType or eCode or danger).toByte()

        val rel = (subjectRelationToCode(subjectRelation) and 0x03) shl 6
        val hop = (hopCount.coerceIn(0, 15) and 0x0F) shl 2
        val locSrc = locationSource.code and 0x03
        val byte1 = (rel or hop or locSrc).toByte()

        buf.put(byte0)
        buf.put(byte1)

        val tokenBytes = ByteArray(6) { i -> transportToken.getOrElse(i) { 0 } }
        buf.put(tokenBytes)

        val latE7 = (latitude * 10_000_000).toInt()
        val lonE7 = (longitude * 10_000_000).toInt()
        buf.putInt(latE7)
        buf.putInt(lonE7)

        val acc = horizontalAccuracyMeters.coerceIn(0, 255).toByte()
        buf.put(acc)

        buf.putShort(creationTimeSlot.toShort())

        val crcPayload = buf.array().copyOfRange(0, 19)
        val crc = crc16(crcPayload)
        buf.putShort(crc.toShort())

        return buf.array()
    }

    fun encodeResponsePacket(
        packetType: BleMeshPacketType,
        hopCount: Int,
        transportToken: ByteArray,
        statusCode: Int,
        creationTimeSlot: Int = (System.currentTimeMillis() / 60000L % 65536L).toInt()
    ): ByteArray {
        val buf = ByteBuffer.allocate(RESPONSE_PACKET_LENGTH).order(ByteOrder.BIG_ENDIAN)

        val ver = 0x02 shl 6
        val pType = (packetType.code.toInt() and 0x03) shl 4
        val byte0 = (ver or pType).toByte()

        val hop = (hopCount.coerceIn(0, 15) and 0x0F) shl 4
        val flags = statusCode and 0x0F
        val byte1 = (hop or flags).toByte()

        buf.put(byte0)
        buf.put(byte1)

        val tokenBytes = ByteArray(6) { i -> transportToken.getOrElse(i) { 0 } }
        buf.put(tokenBytes)

        buf.put((statusCode and 0xFF).toByte())
        buf.putShort(creationTimeSlot.toShort())

        val crcPayload = buf.array().copyOfRange(0, 11)
        val crc = crc16(crcPayload)
        buf.putShort(crc.toShort())

        return buf.array()
    }

    fun decode(data: ByteArray): BleMeshPacket? {
        if (data.size == EMERGENCY_PACKET_LENGTH) {
            val crcCalc = crc16(data, 0, 19)
            val crcExpected = ByteBuffer.wrap(data, 19, 2).order(ByteOrder.BIG_ENDIAN).short.toInt() and 0xFFFF
            if (crcCalc != crcExpected) return null

            val b0 = data[0].toInt() and 0xFF
            val version = (b0 shr 6) and 0x03
            if (version != 2) return null

            val pType = (b0 shr 4) and 0x03
            val packetType = BleMeshPacketType.fromCode(pType.toByte()) ?: return null
            val eCode = (b0 shr 1) and 0x07
            val immediateDanger = (b0 and 0x01) == 1

            val b1 = data[1].toInt() and 0xFF
            val relCode = (b1 shr 6) and 0x03
            val hopCount = (b1 shr 2) and 0x0F
            val locCode = b1 and 0x03

            val transportToken = data.copyOfRange(2, 8)
            val latE7 = ByteBuffer.wrap(data, 8, 4).order(ByteOrder.BIG_ENDIAN).int
            val lonE7 = ByteBuffer.wrap(data, 12, 4).order(ByteOrder.BIG_ENDIAN).int
            val accuracy = data[16].toInt() and 0xFF
            val creationTimeSlot = ByteBuffer.wrap(data, 17, 2).order(ByteOrder.BIG_ENDIAN).short.toInt() and 0xFFFF

            return BleMeshPacket(
                protocolVersion = version.toByte(),
                packetType = packetType,
                emergencyType = codeToEmergencyType(eCode),
                immediateDanger = immediateDanger,
                subjectRelation = codeToSubjectRelation(relCode),
                hopCount = hopCount,
                locationSource = LocationSource.fromCode(locCode),
                transportToken = transportToken,
                latitude = latE7 / 10_000_000.0,
                longitude = lonE7 / 10_000_000.0,
                horizontalAccuracyMeters = accuracy,
                creationTimeSlot = creationTimeSlot
            )
        } else if (data.size == RESPONSE_PACKET_LENGTH) {
            val crcCalc = crc16(data, 0, 11)
            val crcExpected = ByteBuffer.wrap(data, 11, 2).order(ByteOrder.BIG_ENDIAN).short.toInt() and 0xFFFF
            if (crcCalc != crcExpected) return null

            val b0 = data[0].toInt() and 0xFF
            val version = (b0 shr 6) and 0x03
            if (version != 2) return null

            val pType = (b0 shr 4) and 0x03
            val packetType = BleMeshPacketType.fromCode(pType.toByte()) ?: return null

            val b1 = data[1].toInt() and 0xFF
            val hopCount = (b1 shr 4) and 0x0F
            val transportToken = data.copyOfRange(2, 8)
            val messageCode = data[8].toInt() and 0xFF
            val creationTimeSlot = ByteBuffer.wrap(data, 9, 2).order(ByteOrder.BIG_ENDIAN).short.toInt() and 0xFFFF

            return BleMeshPacket(
                protocolVersion = version.toByte(),
                packetType = packetType,
                emergencyType = "UNKNOWN",
                hopCount = hopCount,
                transportToken = transportToken,
                statusCode = messageCode,
                creationTimeSlot = creationTimeSlot
            )
        }
        return null
    }

    private fun emergencyTypeToCode(type: String): Int = when (type.uppercase()) {
        "UNKNOWN" -> 0
        "MEDICAL" -> 1
        "ACCIDENT" -> 2
        "FIRE" -> 3
        "DISASTER" -> 4
        "CRIME" -> 5
        else -> 0
    }

    private fun codeToEmergencyType(code: Int): String = when (code) {
        0 -> "UNKNOWN"
        1 -> "MEDICAL"
        2 -> "ACCIDENT"
        3 -> "FIRE"
        4 -> "DISASTER"
        5 -> "CRIME"
        else -> "UNKNOWN"
    }

    private fun subjectRelationToCode(rel: String): Int = when (rel.uppercase()) {
        "SELF" -> 0
        "OTHER_PERSON" -> 1
        "PUBLIC_OR_UNKNOWN" -> 2
        else -> 0
    }

    private fun codeToSubjectRelation(code: Int): String = when (code) {
        0 -> "SELF"
        1 -> "OTHER_PERSON"
        2 -> "PUBLIC_OR_UNKNOWN"
        else -> "SELF"
    }
}
