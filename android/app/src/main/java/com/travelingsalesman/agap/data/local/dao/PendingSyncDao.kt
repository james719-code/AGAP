package com.travelingsalesman.agap.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.travelingsalesman.agap.data.local.entity.PendingSyncEntity

@Dao
interface PendingSyncDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSyncItem(item: PendingSyncEntity): Long

    @Query("SELECT * FROM pending_sync WHERE status = :status ORDER BY createdAtMs ASC")
    suspend fun getPendingSyncItems(status: String = "PENDING"): List<PendingSyncEntity>

    @Query("UPDATE pending_sync SET status = :status, retryCount = retryCount + 1 WHERE syncId = :syncId")
    suspend fun updateSyncStatus(syncId: Long, status: String)

    @Query("DELETE FROM pending_sync WHERE syncId = :syncId")
    suspend fun deleteSyncItem(syncId: Long)
}
