package com.travelingsalesman.agap.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "local_incident_reports",
    indices = [
        Index(value = ["transportToken"]),
        Index(value = ["syncState", "observedTimestampMs"]),
        Index(value = ["logicalId"])
    ]
)
data class LocalIncidentReportEntity(
    @PrimaryKey
    val reportUuid: String,
    val transportToken: String,
    val incidentUuid: String?,
    val submissionType: String = "CITIZEN_REPORT",
    val subjectRelation: String = "SELF",
    val emergencyType: String = "MEDICAL",
    val subtype: String = "GENERAL",
    val description: String = "",
    val peopleAffected: Int = 1,
    val immediateDanger: Boolean = false,
    val locationSource: String = "GPS",
    val latitude: Double? = null,
    val longitude: Double? = null,
    val gpsAccuracyMeters: Float? = null,
    val selectedBarangayCode: String? = null,
    val observedTimestampMs: Long = System.currentTimeMillis(),
    val syncState: String = "PENDING",
    val serverIncidentId: Long? = null,
    val logicalId: String = transportToken
)
