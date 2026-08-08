package com.travelingsalesman.agap.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.travelingsalesman.agap.data.local.entity.CachedAgencyServiceAreaEntity
import com.travelingsalesman.agap.data.local.entity.CachedBarangayEntity
import com.travelingsalesman.agap.data.local.entity.CachedCatalogVersionEntity
import com.travelingsalesman.agap.data.local.entity.CachedEmergencySubtypeEntity

@Dao
interface CatalogDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCatalogVersion(version: CachedCatalogVersionEntity)

    @Query("SELECT * FROM cached_catalog_versions WHERE catalogName = :name")
    suspend fun getCatalogVersion(name: String): CachedCatalogVersionEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertBarangays(barangays: List<CachedBarangayEntity>)

    @Query("SELECT * FROM cached_barangays ORDER BY barangayName ASC")
    suspend fun getAllBarangays(): List<CachedBarangayEntity>

    @Query("SELECT * FROM cached_barangays WHERE barangayCode = :code")
    suspend fun getBarangayByCode(code: String): CachedBarangayEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertEmergencySubtypes(subtypes: List<CachedEmergencySubtypeEntity>)

    @Query("SELECT * FROM cached_emergency_subtypes WHERE emergencyTypeCode = :typeCode")
    suspend fun getSubtypesForType(typeCode: String): List<CachedEmergencySubtypeEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertServiceAreas(areas: List<CachedAgencyServiceAreaEntity>)

    @Query("SELECT agencyId FROM cached_agency_service_areas WHERE barangayCode = :brgyCode AND isPrimary = 1")
    suspend fun getPrimaryAgenciesForBarangay(brgyCode: String): List<String>
}
