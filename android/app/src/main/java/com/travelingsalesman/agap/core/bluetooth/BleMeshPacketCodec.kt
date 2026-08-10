package com.travelingsalesman.agap.core.bluetooth

import com.travelingsalesman.agap.domain.model.LocationSource
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.security.SecureRandom

enum class BleMeshPacketType(val code: Byte) {
    SOS(0),
    INCIDENT_REPORT(1),
    ACK(2),
    STATUS(3);

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
    val statusCode: Int = 0,
    val originDeviceId: String? = null
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
            statusCode == other.statusCode &&
            originDeviceId == other.originDeviceId
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
        result = 31 * result + (originDeviceId?.hashCode() ?: 0)
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
        latitude: Double = 0.0,
        longitude: Double = 0.0,
        horizontalAccuracyMeters: Int = 255,
        creationTimeSlot: Int = (System.currentTimeMillis() / 60000L % 65536L).toInt(),
        originDeviceId: String? = null
    ): ByteArray {
        val base = encodeV2EmergencyPacket(
            packetType,
            emergencyType,
            immediateDanger,
            subjectRelation,
            hopCount,
            locationSource,
            transportToken,
            latitude,
            longitude,
            horizontalAccuracyMeters,
            creationTimeSlot
        )
        val identityBytes = originDeviceId
            ?.trim()
            ?.takeIf { it.isNotEmpty() }
            ?.toByteArray(Charsets.UTF_8)
            ?.take(MAX_ORIGIN_DEVICE_ID_BYTES)
            ?.toByteArray()
            ?: return base

        // v3 keeps the compact v2 body, replaces the version bits, and appends
        // a length-prefixed opaque origin identity plus an outer CRC.
        val frame = ByteArray(19 + 1 + identityBytes.size + 2)
        base.copyInto(frame, destinationOffset = 0, startIndex = 0, endIndex = 19)
        frame[0] = ((frame[0].toInt() and 0x3F) or (3 shl 6)).toByte()
        frame[19] = identityBytes.size.toByte()
        identityBytes.copyInto(frame, destinationOffset = 20)
        val crcOffset = 20 + identityBytes.size
        val crc = crc16(frame, 0, crcOffset)
        frame[crcOffset] = (crc shr 8).toByte()
        frame[crcOffset + 1] = crc.toByte()
        return frame
    }

    private fun encodeV2EmergencyPacket(
        packetType: BleMeshPacketType,
        emergencyType: String,
        immediateDanger: Boolean,
        subjectRelation: String,
        hopCount: Int,
        locationSource: LocationSource,
        transportToken: ByteArray,
        latitude: Double,
        longitude: Double,
        horizontalAccuracyMeters: Int,
        creationTimeSlot: Int
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

        val latE7 = kotlin.math.round(latitude * 10_000_000).toInt()
        val lonE7 = kotlin.math.round(longitude * 10_000_000).toInt()

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
        val versionBits = data.firstOrNull()?.toInt()?.ushr(6)?.and(0x03)
        if (versionBits == 3 && data.size >= 22) {
            val identityLength = data[19].toInt() and 0xFF
            val crcOffset = 20 + identityLength
            if (identityLength > MAX_ORIGIN_DEVICE_ID_BYTES || data.size != crcOffset + 2) return null
            val expected = ((data[crcOffset].toInt() and 0xFF) shl 8) or
                (data[crcOffset + 1].toInt() and 0xFF)
            if (crc16(data, 0, crcOffset) != expected) return null

            val base = data.copyOfRange(0, 19)
            base[0] = ((base[0].toInt() and 0x3F) or (2 shl 6)).toByte()
            val baseWithCrc = base + byteArrayOf(0, 0)
            val baseCrc = crc16(baseWithCrc, 0, 19)
            baseWithCrc[19] = (baseCrc shr 8).toByte()
            baseWithCrc[20] = baseCrc.toByte()
            val decoded = decode(baseWithCrc) ?: return null
            val origin = data.copyOfRange(20, crcOffset).toString(Charsets.UTF_8).trim()
            return decoded.copy(protocolVersion = 3, originDeviceId = origin.ifBlank { null })
        }
        if (data.size == EMERGENCY_PACKET_LENGTH) {
            val crcCalc = crc16(data, 0, 19)
            val crcExpected = ByteBuffer.wrap(data, 19, 2).order(ByteOrder.BIG_ENDIAN).short.toInt() and 0xFFFF
            if (crcCalc != crcExpected) {
                BleMeshLog.w(
                    "CODEC_CRC_REJECT",
                    "kind" to "EMERGENCY",
                    "length" to data.size,
                    "calculated" to crcCalc,
                    "expected" to crcExpected
                )
                return null
            }

            val b0 = data[0].toInt() and 0xFF
            val version = (b0 shr 6) and 0x03
            if (version != 2) {
                BleMeshLog.w("CODEC_VERSION_REJECT", "kind" to "EMERGENCY", "version" to version)
                return null
            }

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
            if (crcCalc != crcExpected) {
                BleMeshLog.w(
                    "CODEC_CRC_REJECT",
                    "kind" to "RESPONSE",
                    "length" to data.size,
                    "calculated" to crcCalc,
                    "expected" to crcExpected
                )
                return null
            }

            val b0 = data[0].toInt() and 0xFF
            val version = (b0 shr 6) and 0x03
            if (version != 2) {
                BleMeshLog.w("CODEC_VERSION_REJECT", "kind" to "RESPONSE", "version" to version)
                return null
            }

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
        } else if (data.size >= 2 && data[0] == 'A'.code.toByte() && data[1] == 'G'.code.toByte()) {

            val version = if (data.size > 2) data[2] else 1.toByte()
            val token = if (data.size >= 9) data.copyOfRange(3, 9) else ByteArray(6)
            val eCode = if (data.size >= 10) data[9].toInt() and 0xFF else 0
            val hop = if (data.size >= 11) data[10].toInt() and 0x0F else 0
            return BleMeshPacket(
                protocolVersion = version,
                packetType = BleMeshPacketType.SOS,
                emergencyType = codeToEmergencyType(eCode),
                hopCount = hop,
                locationSource = LocationSource.UNKNOWN,
                transportToken = token
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
        "LOCATION_UPDATE" -> 6
        else -> 0
    }

    private fun codeToEmergencyType(code: Int): String = when (code) {
        0 -> "UNKNOWN"
        1 -> "MEDICAL"
        2 -> "ACCIDENT"
        3 -> "FIRE"
        4 -> "DISASTER"
        5 -> "CRIME"
        6 -> "LOCATION_UPDATE"
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

    private const val MAX_ORIGIN_DEVICE_ID_BYTES = 32
}
