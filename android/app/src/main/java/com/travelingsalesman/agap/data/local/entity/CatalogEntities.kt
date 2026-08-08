package com.travelingsalesman.agap.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "cached_catalog_versions")
data class CachedCatalogVersionEntity(
    @PrimaryKey
    val catalogName: String,
    val version: String,
    val updatedAtMs: Long = System.currentTimeMillis()
)

@Entity(tableName = "cached_emergency_subtypes")
data class CachedEmergencySubtypeEntity(
    @PrimaryKey
    val subtypeCode: String,
    val emergencyTypeCode: String,
    val displayName: String,
    val protocolCode: Int
)

@Entity(tableName = "cached_agency_types")
data class CachedAgencyTypeEntity(
    @PrimaryKey
    val agencyTypeCode: String,
    val displayName: String
)

@Entity(tableName = "cached_agency_service_areas")
data class CachedAgencyServiceAreaEntity(
    @PrimaryKey
    val areaId: String,
    val agencyId: String,
    val barangayCode: String,
    val cityMunicipality: String = "LEGAZPI",
    val isPrimary: Boolean = true
)
