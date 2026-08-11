package com.travelingsalesman.agap.data.local

import android.content.Context
import com.travelingsalesman.agap.data.local.dao.AgencyDao
import com.travelingsalesman.agap.data.local.dao.CatalogDao
import com.travelingsalesman.agap.data.local.dao.EmergencyTypeDao
import com.travelingsalesman.agap.data.local.entity.AgencyEntity
import com.travelingsalesman.agap.data.local.entity.CachedAgencyServiceAreaEntity
import com.travelingsalesman.agap.data.local.entity.CachedBarangayEntity
import com.travelingsalesman.agap.data.local.entity.CachedCatalogVersionEntity
import com.travelingsalesman.agap.data.local.entity.EmergencyTypeEntity
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

import com.travelingsalesman.agap.data.local.dao.PredefinedMessageDao
import com.travelingsalesman.agap.data.local.dao.ResponderDao
import com.travelingsalesman.agap.data.local.entity.PredefinedMessageEntity
import com.travelingsalesman.agap.data.local.entity.ResponderUnitEntity

@Serializable
private data class EmergencyTypeAsset(
    val id: Long,
    val code: String,
    val name: String,
    @SerialName("protocol_code") val protocolCode: Int = 0
)

@Serializable
private data class PredefinedMessageAsset(
    val id: Long,
    val code: String,
    val text: String,
    @SerialName("protocol_code") val protocolCode: Int = 0
)

@Serializable
private data class AgencyAsset(
    val id: Long,
    val code: String,
    val name: String,
    val type: String,
    @SerialName("barangay_code") val barangayCode: String = ""
)

@Serializable
private data class RescueUnitAsset(
    val id: Long,
    @SerialName("agency_id") val agencyId: Long,
    @SerialName("target_id") val targetId: String = "",
    @SerialName("unit_callsign") val unitCallsign: String,
    @SerialName("device_id") val deviceId: String = "",
    val status: String = "IDLE"
)

@Serializable
private data class ServiceAreaAsset(
    @SerialName("area_id") val areaId: String,
    @SerialName("agency_id") val agencyId: String,
    @SerialName("barangay_code") val barangayCode: String,
    @SerialName("city_municipality") val cityMunicipality: String = "LEGAZPI",
    @SerialName("is_primary") val isPrimary: Boolean = true
)

@Serializable
private data class CatalogAssetContainer(
    @SerialName("emergency_types") val emergencyTypes: List<EmergencyTypeAsset> = emptyList(),
    @SerialName("predefined_messages") val predefinedMessages: List<PredefinedMessageAsset> = emptyList(),
    val agencies: List<AgencyAsset> = emptyList(),
    @SerialName("agency_service_areas") val serviceAreas: List<ServiceAreaAsset> = emptyList(),
    @SerialName("rescue_units") val rescueUnits: List<RescueUnitAsset> = emptyList()
)

@Serializable
private data class BarangayAsset(
    @SerialName("barangay_code") val barangayCode: String,
    @SerialName("barangay_name") val barangayName: String,
    @SerialName("city_municipality") val cityMunicipality: String,
    val province: String,
    @SerialName("representative_latitude_e7") val representativeLatitudeE7: Int,
    @SerialName("representative_longitude_e7") val representativeLongitudeE7: Int,
    @SerialName("boundary_geojson") val boundaryGeojson: String? = null
)

@Singleton
class AssetCatalogImporter @Inject constructor(
    @ApplicationContext private val context: Context,
    private val catalogDao: CatalogDao,
    private val emergencyTypeDao: EmergencyTypeDao,
    private val predefinedMessageDao: PredefinedMessageDao,
    private val agencyDao: AgencyDao,
    private val responderDao: ResponderDao,
    private val json: Json
) {
    suspend fun importAssetsIfNecessary() {
        try {
            importBarangays()
            importCatalogs()
        } catch (e: Exception) {
            Timber.e(e, "AssetCatalogImporter: Failed to import initial asset catalogs into Room")
        }
    }

    private suspend fun importBarangays() {
        val existing = catalogDao.getAllBarangays()
        if (existing.isNotEmpty()) return

        val jsonStr = context.assets.open("agap/barangays.json").bufferedReader().use { it.readText() }
        val assets = json.decodeFromString<List<BarangayAsset>>(jsonStr)

        val entities = assets.map { b ->
            CachedBarangayEntity(
                barangayCode = b.barangayCode,
                barangayName = b.barangayName,
                cityMunicipality = b.cityMunicipality,
                province = b.province,
                representativeLatitudeE7 = b.representativeLatitudeE7,
                representativeLongitudeE7 = b.representativeLongitudeE7,
                boundaryGeoJson = b.boundaryGeojson
            )
        }
        catalogDao.insertBarangays(entities)
        catalogDao.insertCatalogVersion(
            CachedCatalogVersionEntity("barangays", "1.0.0", System.currentTimeMillis())
        )
        Timber.i("AssetCatalogImporter: Imported ${entities.size} Barangays from asset JSON")
    }

    private suspend fun importCatalogs() {
        val version = catalogDao.getCatalogVersion("catalogs")
        if (version != null) return

        val jsonStr = context.assets.open("agap/catalogs.json").bufferedReader().use { it.readText() }
        val container = json.decodeFromString<CatalogAssetContainer>(jsonStr)

        if (container.emergencyTypes.isNotEmpty()) {
            val typeEntities = container.emergencyTypes.map { et ->
                EmergencyTypeEntity(
                    id = et.id,
                    code = et.code,
                    name = et.name,
                    description = et.name,
                    isActive = true
                )
            }
            emergencyTypeDao.insertEmergencyTypes(typeEntities)
        }

        if (container.predefinedMessages.isNotEmpty()) {
            val pmEntities = container.predefinedMessages.map { pm ->
                PredefinedMessageEntity(
                    id = pm.id.toInt(),
                    code = pm.code,
                    protocolCode = pm.protocolCode,
                    messageText = pm.text,
                    messageType = if (pm.protocolCode >= 4) "STATUS" else "ACK",
                    isActive = true
                )
            }
            predefinedMessageDao.insertMessages(pmEntities)
        }

        if (container.agencies.isNotEmpty()) {
            val agencyEntities = container.agencies.map { a ->
                AgencyEntity(
                    id = a.id,
                    code = a.code,
                    name = a.name,
                    type = a.type
                )
            }
            agencyDao.insertAgencies(agencyEntities)
        }

        if (container.serviceAreas.isNotEmpty()) {
            val saEntities = container.serviceAreas.map { sa ->
                CachedAgencyServiceAreaEntity(
                    areaId = sa.areaId,
                    agencyId = sa.agencyId,
                    barangayCode = sa.barangayCode,
                    cityMunicipality = sa.cityMunicipality,
                    isPrimary = sa.isPrimary
                )
            }
            catalogDao.insertServiceAreas(saEntities)
        }

        if (container.rescueUnits.isNotEmpty()) {
            val ruEntities = container.rescueUnits.map { ru ->
                ResponderUnitEntity(
                    id = ru.id,
                    agencyId = ru.agencyId,
                    targetId = ru.targetId,
                    unitCallsign = ru.unitCallsign,
                    deviceId = ru.deviceId,
                    status = ru.status
                )
            }
            responderDao.insertResponders(ruEntities)
        }

        catalogDao.insertCatalogVersion(
            CachedCatalogVersionEntity("catalogs", "1.0.0", System.currentTimeMillis())
        )
        Timber.i("AssetCatalogImporter: Imported catalog assets from JSON into Room")
    }
}
