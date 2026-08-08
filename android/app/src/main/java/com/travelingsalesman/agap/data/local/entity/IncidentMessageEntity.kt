package com.travelingsalesman.agap.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "incident_messages")
data class IncidentMessageEntity(
    @PrimaryKey val id: Long,
    val incidentId: Long,
    val senderId: Long,
    val senderName: String,
    val senderRole: String,
    val messageContent: String,
    val messageType: String,
    val createdAt: Long, // Converted to epoch millis
    val isSystem: Boolean = false
)
