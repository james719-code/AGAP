package com.travelingsalesman.agap.core.bluetooth

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.bluetooth.le.AdvertiseCallback
import android.bluetooth.le.AdvertiseData
import android.bluetooth.le.AdvertiseSettings
import android.bluetooth.le.BluetoothLeAdvertiser
import android.bluetooth.le.BluetoothLeScanner
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanFilter
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.ParcelUuid
import androidx.core.content.ContextCompat
import com.travelingsalesman.agap.data.local.AuthTokenManager
import com.travelingsalesman.agap.data.local.dao.AgencyDao
import com.travelingsalesman.agap.data.local.dao.IncidentDao
import com.travelingsalesman.agap.data.local.dao.MeshDeliveryEventDao
import com.travelingsalesman.agap.data.local.dao.PendingPacketDao
import com.travelingsalesman.agap.data.local.entity.IncidentEntity
import com.travelingsalesman.agap.data.local.entity.MeshDeliveryEventEntity
import com.travelingsalesman.agap.data.local.entity.PendingPacketEntity
import com.travelingsalesman.agap.domain.model.LocationSource
import com.travelingsalesman.agap.location.LocationRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import timber.log.Timber
import java.security.MessageDigest
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

data class BleMeshState(
    val isSupported: Boolean = true,
    val isEnabled: Boolean = false,
    val isScanning: Boolean = false,
    val isAdvertising: Boolean = false,
    val nearbyDeviceCount: Int = 0,
    val relayedPacketCount: Int = 0,
    val activeAdPacketHash: String? = null,
    val needsPermissionsPrompt: Boolean = false,
    val needsBluetoothEnable: Boolean = false
)

