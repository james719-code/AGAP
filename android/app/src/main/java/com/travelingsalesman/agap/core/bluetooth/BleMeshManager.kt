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
import com.travelingsalesman.agap.core.util.TargetIdEncoder
import com.travelingsalesman.agap.data.local.dao.PendingPacketDao
import com.travelingsalesman.agap.data.local.entity.PendingPacketEntity
import com.travelingsalesman.agap.location.LocationRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import timber.log.Timber
import java.nio.ByteBuffer
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

import com.travelingsalesman.agap.data.local.AuthTokenManager
import kotlinx.coroutines.flow.first

data class BleMeshState(
    val isSupported: Boolean = true,
    val isEnabled: Boolean = false,
    val isScanning: Boolean = false,
    val isAdvertising: Boolean = false,
    val nearbyDeviceCount: Int = 0,
    val relayedPacketCount: Int = 0,
    val activeAdPacketHash: String? = null,
    val needsPermissionsPrompt: Boolean = false
)

@Singleton
class BleMeshManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val pendingPacketDao: PendingPacketDao,
    private val incidentDao: com.travelingsalesman.agap.data.local.dao.IncidentDao,
    private val locationRepository: LocationRepository,
    private val authTokenManager: AuthTokenManager
) {
    companion object {
        // AGAP Mesh 16-bit Short Service UUID (0xFEAF)
        val SERVICE_UUID: UUID = UUID.fromString("0000FEAF-0000-1000-8000-00805F9B34FB")
        private val AGAP_HEADER = byteArrayOf(0x41, 0x47) // "AG"
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
            Timber.i("BLE_MESH: Started 2.0-second periodic scan & mesh pulse engine")
            while (isActive) {
                kotlinx.coroutines.delay(2000) // 2.0 second interval
                if (hasBluetoothScanPermission() && bluetoothAdapter?.isEnabled == true) {
                    // 1. Purge stale peer nodes (>15s since last seen)
                    val now = System.currentTimeMillis()
                    discoveredNodes.entries.removeAll { now - it.value > 15000 }
                    _meshState.update { it.copy(nearbyDeviceCount = discoveredNodes.size) }

                    // 2. Ensure scanning is active
                    if (!_meshState.value.isScanning) {
                        startScanning()
                    }

                    // 3. Continuous Mesh Broadcast: SOS Alert if pending, otherwise Presence Beacon
                    try {
                        val pending = pendingPacketDao.getAllPendingPackets()
                        if (pending.isNotEmpty()) {
                            val latestSos = pending.last()
                            startAdvertisingSosPacket(latestSos)
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

        val payload = buildPacketPayload(
            packetType = 1, // BEACON
            emergencyTypeStr = "NONE",
            hopCount = 0,
            idBytes = deviceNodeIdBytes,
            latitude = lat,
            longitude = lon
        )

        broadcastPayload(advertiser, payload, activeHash = null)
    }

    @SuppressLint("MissingPermission")
    fun ensureBluetoothEnabled(): Boolean {
        val adapter = bluetoothAdapter ?: return false
        if (!adapter.isEnabled) {
            try {
                adapter.enable()
                _meshState.update { it.copy(isEnabled = true) }
                Timber.i("BleMeshManager: Auto-enabled Bluetooth for Emergency SOS broadcast")
            } catch (e: Exception) {
                Timber.e(e, "BleMeshManager: Failed to auto-enable Bluetooth")
            }
        }
        return adapter.isEnabled
    }

    fun resetPermissionsPrompt() {
        _meshState.update { it.copy(needsPermissionsPrompt = false) }
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

        val payload = buildPacketPayload(
            packetType = 2, // SOS
            emergencyTypeStr = packet.emergencyType,
            hopCount = packet.hopCount,
            idBytes = hashBytes,
            latitude = packet.latitude,
            longitude = packet.longitude
        )

        broadcastPayload(advertiser, payload, activeHash = packet.packetHash)
    }

    private var currentActivePayload: ByteArray? = null

    @SuppressLint("MissingPermission")
    private fun broadcastPayload(advertiser: BluetoothLeAdvertiser, payload: ByteArray, activeHash: String?) {
        if (_meshState.value.isAdvertising && currentActivePayload != null && currentActivePayload.contentEquals(payload)) {
            // Already advertising this exact payload continuously - no need to restart
            return
        }

        try {
            stopAdvertising()

            val settings = AdvertiseSettings.Builder()
                .setAdvertiseMode(AdvertiseSettings.ADVERTISE_MODE_LOW_LATENCY)
                .setTxPowerLevel(AdvertiseSettings.ADVERTISE_TX_POWER_HIGH)
                .setConnectable(false)
                .build()

            // 4 bytes Service UUID + 21 bytes Service Data = 25 bytes total (fits legacy 31-byte limit)
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

    private fun buildPacketPayload(
        packetType: Byte,
        emergencyTypeStr: String,
        hopCount: Int,
        idBytes: ByteArray,
        latitude: Double,
        longitude: Double
    ): ByteArray {
        val buffer = ByteBuffer.allocate(17)
        buffer.put(AGAP_HEADER) // 2 bytes: "AG"
        buffer.put(packetType)  // 1 byte: 1 = BEACON, 2 = SOS
        
        val emergencyTypeByte: Byte = when (emergencyTypeStr.uppercase()) {
            "MEDICAL" -> 1
            "ACCIDENT" -> 2
            "CRIME" -> 3
            "DISASTER" -> 4
            else -> 0
        }
        buffer.put(emergencyTypeByte) // 1 byte
        buffer.put((hopCount and 0x0F).toByte()) // 1 byte

        val truncatedId = ByteArray(8) { i -> if (i < idBytes.size) idBytes[i] else 0 }
        buffer.put(truncatedId) // 8 bytes ID

        buffer.putShort(encodeLat(latitude))
        buffer.putShort(encodeLon(longitude))

        return buffer.array()
    }

    private fun encodeLat(lat: Double): Short {
        val clamped = lat.coerceIn(4.0, 21.0)
        return ((clamped - 4.0) * 3000).toInt().toShort()
    }

    private fun decodeLat(latShort: Short): Double {
        val raw = latShort.toInt() and 0xFFFF
        return 4.0 + (raw / 3000.0)
    }

    private fun encodeLon(lon: Double): Short {
        val clamped = lon.coerceIn(116.0, 127.0)
        return ((clamped - 116.0) * 3000).toInt().toShort()
    }

    private fun decodeLon(lonShort: Short): Double {
        val raw = lonShort.toInt() and 0xFFFF
        return 116.0 + (raw / 3000.0)
    }

    private val scanCallback = object : ScanCallback() {
        override fun onScanResult(callbackType: Int, result: ScanResult?) {
            result ?: return
            val scanRecord = result.scanRecord ?: return

            val serviceData = scanRecord.getServiceData(ParcelUuid(SERVICE_UUID))
                ?: scanRecord.serviceData.values.firstOrNull { it.size >= 17 && it[0] == 0x41.toByte() && it[1] == 0x47.toByte() }

            if (serviceData != null && serviceData.size >= 17 && serviceData[0] == 0x41.toByte() && serviceData[1] == 0x47.toByte()) {
                val packetType = serviceData[2].toInt()
                val emergencyTypeByte = serviceData[3].toInt()
                val hopCount = serviceData[4].toInt() and 0x0F
                val idBytes = serviceData.copyOfRange(5, 13)
                val idHex = idBytes.joinToString("") { "%02x".format(it) }

                val now = System.currentTimeMillis()
                discoveredNodes[idHex] = now

                val activePeers = discoveredNodes.size
                _meshState.update { it.copy(nearbyDeviceCount = activePeers) }
                Timber.d("BLE_MESH: Scanned Node Hex=$idHex Type=$packetType Peers=$activePeers")

                if (packetType == 2) { // SOS_ALERT
                    handleRelayedPacket(serviceData, idHex, emergencyTypeByte, hopCount)
                }
            }
        }

        override fun onScanFailed(errorCode: Int) {
            Timber.e("BleMeshManager: Scan failed with error code $errorCode")
            _meshState.update { it.copy(isScanning = false) }
        }
    }

    private fun handleRelayedPacket(
        payload: ByteArray,
        idHex: String,
        emergencyTypeByte: Int,
        hopCount: Int
    ) {
        val emergencyType = when (emergencyTypeByte) {
            1 -> "MEDICAL"
            2 -> "ACCIDENT"
            3 -> "CRIME"
            4 -> "DISASTER"
            else -> "MEDICAL"
        }

        val latShort = ByteBuffer.wrap(payload, 13, 2).short
        val lonShort = ByteBuffer.wrap(payload, 15, 2).short

        val decodedLat = decodeLat(latShort)
        val decodedLon = decodeLon(lonShort)

        val myLocation = locationRepository.currentLocation.value
        val lat = if (decodedLat in 4.0..21.0) decodedLat else (myLocation?.latitude ?: 13.1391)
        val lon = if (decodedLon in 116.0..127.0) decodedLon else (myLocation?.longitude ?: 123.7438)

        val packetHash = "SOS-RELAY-${idHex.take(12)}"

        scope.launch {
            val myUserId = authTokenManager.userId.first() ?: 0L

            val existing = pendingPacketDao.getPacketByHash(packetHash)
            if (existing != null) {
                Timber.d("BleMeshManager: Duplicate BLE mesh packet received (Hash=$packetHash). Already in local DB - ignoring.")
                return@launch
            }

            val newRelayedPacket = PendingPacketEntity(
                packetHash = packetHash,
                targetId = TargetIdEncoder.encodeTargetId(),
                emergencyType = emergencyType,
                latitude = lat,
                longitude = lon,
                timestamp = System.currentTimeMillis(),
                hopCount = hopCount + 1,
                encryptedPayload = "INDEFINITE_MESH_RELAY_HOP_${hopCount + 1}"
            )

            val liveIncident = com.travelingsalesman.agap.data.local.entity.IncidentEntity(
                incidentId = packetHash,
                targetId = TargetIdEncoder.encodeTargetId(),
                emergencyType = emergencyType,
                latitude = lat,
                longitude = lon,
                timestamp = System.currentTimeMillis(),
                status = "ACTIVE"
            )

            pendingPacketDao.insertPacket(newRelayedPacket)
            incidentDao.insertIncident(liveIncident)
            _meshState.update { it.copy(relayedPacketCount = it.relayedPacketCount + 1) }

            Timber.i("BleMeshManager: Parsed Compact BLE SOS (Lat: $lat, Lon: $lon, Type: $emergencyType). Displayed on Responder/Admin Map UI! Hash=$packetHash")
        }
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
