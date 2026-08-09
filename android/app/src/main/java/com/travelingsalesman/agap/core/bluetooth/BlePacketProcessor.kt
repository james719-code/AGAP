package com.travelingsalesman.agap.core.bluetooth

import com.travelingsalesman.agap.data.local.dao.IncidentDao
import com.travelingsalesman.agap.data.local.dao.MeshDeliveryEventDao
import com.travelingsalesman.agap.data.local.dao.MessageDao
import com.travelingsalesman.agap.data.local.dao.PredefinedMessageDao
import com.travelingsalesman.agap.data.local.dao.PendingPacketDao
import com.travelingsalesman.agap.data.local.dao.RelayDao
import com.travelingsalesman.agap.data.local.dao.ReportDao
import com.travelingsalesman.agap.data.local.entity.IncidentEntity
import com.travelingsalesman.agap.data.local.entity.IncidentMessageEntity
import com.travelingsalesman.agap.data.local.entity.MeshDeliveryEventEntity
import com.travelingsalesman.agap.data.local.entity.PendingPacketEntity
import com.travelingsalesman.agap.data.local.entity.SeenMeshPacketEntity
import com.travelingsalesman.agap.core.notification.EmergencyNotificationCoordinator
import java.util.UUID
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class BlePacketProcessor @Inject constructor(
    private val relayDao: RelayDao,
    private val incidentDao: IncidentDao,
    private val reportDao: ReportDao,
    private val pendingPacketDao: PendingPacketDao,
    private val meshDeliveryEventDao: MeshDeliveryEventDao,
    private val messageDao: MessageDao,
    private val predefinedMessageDao: PredefinedMessageDao,
    private val relayScheduler: BleRelayScheduler,
    private val visibilityPolicy: BleVisibilityPolicy,
    private val emergencyNotifications: EmergencyNotificationCoordinator
) {
    private val receiveMutex = Mutex()

    suspend fun processIncomingPacket(
        data: ByteArray,
        userRole: UserRole = UserRole.CITIZEN,
        userAgencyType: String? = null,
        sourcePeerId: String? = null
    ): BleMeshPacket? = receiveMutex.withLock {
        processIncomingPacketLocked(data, userRole, userAgencyType, sourcePeerId)
    }

    /**
     * All transports enter this method through receiveMutex. Nearby P2P_CLUSTER
     * may invoke callbacks from several peers concurrently, while the raw GATT
     * fallback can also receive at the same time. Serializing this critical
     * section prevents duplicate-check / persistence races.
     */
    private suspend fun processIncomingPacketLocked(
        data: ByteArray,
        userRole: UserRole,
        userAgencyType: String?,
        sourcePeerId: String?
    ): BleMeshPacket? {
        val packet = BleMeshPacketCodec.decode(data) ?: run {
            BleMeshLog.w("PROCESS_DECODE_REJECTED", "bytes" to data.size)
            return null
        }
        if (packet.hopCount >= BleRelayScheduler.MAX_HOP) {
            BleMeshLog.w(
                "PROCESS_HOP_LIMIT_DROP",
                "token" to BleMeshLog.token(packet.transportTokenHex),
                "type" to packet.packetType.name,
                "hop" to packet.hopCount
            )
            return null
        }
        if (isExpired(packet)) {
            BleMeshLog.w(
                "PROCESS_EXPIRED_DROP",
                "token" to BleMeshLog.token(packet.transportTokenHex),
                "type" to packet.packetType.name,
                "slot" to packet.creationTimeSlot
            )
            return null
        }

        val tokenHex = packet.transportTokenHex
        val identity = packet.seenIdentity()
        val now = System.currentTimeMillis()
        val seen = relayDao.getSeenPacket(tokenHex, identity)

        if (seen != null) {
            BleMeshLog.d(
                "PROCESS_DUPLICATE_DROP",
                "token" to BleMeshLog.token(tokenHex),
                "identity" to identity,
                "hop" to packet.hopCount,
                "highestHop" to seen.highestHop
            )
            // Repeated Android scan callbacks are normal. Update freshness, but do not
            // create another incident or relay row for the same logical packet.
            relayDao.insertSeenPacket(
                seen.copy(
                    highestHop = maxOf(seen.highestHop, packet.hopCount),
                    lastSeenMs = now
                )
            )
            return null
        }

        if (relayDao.isTokenTerminal(tokenHex) && packet.packetType != BleMeshPacketType.STATUS) {
            BleMeshLog.d(
                "PROCESS_TERMINAL_TOKEN_DROP",
                "token" to BleMeshLog.token(tokenHex),
                "type" to packet.packetType.name
            )
            return null
        }

        val ttlMs = if (
            packet.packetType == BleMeshPacketType.ACK || packet.packetType == BleMeshPacketType.STATUS
        ) {
            BleRelayScheduler.ACK_STATUS_TTL_MS
        } else {
            BleRelayScheduler.SOS_REPORT_TTL_MS
        }

        BleMeshLog.i(
            "PROCESS_ACCEPT",
            "token" to BleMeshLog.token(tokenHex),
            "identity" to identity,
            "hop" to packet.hopCount,
            "role" to userRole.name,
            "agency" to (userAgencyType ?: "none")
        )

        relayDao.insertSeenPacket(
            SeenMeshPacketEntity(
                transportToken = tokenHex,
                packetType = identity,
                highestHop = packet.hopCount,
                firstSeenMs = now,
                lastSeenMs = now,
                expiryMs = now + ttlMs,
                deliveryState = "RECEIVED"
            )
        )

        meshDeliveryEventDao.insertEvent(
            MeshDeliveryEventEntity(
                packetHash = tokenHex,
                packetType = packet.packetType.name,
                eventType = "RECEIVED",
                // Persist the stable source node when the transport knows it. This
                // lets the next relay hop avoid immediately bouncing back to sender.
                deviceIdentifier = sourcePeerId ?: tokenHex,
                latitude = packet.latitude.takeIf { packet.packetType == BleMeshPacketType.SOS || packet.packetType == BleMeshPacketType.INCIDENT_REPORT },
                longitude = packet.longitude.takeIf { packet.packetType == BleMeshPacketType.SOS || packet.packetType == BleMeshPacketType.INCIDENT_REPORT },
                hopCount = packet.hopCount
            )
        )

        when (packet.packetType) {
            BleMeshPacketType.SOS,
            BleMeshPacketType.INCIDENT_REPORT -> processEmergencyPacket(packet, userRole, userAgencyType, now)

            BleMeshPacketType.ACK,
            BleMeshPacketType.STATUS -> processResponsePacket(packet)
        }

        if (packet.hopCount < BleRelayScheduler.MAX_HOP) {
            val nextHopPacket = packet.copy(hopCount = packet.hopCount + 1)
            val relayBytes = encodeRelayPacket(nextHopPacket)
            if (relayBytes != null) {
                val relayQueueId = relayScheduler.enqueueForRelay(nextHopPacket, relayBytes, isOriginLocal = false)
                BleMeshLog.i(
                    "PROCESS_RELAY_QUEUED",
                    "token" to BleMeshLog.token(tokenHex),
                    "type" to nextHopPacket.packetType.name,
                    "status" to nextHopPacket.statusCode,
                    "hop" to nextHopPacket.hopCount,
                    "queueId" to relayQueueId
                )
                meshDeliveryEventDao.insertEvent(
                    MeshDeliveryEventEntity(
                        packetHash = tokenHex,
                        packetType = packet.packetType.name,
                        eventType = "RELAY_QUEUED",
                        deviceIdentifier = tokenHex,
                        latitude = packet.latitude.takeIf { packet.packetType == BleMeshPacketType.SOS || packet.packetType == BleMeshPacketType.INCIDENT_REPORT },
                        longitude = packet.longitude.takeIf { packet.packetType == BleMeshPacketType.SOS || packet.packetType == BleMeshPacketType.INCIDENT_REPORT },
                        hopCount = nextHopPacket.hopCount
                    )
                )
            }
        }

        return packet
    }

    private suspend fun processEmergencyPacket(
        packet: BleMeshPacket,
        userRole: UserRole,
        userAgencyType: String?,
        now: Long
    ) {
        val tokenHex = packet.transportTokenHex
        val isOwnReport = reportDao.getReportByToken(tokenHex) != null
        BleMeshLog.d(
            "EMERGENCY_PACKET_PROCESS",
            "token" to BleMeshLog.token(tokenHex),
            "own" to isOwnReport,
            "type" to packet.packetType.name,
            "emergency" to packet.emergencyType,
            "hop" to packet.hopCount
        )

        // Store a BLE-origin packet as a future gateway uplink candidate. In BLE-only
        // mode the network policy prevents this row from being uploaded; if the user
        // later switches to AUTO/Internet, the packet may bridge to the backend.
        if (!isOwnReport && pendingPacketDao.getPacketByHash(tokenHex) == null) {
            pendingPacketDao.insertPacket(
                PendingPacketEntity(
                    packetHash = tokenHex,
                    incidentUuid = tokenHex,
                    packetUuid = tokenHex,
                    targetId = "BLE-BLE-BLE-BLE::$tokenHex",
                    emergencyType = packet.emergencyType,
                    latitude = packet.latitude,
                    longitude = packet.longitude,
                    timestamp = now,
                    hopCount = packet.hopCount,
                    encryptedPayload = "BLE_MESH_V${packet.protocolVersion}_TOKEN_$tokenHex"
                )
            )
            BleMeshLog.i(
                "GATEWAY_PACKET_STORED",
                "token" to BleMeshLog.token(tokenHex),
                "hop" to packet.hopCount,
                "internetDeferred" to true
            )
        }

        val shouldShow = visibilityPolicy.shouldShowInUi(
            userRole = userRole,
            isOwnReport = isOwnReport,
            emergencyType = packet.emergencyType,
            responderAgencyType = userAgencyType
        )

        if (!shouldShow) {
            BleMeshLog.d(
                "UI_VISIBILITY_DROP",
                "token" to BleMeshLog.token(tokenHex),
                "role" to userRole.name,
                "agency" to (userAgencyType ?: "none"),
                "emergency" to packet.emergencyType
            )
            return
        }

        val existingIncident = incidentDao.getIncidentByPacketHash(tokenHex)
        if (existingIncident == null) {
            incidentDao.addIncident(
                IncidentEntity(
                    incidentId = UUID.randomUUID().toString(),
                    targetId = "BLE-BLE-BLE-BLE::$tokenHex",
                    emergencyType = packet.emergencyType,
                    latitude = packet.latitude,
                    longitude = packet.longitude,
                    timestamp = now,
                    status = "RECEIVED",
                    description = "Received through AGAP BLE mesh • Token ${tokenHex.take(6)}",
                    packetHash = tokenHex,
                    channel = "BLE_MESH",
                    deliveryPath = "BLE_CLOUD_SHORTCUT",
                    senderDeviceId = "BLE_PEER",
                    isDirty = true
                )
            )
            BleMeshLog.i(
                "INCIDENT_CREATED_FROM_BLE",
                "token" to BleMeshLog.token(tokenHex),
                "emergency" to packet.emergencyType,
                "hop" to packet.hopCount
            )
            if (userRole == UserRole.ADMIN || userRole == UserRole.RESPONDER) {
                emergencyNotifications.recordRelayEmergency(
                    transportToken = packet.transportTokenHex,
                    emergencyType = packet.emergencyType,
                    hopCount = packet.hopCount
                )
            }
        } else if (existingIncident.status !in TERMINAL_STATUSES) {
            // A later relay may carry the same origin coordinates; only update when
            // they actually changed.
            if (existingIncident.latitude != packet.latitude || existingIncident.longitude != packet.longitude) {
                incidentDao.addIncident(
                    existingIncident.copy(
                        latitude = packet.latitude,
                        longitude = packet.longitude,
                        isDirty = true
                    )
                )
            }
        }
    }

    private suspend fun processResponsePacket(packet: BleMeshPacket) {
        val tokenHex = packet.transportTokenHex
        val predefined = predefinedMessageDao.getMessageByProtocolCode(packet.statusCode)
        val fallbackStatus = statusName(packet)
        val incidentStatus = predefined?.code?.let(::incidentStatusForCatalogCode) ?: fallbackStatus
        val messageText = predefined?.messageText ?: fallbackStatus.replace('_', ' ').lowercase()
            .replaceFirstChar { if (it.isLowerCase()) it.titlecase() else it.toString() }

        BleMeshLog.i(
            "RESPONSE_PACKET_PROCESS",
            "token" to BleMeshLog.token(tokenHex),
            "type" to packet.packetType.name,
            "statusCode" to packet.statusCode,
            "catalogCode" to (predefined?.code ?: "unknown"),
            "status" to incidentStatus,
            "hop" to packet.hopCount
        )

        val existingIncident = incidentDao.getIncidentByPacketHash(tokenHex)
        if (existingIncident != null) {
            // The compact relay carries only token + protocol code. Resolve the
            // human-readable text from the server catalog cached in Room.
            messageDao.insertMessage(
                IncidentMessageEntity(
                    id = "mesh:$tokenHex:${packet.packetType.name}:${packet.statusCode}",
                    incidentId = existingIncident.incidentId,
                    senderName = "AGAP Relay",
                    senderRole = "COMMAND",
                    messageContent = messageText,
                    messageType = predefined?.messageType ?: packet.packetType.name,
                    createdAt = System.currentTimeMillis(),
                    predefinedMessageId = predefined?.id?.toLong(),
                    predefinedCode = predefined?.code,
                    protocolCode = packet.statusCode,
                    channel = "MESH",
                    deliveryState = "RECEIVED"
                )
            )

            if (
                incidentStatus != "RECEIVED" &&
                shouldAdvanceIncidentStatus(existingIncident.status, incidentStatus)
            ) {
                incidentDao.updateIncidentStatus(existingIncident.incidentId, incidentStatus)
                BleMeshLog.i(
                    "INCIDENT_STATUS_UPDATED_FROM_MESH_REPLY",
                    "token" to BleMeshLog.token(tokenHex),
                    "from" to existingIncident.status,
                    "status" to incidentStatus
                )
            }
        } else {
            BleMeshLog.d(
                "RESPONSE_WITHOUT_LOCAL_INCIDENT",
                "token" to BleMeshLog.token(tokenHex),
                "status" to incidentStatus
            )
        }

        if (incidentStatus in TERMINAL_STATUSES) {
            relayDao.markTokenTerminal(tokenHex, incidentStatus)
            relayDao.removeTokenFromQueue(tokenHex)
            BleMeshLog.i(
                "TOKEN_TERMINAL",
                "token" to BleMeshLog.token(tokenHex),
                "status" to incidentStatus
            )
        }

        meshDeliveryEventDao.insertEvent(
            MeshDeliveryEventEntity(
                packetHash = tokenHex,
                packetType = packet.packetType.name,
                eventType = "TARGET_REACHED",
                deviceIdentifier = tokenHex,
                hopCount = packet.hopCount
            )
        )
    }

    private fun incidentStatusForCatalogCode(code: String): String? = when (code.uppercase()) {
        "SOS_RECEIVED" -> "ACKNOWLEDGED"
        "HELP_EN_ROUTE" -> "EN_ROUTE"
        "RESCUE_DISPATCHED" -> "DISPATCHED"
        "MEDIC_DEPLOYED" -> "DISPATCHED"
        "FIRE_RESPONSE" -> "EN_ROUTE"
        "UNIT_EN_ROUTE" -> "EN_ROUTE"
        "UNIT_ARRIVING" -> "ON_SCENE"
        "INCIDENT_RESOLVED" -> "RESOLVED"
        "INCIDENT_CANCELLED" -> "CANCELLED"
        else -> null
    }


    /**
     * Mesh packets can arrive out of order through different Nearby/BLE paths.
     * Never let an older operational reply regress an incident that has already
     * progressed further through the response lifecycle.
     */
    private fun shouldAdvanceIncidentStatus(current: String, next: String): Boolean {
        val currentNormalized = current.uppercase()
        val nextNormalized = next.uppercase()
        if (currentNormalized in TERMINAL_STATUSES) return false
        if (nextNormalized == "CANCELLED") return true

        val rank = mapOf(
            "CREATED" to 0,
            "QUEUED" to 0,
            "RECEIVED" to 1,
            "ACKNOWLEDGED" to 2,
            "DISPATCHED" to 3,
            "EN_ROUTE" to 4,
            "ON_SCENE" to 5,
            "RESOLVED" to 6
        )
        val currentRank = rank[currentNormalized] ?: 0
        val nextRank = rank[nextNormalized] ?: return false
        return nextRank >= currentRank
    }

    private fun statusName(packet: BleMeshPacket): String {
        if (packet.packetType == BleMeshPacketType.ACK) return "ACKNOWLEDGED"
        return when (packet.statusCode) {
            1 -> "ACKNOWLEDGED" // SOS_RECEIVED
            2 -> "EN_ROUTE"     // HELP_EN_ROUTE
            3 -> "DISPATCHED"   // RESCUE_DISPATCHED
            4 -> "EN_ROUTE"     // UNIT_EN_ROUTE
            5 -> "ON_SCENE"     // UNIT_ARRIVING
            6 -> "RESOLVED"     // INCIDENT_RESOLVED
            7 -> "CANCELLED"    // INCIDENT_CANCELLED
            8 -> "DISPATCHED"   // MEDIC_DEPLOYED
            9 -> "EN_ROUTE"     // FIRE_RESPONSE
            else -> "RECEIVED"
        }
    }

    private fun encodeRelayPacket(packet: BleMeshPacket): ByteArray? {
        return when (packet.packetType) {
            BleMeshPacketType.SOS,
            BleMeshPacketType.INCIDENT_REPORT -> BleMeshPacketCodec.encodeEmergencyPacket(
                packetType = packet.packetType,
                emergencyType = packet.emergencyType,
                immediateDanger = packet.immediateDanger,
                subjectRelation = packet.subjectRelation,
                hopCount = packet.hopCount,
                locationSource = packet.locationSource,
                transportToken = packet.transportToken,
                latitude = packet.latitude,
                longitude = packet.longitude,
                horizontalAccuracyMeters = packet.horizontalAccuracyMeters,
                creationTimeSlot = packet.creationTimeSlot
            )

            BleMeshPacketType.ACK,
            BleMeshPacketType.STATUS -> BleMeshPacketCodec.encodeResponsePacket(
                packetType = packet.packetType,
                hopCount = packet.hopCount,
                transportToken = packet.transportToken,
                statusCode = packet.statusCode,
                creationTimeSlot = packet.creationTimeSlot
            )
        }
    }

    private fun isExpired(packet: BleMeshPacket): Boolean {
        if (packet.creationTimeSlot == 0) return false
        val nowSlot = ((System.currentTimeMillis() / 60_000L) and 0xFFFF).toInt()
        val ageMinutes = (nowSlot - packet.creationTimeSlot + 65_536) and 0xFFFF
        val maxMinutes = if (
            packet.packetType == BleMeshPacketType.ACK || packet.packetType == BleMeshPacketType.STATUS
        ) 60 else 120
        return ageMinutes > maxMinutes
    }



    companion object {
        private val TERMINAL_STATUSES = setOf("RESOLVED", "CANCELLED")
    }
}
