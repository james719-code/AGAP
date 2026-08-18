package com.travelingsalesman.agap.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "admin_alerts",
    indices = [
        Index(value = ["uuid"], unique = true),
        Index(value = ["publishedAt"]),
        Index(value = ["severity"])
    ]
)
data class AdminAlertEntity(
    @PrimaryKey val id: Long,
    val uuid: String,
    val authorId: Long,
    val authorName: String?,
    val title: String,
    val body: String,
    val category: String, // String representation of AlertCategory
    val severity: String, // String representation of AlertSeverity
    val isPinned: Boolean,
    val isActive: Boolean,
    val publishedAt: Long,
    val expiresAt: Long?,
    val createdAt: Long,
    val isRead: Boolean = false,
    /**
     * Set to true if this is a placeholder received via BLE mesh
     * that hasn't been synced with the full body from the server yet.
     */
    val isPlaceholder: Boolean = false
)
