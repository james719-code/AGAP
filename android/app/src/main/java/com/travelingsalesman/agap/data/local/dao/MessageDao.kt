package com.travelingsalesman.agap.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import com.travelingsalesman.agap.data.local.entity.IncidentMessageEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface MessageDao {
    @Query("SELECT * FROM incident_messages WHERE incidentId = :incidentId ORDER BY createdAt ASC")
    fun getMessagesForIncident(incidentId: String): Flow<List<IncidentMessageEntity>>

    @Query("SELECT * FROM incident_messages WHERE incidentId = :incidentId ORDER BY createdAt ASC")
    suspend fun getMessagesForIncidentList(incidentId: String): List<IncidentMessageEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMessages(messages: List<IncidentMessageEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMessage(message: IncidentMessageEntity)

    @Query("DELETE FROM incident_messages WHERE id = :id")
    suspend fun deleteMessage(id: String)

    @Query("UPDATE incident_messages SET deliveryState = :state, channel = :channel WHERE id = :id")
    suspend fun updateDeliveryState(id: String, state: String, channel: String)

    @Query("DELETE FROM incident_messages WHERE incidentId = :incidentId")
    suspend fun deleteMessagesForIncident(incidentId: String)

    /**
     * Removes only messages that originated from the authoritative REST API.
     * Local optimistic and mesh rows must survive a successful empty server
     * response so an offline reply is never lost during reconciliation.
     */
    @Query("DELETE FROM incident_messages WHERE incidentId = :incidentId AND id LIKE 'server:%'")
    suspend fun deleteServerMessagesForIncident(incidentId: String)

    /**
     * A successful message-list response is authoritative for server rows.
     * Replacing only the server subset prevents stale online messages while
     * preserving pending local and mesh-delivered messages.
     */
    @Transaction
    suspend fun replaceServerMessagesForIncident(
        incidentId: String,
        messages: List<IncidentMessageEntity>
    ) {
        deleteServerMessagesForIncident(incidentId)
        if (messages.isNotEmpty()) {
            insertMessages(messages)
        }
    }
}
