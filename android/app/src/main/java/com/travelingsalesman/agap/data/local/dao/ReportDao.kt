package com.travelingsalesman.agap.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.travelingsalesman.agap.data.local.entity.LocalIncidentReportEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ReportDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertReport(report: LocalIncidentReportEntity)

    @Query("SELECT * FROM local_incident_reports WHERE reportUuid = :uuid")
    suspend fun getReportByUuid(uuid: String): LocalIncidentReportEntity?

    @Query("SELECT * FROM local_incident_reports WHERE transportToken = :token LIMIT 1")
    suspend fun getReportByToken(token: String): LocalIncidentReportEntity?

    @Query("SELECT * FROM local_incident_reports ORDER BY observedTimestampMs DESC")
    fun getAllReportsFlow(): Flow<List<LocalIncidentReportEntity>>

    @Query("SELECT * FROM local_incident_reports WHERE syncState IN ('PENDING', 'PENDING_INTERNET', 'QUEUED_OFFLINE', 'BLE_ONLY') ORDER BY observedTimestampMs ASC")
    suspend fun getPendingReports(): List<LocalIncidentReportEntity>

    @Query("SELECT COUNT(*) FROM local_incident_reports WHERE syncState IN ('PENDING', 'PENDING_INTERNET', 'QUEUED_OFFLINE', 'BLE_ONLY')")
    fun observePendingReportCount(): Flow<Int>
}