@Singleton
class BleMeshManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val pendingPacketDao: PendingPacketDao,
    private val meshDeliveryEventDao: MeshDeliveryEventDao,
    private val agencyDao: AgencyDao,
    private val incidentDao: IncidentDao,
    private val locationRepository: LocationRepository,
    private val authTokenManager: AuthTokenManager,
    private val relayScheduler: BleRelayScheduler
) {
    companion object {
        // AGAP Mesh 16-bit Short Service UUID (0xFEAF)
        val SERVICE_UUID: UUID = UUID.fromString("0000FEAF-0000-1000-8000-00805F9B34FB")
    }

    private val bluetoothManager: BluetoothManager? =
        context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager
    private val bluetoothAdapter: BluetoothAdapter? get() = bluetoothManager?.adapter

    private var bluetoothLeAdvertiser: BluetoothLeAdvertiser? = null
    private var bluetoothLeScanner: BluetoothLeScanner? = null

    private val scope = CoroutineScope(Dispatchers.IO)

    private val _meshState = MutableStateFlow(BleMeshState())
    val meshState: StateFlow<BleMeshState> = _meshState.asStateFlow()

    // Node ID -> last seen timestamp
    private val discoveredNodes = ConcurrentHashMap<String, Long>()

    // In-memory cache of processed packet hashes to eliminate redundant DB calls & coroutine overhead
    private val processedPacketHashes = ConcurrentHashMap<String, Long>()

    // Persistent 8-byte device node identifier
    private val deviceNodeIdBytes: ByteArray by lazy {
        val prefs = context.getSharedPreferences("agap_ble_mesh_prefs", Context.MODE_PRIVATE)
        var savedHex = prefs.getString("device_node_id_hex", null)
        if (savedHex == null || savedHex.length != 16) {
            val randomBytes = ByteArray(8)
            java.security.SecureRandom().nextBytes(randomBytes)
            savedHex = randomBytes.joinToString("") { "%02x".format(it) }
            prefs.edit().putString("device_node_id_hex", savedHex).apply()
        }
        ByteArray(8) { i -> savedHex.substring(i * 2, i * 2 + 2).toInt(16).toByte() }
    }

    init {
        checkHardwareSupport()
    }

    fun checkHardwareSupport() {
        val adapter = bluetoothAdapter
        val isSupported = adapter != null
        val isEnabled = adapter?.isEnabled == true
        _meshState.update {
            it.copy(isSupported = isSupported, isEnabled = isEnabled)
        }
    }

    fun hasBluetoothScanPermission(): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            return ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_SCAN) == PackageManager.PERMISSION_GRANTED
        }
        return ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
    }

    fun hasBluetoothAdvertisePermission(): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            return ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_ADVERTISE) == PackageManager.PERMISSION_GRANTED
        }
        return true
    }

    private var periodicJob: kotlinx.coroutines.Job? = null

    @SuppressLint("MissingPermission")
    fun startMeshServices() {
        if (!hasBluetoothScanPermission()) {
            Timber.w("BleMeshManager: Cannot start mesh services - BLUETOOTH_SCAN permission not granted yet")
            return
        }

        val adapter = bluetoothAdapter ?: return
        if (!adapter.isEnabled) return

        bluetoothLeScanner = adapter.bluetoothLeScanner
        bluetoothLeAdvertiser = adapter.bluetoothLeAdvertiser

        startScanning()
        startPeriodicMeshPulse()
    }

    private fun startPeriodicMeshPulse() {
        periodicJob?.cancel()
        periodicJob = scope.launch {
            Timber.i("BLE_MESH: Started 5.0-second periodic scan & mesh pulse engine")
            while (isActive) {
                kotlinx.coroutines.delay(5000) // 5.0 second throttled interval
                if (hasBluetoothScanPermission() && bluetoothAdapter?.isEnabled == true) {
                    val now = System.currentTimeMillis()
                    // 1. Purge stale peer nodes (>15s since last seen)
                    discoveredNodes.entries.removeAll { now - it.value > 15000 }
                    // 2. Purge stale processed packet hashes (>10 mins old)
                    processedPacketHashes.entries.removeAll { now - it.value > 600000 }

                    _meshState.update { it.copy(nearbyDeviceCount = discoveredNodes.size) }

                    // 3. Ensure scanning is active
                    if (!_meshState.value.isScanning) {
                        startScanning()
                    }

                    // 4. Fair Scheduled Relay Broadcast: pick top priority/round-robin queued packet, or beacon fallback
                    try {
                        val scheduled = relayScheduler.getNextPacketToBroadcast()
                        if (scheduled != null) {
                            val advertiser = bluetoothLeAdvertiser ?: bluetoothAdapter?.bluetoothLeAdvertiser
                            if (advertiser != null) {
                                val payload = hexToBytes(scheduled.payloadBytesHex)
                                broadcastPayload(advertiser, payload, activeHash = scheduled.transportToken)
                                relayScheduler.markAdvertised(scheduled)
                            }
                        } else {
                            startAdvertisingBeacon()
                        }
                    } catch (e: Exception) {
                        Timber.e(e, "BLE_MESH: Error during periodic mesh pulse")
                    }
                }
            }
        }
    }

    private fun hexToBytes(hex: String): ByteArray {
        val result = ByteArray(hex.length / 2)
        for (i in result.indices) {
            result[i] = hex.substring(i * 2, i * 2 + 2).toInt(16).toByte()
        }
        return result
    }

    @SuppressLint("MissingPermission")
    fun startScanning() {
        if (!hasBluetoothScanPermission()) {
            Timber.w("BleMeshManager: Cannot start scan - BLUETOOTH_SCAN permission not granted yet")
            return
        }

        val scanner = bluetoothLeScanner ?: bluetoothAdapter?.bluetoothLeScanner ?: return
        if (_meshState.value.isScanning) return

        try {
            val filter = ScanFilter.Builder()
                .setServiceUuid(ParcelUuid(SERVICE_UUID))
                .build()

            val settings = ScanSettings.Builder()
                .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
                .setCallbackType(ScanSettings.CALLBACK_TYPE_ALL_MATCHES)
                .build()

            scanner.startScan(listOf(filter), settings, scanCallback)
            _meshState.update { it.copy(isScanning = true) }
            Timber.i("BleMeshManager: Started BLE Mesh Scanner for Service UUID 0xFEAF")
        } catch (e: Exception) {
            Timber.e(e, "BleMeshManager: Failed to start BLE scanning")
        }
    }

    @SuppressLint("MissingPermission")
    fun stopScanning() {
        if (!hasBluetoothScanPermission()) return
        val scanner = bluetoothLeScanner ?: return
        try {
            scanner.stopScan(scanCallback)
            _meshState.update { it.copy(isScanning = false) }
        } catch (e: Exception) {
            Timber.e(e, "BleMeshManager: Error stopping scan")
        }
    }

    @SuppressLint("MissingPermission")
    fun startAdvertisingBeacon() {
        if (!hasBluetoothAdvertisePermission()) return
        val advertiser = bluetoothLeAdvertiser ?: bluetoothAdapter?.bluetoothLeAdvertiser ?: return

        val loc = locationRepository.currentLocation.value
        val lat = loc?.latitude ?: 13.1391
        val lon = loc?.longitude ?: 123.7438

        val payload = BleMeshPacketCodec.encodeEmergencyPacket(
            packetType = BleMeshPacketType.BEACON,
            emergencyType = "UNKNOWN",
            immediateDanger = false,
            subjectRelation = "SELF",
            hopCount = 0,
            locationSource = LocationSource.GPS,
            transportToken = deviceNodeIdBytes,
            latitude = lat,
            longitude = lon
        )

        broadcastPayload(advertiser, payload, activeHash = null)
    }

    @SuppressLint("MissingPermission")
    fun ensureBluetoothEnabled(): Boolean {
        val adapter = bluetoothAdapter ?: return false
        val enabled = adapter.isEnabled
        _meshState.update { it.copy(isEnabled = enabled) }
        if (!enabled) {
            _meshState.update { it.copy(needsBluetoothEnable = true) }
            Timber.i("BleMeshManager: Bluetooth disabled. Signaling UI to request OS Bluetooth Enable dialog.")
        }
        return enabled
    }

    fun resetPermissionsPrompt() {
        _meshState.update { it.copy(needsPermissionsPrompt = false) }
    }

    fun resetBluetoothEnablePrompt() {
        _meshState.update { it.copy(needsBluetoothEnable = false) }
    }

    @SuppressLint("MissingPermission")
    fun startAdvertisingSosPacket(packet: PendingPacketEntity) {
        ensureBluetoothEnabled()

        if (!hasBluetoothAdvertisePermission() || !hasBluetoothScanPermission()) {
            Timber.w("BleMeshManager: SOS broadcast attempted without Bluetooth permissions! Re-prompting user permissions.")
            _meshState.update { it.copy(needsPermissionsPrompt = true) }
            return
        }

        val advertiser = bluetoothLeAdvertiser ?: bluetoothAdapter?.bluetoothLeAdvertiser ?: return

        val hashBytes = packet.packetHash.toByteArray()

        val payload = BleMeshPacketCodec.encodeEmergencyPacket(
            packetType = BleMeshPacketType.SOS,
            emergencyType = packet.emergencyType,
            immediateDanger = true,
            subjectRelation = "SELF",
            hopCount = packet.hopCount,
            locationSource = LocationSource.GPS,
            transportToken = hashBytes,
            latitude = packet.latitude,
            longitude = packet.longitude
        )

        broadcastPayload(advertiser, payload, activeHash = packet.packetHash)
    }

    @SuppressLint("MissingPermission")
    fun startAdvertisingAcknowledgment(incidentIdentifier: String, emergencyType: String, messageCode: Int = 1) {
        ensureBluetoothEnabled()

        if (!hasBluetoothAdvertisePermission() || !hasBluetoothScanPermission()) {
            Timber.w("BleMeshManager: ACK broadcast attempted without Bluetooth permissions.")
            _meshState.update { it.copy(needsPermissionsPrompt = true) }
            return
        }

        val advertiser = bluetoothLeAdvertiser ?: bluetoothAdapter?.bluetoothLeAdvertiser ?: return
        val loc = locationRepository.currentLocation.value
        val payload = BleMeshPacketCodec.encodeResponsePacket(
            packetType = BleMeshPacketType.ACK,
            hopCount = 0,
            transportToken = compactIdentifier(incidentIdentifier),
            statusCode = messageCode
        )

        val activeHash = "ACK-${compactIdentifierHex(incidentIdentifier)}-$messageCode"
        broadcastPayload(advertiser, payload, activeHash = activeHash)
        scope.launch {
            meshDeliveryEventDao.insertEvent(
                MeshDeliveryEventEntity(
                    packetHash = activeHash,
                    packetType = "ACK",
                    eventType = "CREATED",
                    deviceIdentifier = compactIdentifierHex(incidentIdentifier),
                    latitude = loc?.latitude,
                    longitude = loc?.longitude,
                    hopCount = 0
                )
            )
        }
    }

    private var currentActivePayload: ByteArray? = null

    @SuppressLint("MissingPermission")
    private fun broadcastPayload(advertiser: BluetoothLeAdvertiser, payload: ByteArray, activeHash: String?) {
        if (_meshState.value.isAdvertising && currentActivePayload != null && currentActivePayload.contentEquals(payload)) {
            return
        }

        try {
            stopAdvertising()

            val settings = AdvertiseSettings.Builder()
                .setAdvertiseMode(AdvertiseSettings.ADVERTISE_MODE_LOW_LATENCY)
                .setTxPowerLevel(AdvertiseSettings.ADVERTISE_TX_POWER_HIGH)
                .setConnectable(false)
                .build()

            val data = AdvertiseData.Builder()
                .addServiceUuid(ParcelUuid(SERVICE_UUID))
                .addServiceData(ParcelUuid(SERVICE_UUID), payload)
                .setIncludeDeviceName(false)
                .setIncludeTxPowerLevel(false)
                .build()

            advertiser.startAdvertising(settings, data, advertiseCallback)
            currentActivePayload = payload
            _meshState.update {
                it.copy(isAdvertising = true, activeAdPacketHash = activeHash)
            }
        } catch (e: Exception) {
            Timber.e(e, "BleMeshManager: Failed to start BLE advertising")
        }
    }

    @SuppressLint("MissingPermission")
    fun stopAdvertising() {
        val advertiser = bluetoothLeAdvertiser ?: return
        if (!_meshState.value.isAdvertising) return
        try {
            advertiser.stopAdvertising(advertiseCallback)
            currentActivePayload = null
            _meshState.update { it.copy(isAdvertising = false, activeAdPacketHash = null) }
        } catch (e: Exception) {
            Timber.e(e, "BleMeshManager: Error stopping advertising")
        }
    }

    private val scanCallback = object : ScanCallback() {
        override fun onScanResult(callbackType: Int, result: ScanResult?) {
            result ?: return
            val scanRecord = result.scanRecord ?: return

            val serviceData = scanRecord.getServiceData(ParcelUuid(SERVICE_UUID))
                ?: scanRecord.serviceData.values.firstOrNull { BleMeshPacketCodec.decode(it) != null }

            val packet = serviceData?.let { BleMeshPacketCodec.decode(it) } ?: return
            val idHex = packet.transportTokenHex

            val now = System.currentTimeMillis()
            discoveredNodes[idHex] = now

            val activePeers = discoveredNodes.size
            _meshState.update { it.copy(nearbyDeviceCount = activePeers) }
            Timber.d("BLE_MESH: Scanned Node Hex=$idHex Type=${packet.packetType} Peers=$activePeers")

            if (packet.packetType == BleMeshPacketType.SOS || packet.packetType == BleMeshPacketType.INCIDENT_REPORT) {
                val packetHash = "SOS-RELAY-${idHex.take(12)}"
                if (isBleSosExpired(packet.hopCount)) {
                    scope.launch {
                        meshDeliveryEventDao.insertEvent(
                            MeshDeliveryEventEntity(
                                packetHash = packetHash,
                                packetType = packet.packetType.name,
                                eventType = "EXPIRED",
                                deviceIdentifier = idHex,
                                latitude = packet.latitude,
                                longitude = packet.longitude,
                                hopCount = packet.hopCount
                            )
                        )
                    }
                    Timber.d("BleMeshManager: Dropped expired BLE mesh SOS Hash=$packetHash Hop=${packet.hopCount}")
                    return
                }
                if (processedPacketHashes.containsKey(packetHash)) {
                    scope.launch {
                        meshDeliveryEventDao.insertEvent(
                            MeshDeliveryEventEntity(
                                packetHash = packetHash,
                                packetType = packet.packetType.name,
                                eventType = "DUPLICATE_DROPPED",
                                deviceIdentifier = idHex,
                                latitude = packet.latitude,
                                longitude = packet.longitude,
                                hopCount = packet.hopCount
                            )
                        )
                    }
                    return
                }
                processedPacketHashes[packetHash] = now
                handleRelayedPacket(packet, packetHash)
            } else if (packet.packetType == BleMeshPacketType.ACK || packet.packetType == BleMeshPacketType.STATUS) {
                val packetHash = "${packet.packetType.name}-${idHex.take(12)}-${packet.statusCode}"
                if (processedPacketHashes.containsKey(packetHash)) {
                    return
                }
                processedPacketHashes[packetHash] = now
                handleAcknowledgmentPacket(packet, packetHash)
            }
        }

        override fun onScanFailed(errorCode: Int) {
            Timber.e("BleMeshManager: Scan failed with error code $errorCode")
            _meshState.update { it.copy(isScanning = false) }
        }
    }

    private fun handleRelayedPacket(packet: BleMeshPacket, packetHash: String) {
        val myLocation = locationRepository.currentLocation.value
        val lat = if (packet.latitude in 4.0..21.0) packet.latitude else (myLocation?.latitude ?: 13.1391)
        val lon = if (packet.longitude in 116.0..127.0) packet.longitude else (myLocation?.longitude ?: 123.7438)

        scope.launch {
            val userRole = authTokenManager.userRole.first()
            val agencyType = authTokenManager.agencyId.first()
                ?.let { agencyId -> agencyDao.getAgencyById(agencyId)?.type }

            val existing = pendingPacketDao.getPacketByHash(packetHash)
            if (existing != null) {
                meshDeliveryEventDao.insertEvent(
                    MeshDeliveryEventEntity(
                        packetHash = packetHash,
                        packetType = "SOS",
                        eventType = "DUPLICATE_DROPPED",
                        deviceIdentifier = packet.transportTokenHex,
                        latitude = lat,
                        longitude = lon,
                        hopCount = packet.hopCount
                    )
                )
                Timber.d("BleMeshManager: Duplicate BLE mesh packet received (Hash=$packetHash). Already in local DB - ignoring.")
                return@launch
            }

            val newRelayedPacket = PendingPacketEntity(
                packetHash = packetHash,
                incidentUuid = packet.transportTokenHex,
                packetUuid = packet.transportTokenHex,
                targetId = "BLE_P2P",
                emergencyType = packet.emergencyType,
                latitude = lat,
                longitude = lon,
                timestamp = System.currentTimeMillis(),
                hopCount = packet.hopCount + 1,
                encryptedPayload = "MESH_V${packet.protocolVersion}_RELAY_HOP_${packet.hopCount + 1}"
            )

            pendingPacketDao.insertPacket(newRelayedPacket)
            meshDeliveryEventDao.insertEvent(
                MeshDeliveryEventEntity(
                    packetHash = packetHash,
                    packetType = "SOS",
                    eventType = "RECEIVED",
                    deviceIdentifier = packet.transportTokenHex,
                    latitude = lat,
                    longitude = lon,
                    hopCount = packet.hopCount
                )
            )
            meshDeliveryEventDao.insertEvent(
                MeshDeliveryEventEntity(
                    packetHash = packetHash,
                    packetType = "SOS",
                    eventType = "RELAYED",
                    deviceIdentifier = packet.transportTokenHex,
                    latitude = lat,
                    longitude = lon,
                    hopCount = packet.hopCount + 1
                )
            )
            if (shouldDisplayBleSosForRole(userRole, agencyType, packet.emergencyType)) {
                val liveIncident = IncidentEntity(
                    incidentId = packetHash,
                    targetId = "BLE_P2P",
                    emergencyType = packet.emergencyType,
                    latitude = lat,
                    longitude = lon,
                    timestamp = System.currentTimeMillis(),
                    status = "RECEIVED",
                    description = "Target agency: ${targetAgencyTypeForEmergency(packet.emergencyType)}",
                    channel = "BLE_MESH",
                    deliveryPath = "BLE_CLOUD_SHORTCUT",
                    senderDeviceId = packet.transportTokenHex
                )
                incidentDao.insertIncident(liveIncident)
            } else {
                Timber.d("BleMeshManager: Relayed BLE SOS Hash=$packetHash without displaying; role=$userRole agencyType=$agencyType emergency=${packet.emergencyType}")
            }
            _meshState.update { it.copy(relayedPacketCount = it.relayedPacketCount + 1) }

            Timber.i("BleMeshManager: Parsed BLE mesh SOS (Lat: $lat, Lon: $lon, Type: ${packet.emergencyType}, Version: ${packet.protocolVersion}). Displayed on Responder/Admin Map UI! Hash=$packetHash")
        }
    }

    private fun handleAcknowledgmentPacket(packet: BleMeshPacket, packetHash: String) {
        val myLocation = locationRepository.currentLocation.value
        val lat = if (packet.latitude in 4.0..21.0) packet.latitude else (myLocation?.latitude ?: 13.1391)
        val lon = if (packet.longitude in 116.0..127.0) packet.longitude else (myLocation?.longitude ?: 123.7438)
        val status = if (packet.packetType == BleMeshPacketType.ACK) "ACKNOWLEDGED" else "EN_ROUTE"

        scope.launch {
            meshDeliveryEventDao.insertEvent(
                MeshDeliveryEventEntity(
                    packetHash = packetHash,
                    packetType = packet.packetType.name,
                    eventType = "TARGET_REACHED",
                    deviceIdentifier = packet.transportTokenHex,
                    latitude = lat,
                    longitude = lon,
                    hopCount = packet.hopCount
                )
            )

            incidentDao.insertIncident(
                IncidentEntity(
                    incidentId = packetHash,
                    targetId = "BLE_P2P",
                    emergencyType = packet.emergencyType,
                    latitude = lat,
                    longitude = lon,
                    timestamp = System.currentTimeMillis(),
                    status = status,
                    description = "BLE ${packet.packetType.name} message #${packet.statusCode}",
                    packetHash = packetHash,
                    channel = "BLE_MESH",
                    deliveryPath = "BLE_CLOUD_SHORTCUT",
                    senderDeviceId = packet.transportTokenHex
                )
            )
            Timber.i("BleMeshManager: Received BLE ${packet.packetType.name} for ${packet.emergencyType} using message code ${packet.statusCode}.")
        }
    }

    private fun compactIdentifier(identifier: String): ByteArray {
        return MessageDigest.getInstance("SHA-256")
            .digest(identifier.toByteArray(Charsets.UTF_8))
            .copyOfRange(0, 8)
    }

    private fun compactIdentifierHex(identifier: String): String {
        return compactIdentifier(identifier).joinToString("") { "%02x".format(it) }
    }

    private val advertiseCallback = object : AdvertiseCallback() {
        override fun onStartSuccess(settingsInEffect: AdvertiseSettings?) {
            Timber.d("BleMeshManager: BLE Advertising packet sent successfully.")
            _meshState.update { it.copy(isAdvertising = true) }
        }

        override fun onStartFailure(errorCode: Int) {
            val errorName = when(errorCode) {
                ADVERTISE_FAILED_DATA_TOO_LARGE -> "DATA_TOO_LARGE"
                ADVERTISE_FAILED_TOO_MANY_ADVERTISERS -> "TOO_MANY_ADVERTISERS"
                ADVERTISE_FAILED_ALREADY_STARTED -> "ALREADY_STARTED"
                ADVERTISE_FAILED_INTERNAL_ERROR -> "INTERNAL_ERROR"
                ADVERTISE_FAILED_FEATURE_UNSUPPORTED -> "FEATURE_UNSUPPORTED"
                else -> "ERROR_$errorCode"
            }
            Timber.e("BleMeshManager: BLE Advertising failed: $errorName ($errorCode)")
            _meshState.update { it.copy(isAdvertising = false, activeAdPacketHash = null) }
        }
    }
}

internal const val BLE_MESH_MAX_HOPS = 15

internal fun isBleSosExpired(hopCount: Int): Boolean {
    return hopCount >= BLE_MESH_MAX_HOPS
}

internal fun targetAgencyTypeForEmergency(emergencyType: String): String {
    return when (emergencyType.uppercase()) {
        "MEDICAL" -> "MEDICAL"
        "FIRE" -> "FIRE"
        "CRIME" -> "POLICE"
        "ACCIDENT" -> "RESCUE"
        "DISASTER" -> "DRRMO"
        else -> "RESCUE"
    }
}

internal fun shouldDisplayBleSosForRole(userRole: String?, agencyType: String?, emergencyType: String): Boolean {
    return when (userRole?.uppercase()) {
        "ADMIN" -> true
        "RESPONDER" -> agencyType?.uppercase() == targetAgencyTypeForEmergency(emergencyType)
        else -> false
    }
}
