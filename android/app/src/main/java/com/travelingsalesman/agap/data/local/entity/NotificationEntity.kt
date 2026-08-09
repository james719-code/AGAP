package com.travelingsalesman.agap.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "notifications",
    indices = [
        Index(value = ["createdAt"]),
        Index(value = ["isRead", "createdAt"])
    ]
)
data class NotificationEntity(
    @PrimaryKey val id: Long,
    val userId: Long,
    val title: String,
    val message: String,
    val type: String,
    val isRead: Boolean,
    val referenceId: String?,
    val createdAt: Long
)
