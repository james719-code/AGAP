package com.travelingsalesman.agap.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "seen_mesh_packets",
    primaryKeys = ["transportToken", "packetType"],
    indices = [
        Index(value = ["expiryMs"]),
        Index(value = ["isTerminal", "tombstoneExpiryMs"])
    ]
)
data class SeenMeshPacketEntity(
    val transportToken: String,
    val packetType: String,
    val highestHop: Int = 0,
    val firstSeenMs: Long = System.currentTimeMillis(),
    val lastSeenMs: Long = System.currentTimeMillis(),
    val expiryMs: Long = System.currentTimeMillis() + 7200_000L,
    val deliveryState: String = "RECEIVED",
    val isTerminal: Boolean = false,
    val terminalStatus: String? = null,
    val terminalTimeMs: Long? = null,
    val tombstoneExpiryMs: Long? = null
)

@Entity(
    tableName = "relay_queue",
    indices = [
        Index(value = ["relayState", "expiryMs", "priority", "lastAdvertiseMs", "createdAtMs"]),
        Index(value = ["transportToken", "packetType", "relayState"]),
        Index(value = ["expiryMs"])
    ]
)
data class RelayQueueEntity(
    @PrimaryKey(autoGenerate = true)
    val queueId: Long = 0,
    val transportToken: String,
    val packetType: String,
    val payloadBytesHex: String,
    val priority: Int = 50,
    val isOriginLocal: Boolean = false,
    val hopCount: Int = 0,
    val createdAtMs: Long = System.currentTimeMillis(),
    val expiryMs: Long = System.currentTimeMillis() + 7200_000L,
    val lastAdvertiseMs: Long = 0,
    val nextAdvertiseMs: Long = 0,
    val advertiseCount: Int = 0,
    val relayState: String = "QUEUED"
)
