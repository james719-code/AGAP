package com.travelingsalesman.agap.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(
    tableName = "seen_mesh_packets",
    primaryKeys = ["transportToken", "packetType"]
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

@Entity(tableName = "relay_queue")
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
