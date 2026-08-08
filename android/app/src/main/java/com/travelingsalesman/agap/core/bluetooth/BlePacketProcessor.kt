package com.travelingsalesman.agap.core.bluetooth

import com.travelingsalesman.agap.data.local.dao.IncidentDao
import com.travelingsalesman.agap.data.local.dao.RelayDao
import com.travelingsalesman.agap.data.local.dao.ReportDao
import com.travelingsalesman.agap.data.local.entity.IncidentEntity
import com.travelingsalesman.agap.data.local.entity.LocalIncidentReportEntity
import com.travelingsalesman.agap.data.local.entity.SeenMeshPacketEntity
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class BlePacketProcessor @Inject constructor(
    private val relayDao: RelayDao,
    private val incidentDao: IncidentDao,
    private val reportDao: ReportDao,
    private val relayScheduler: BleRelayScheduler,
    private val visibilityPolicy: BleVisibilityPolicy
) {
    suspend fun processIncomingPacket(
        data: ByteArray,
        userRole: UserRole = UserRole.CITIZEN,
        userAgencyType: String? = null
    ): BleMeshPacket? {
        val packet = BleMeshPacketCodec.decode(data) ?: return null

        val tokenHex = packet.transportTokenHex
        val seen = relayDao.getSeenPacket(tokenHex)
        val now = System.currentTimeMillis()

        if (seen != null && seen.isTerminal && packet.packetType != BleMeshPacketType.STATUS) {
            return null
        }

        if (seen != null) {
            val updated = seen.copy(
                highestHop = maxOf(seen.highestHop, packet.hopCount),
                lastSeenMs = now
            )
            relayDao.insertSeenPacket(updated)
        } else {
            val newSeen = SeenMeshPacketEntity(
                transportToken = tokenHex,
                packetType = packet.packetType.name,
                highestHop = packet.hopCount,
                firstSeenMs = now,
                lastSeenMs = now,
                expiryMs = now + 7200_000L,
                deliveryState = "RECEIVED"
            )
            relayDao.insertSeenPacket(newSeen)
        }

        val isOwnReport = reportDao.getReportByToken(tokenHex) != null

        val shouldShow = visibilityPolicy.shouldShowInUi(
            userRole = userRole,
            isOwnReport = isOwnReport,
            emergencyType = packet.emergencyType,
            responderAgencyType = userAgencyType
        )

        if (shouldShow && (packet.packetType == BleMeshPacketType.SOS || packet.packetType == BleMeshPacketType.INCIDENT_REPORT)) {
            val existingIncident = incidentDao.getIncidentByPacketHash(tokenHex)
            if (existingIncident == null) {
                val incidentUuid = UUID.randomUUID().toString()
                val incident = IncidentEntity(
                    incidentId = incidentUuid,
                    targetId = "BLE_P2P",
                    emergencyType = packet.emergencyType,
                    latitude = packet.latitude,
                    longitude = packet.longitude,
                    timestamp = now,
                    status = "QUEUED",
                    description = "Received via BLE Relay (Token: ${tokenHex.take(6)})",
                    packetHash = tokenHex,
                    channel = "BLE_MESH",
                    deliveryPath = "BLE_CLOUD_SHORTCUT",
                    senderDeviceId = "P2P_PEER",
                    isDirty = true
                )
                incidentDao.addIncident(incident)
            } else {
                // Reject location refinement if incident is already terminal
                val isIncidentTerminal = existingIncident.status == "RESOLVED" || existingIncident.status == "CANCELLED"
                if (!isIncidentTerminal) {
                    if (existingIncident.latitude != packet.latitude || existingIncident.longitude != packet.longitude) {
                        val refined = existingIncident.copy(
                            latitude = packet.latitude,
                            longitude = packet.longitude,
                            isDirty = true
                        )
                        incidentDao.addIncident(refined)
                    }
                }
            }
        }

        if (packet.packetType == BleMeshPacketType.STATUS && (packet.statusCode == 3 || packet.statusCode == 4)) {
            val statusStr = if (packet.statusCode == 3) "RESOLVED" else "CANCELLED"
            relayDao.markTokenTerminal(tokenHex, statusStr)
            relayDao.removeTokenFromQueue(tokenHex)

            val existingIncident = incidentDao.getIncidentByPacketHash(tokenHex)
            if (existingIncident != null) {
                incidentDao.updateIncidentStatus(existingIncident.incidentId, statusStr)
            }

            val localReport = reportDao.getReportByToken(tokenHex)
            if (localReport != null) {
                reportDao.insertReport(localReport.copy(syncState = statusStr))
            }
        }

        if (packet.hopCount < 15) {
            val nextHopPacket = packet.copy(hopCount = packet.hopCount + 1)
            val rebroadcastBytes = when (packet.packetType) {
                BleMeshPacketType.SOS, BleMeshPacketType.INCIDENT_REPORT -> {
                    BleMeshPacketCodec.encodeEmergencyPacket(
                        packetType = nextHopPacket.packetType,
                        emergencyType = nextHopPacket.emergencyType,
                        immediateDanger = nextHopPacket.immediateDanger,
                        subjectRelation = nextHopPacket.subjectRelation,
                        hopCount = nextHopPacket.hopCount,
                        locationSource = nextHopPacket.locationSource,
                        transportToken = nextHopPacket.transportToken,
                        latitude = nextHopPacket.latitude,
                        longitude = nextHopPacket.longitude,
                        horizontalAccuracyMeters = nextHopPacket.horizontalAccuracyMeters
                    )
                }
                BleMeshPacketType.ACK, BleMeshPacketType.STATUS -> {
                    BleMeshPacketCodec.encodeResponsePacket(
                        packetType = nextHopPacket.packetType,
                        hopCount = nextHopPacket.hopCount,
                        transportToken = nextHopPacket.transportToken,
                        statusCode = nextHopPacket.statusCode
                    )
                }
                else -> null
            }

            if (rebroadcastBytes != null) {
                relayScheduler.enqueueForRelay(nextHopPacket, rebroadcastBytes, isOriginLocal = false)
            }
        }

        return packet
    }
}
