package com.travelingsalesman.agap.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.travelingsalesman.agap.data.local.entity.IncidentMessageEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface MessageDao {
    @Query("SELECT * FROM incident_messages WHERE incidentId = :incidentId ORDER BY createdAt ASC")
    fun getMessagesForIncident(incidentId: Long): Flow<List<IncidentMessageEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMessages(messages: List<IncidentMessageEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMessage(message: IncidentMessageEntity)

    @Query("DELETE FROM incident_messages WHERE incidentId = :incidentId")
    suspend fun deleteMessagesForIncident(incidentId: Long)
}
