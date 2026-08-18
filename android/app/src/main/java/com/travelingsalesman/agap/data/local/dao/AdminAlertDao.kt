package com.travelingsalesman.agap.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.travelingsalesman.agap.data.local.entity.AdminAlertEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface AdminAlertDao {
    @Query("SELECT * FROM admin_alerts ORDER BY publishedAt DESC")
    fun getAllAlerts(): Flow<List<AdminAlertEntity>>

    @Query("SELECT * FROM admin_alerts ORDER BY isPinned DESC, publishedAt DESC")
    fun getActiveAlerts(): Flow<List<AdminAlertEntity>>

    @Query("SELECT * FROM admin_alerts WHERE uuid = :uuid LIMIT 1")
    suspend fun getAlertByUuid(uuid: String): AdminAlertEntity?

    @Query("SELECT * FROM admin_alerts WHERE id = :id LIMIT 1")
    suspend fun getAlertById(id: Long): AdminAlertEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAlerts(alerts: List<AdminAlertEntity>)

    @Query("UPDATE admin_alerts SET isRead = 1 WHERE id = :id")
    suspend fun markAsRead(id: Long)

    @Query("SELECT COUNT(*) FROM admin_alerts WHERE isRead = 0 AND isActive = 1")
    fun getUnreadCount(): Flow<Int>

    @Query("DELETE FROM admin_alerts")
    suspend fun clearAll()
}
