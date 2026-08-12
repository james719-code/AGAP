package com.travelingsalesman.agap.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Local incident conversation entry.
 *
 * incidentId is deliberately a String: server incidents use numeric IDs while
 * off-grid incidents use a stable local UUID/token. This allows the same
 * conversation UI to work before and after cloud synchronization.
 */
@Entity(
    tableName = "incident_messages",
    indices = [
        Index(value = ["incidentId", "createdAt"]),
        Index(value = ["predefinedMessageId"]),
        Index(value = ["clientMessageId"]),
        Index(value = ["deliveryState", "createdAt"])
    ]
)
data class IncidentMessageEntity(
    @PrimaryKey val id: String,
    val incidentId: String,
    val senderId: Long = 0L,
    val senderName: String,
    val senderRole: String,
    val messageContent: String,
    val messageType: String,
    val createdAt: Long,
    val isSystem: Boolean = false,
    val predefinedMessageId: Long? = null,
    val predefinedCode: String? = null,
    val protocolCode: Int = 0,
    val clientMessageId: String? = null,
    val channel: String = "ONLINE",
    val deliveryState: String = "RECEIVED"
)
