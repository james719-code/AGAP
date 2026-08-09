package com.travelingsalesman.agap.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "pending_sync",
    indices = [
        Index(value = ["status", "createdAtMs"])
    ]
)
data class PendingSyncEntity(
    @PrimaryKey(autoGenerate = true)
    val syncId: Long = 0,
    val syncType: String,
    val payloadJson: String,
    val reportUuid: String? = null,
    val createdAtMs: Long = System.currentTimeMillis(),
    val status: String = "PENDING",
    val retryCount: Int = 0
)
