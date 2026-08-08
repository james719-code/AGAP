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
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.ParcelUuid
import androidx.core.content.ContextCompat
import com.travelingsalesman.agap.data.local.AuthTokenManager
import com.travelingsalesman.agap.data.local.dao.AgencyDao
import com.travelingsalesman.agap.data.local.entity.PendingPacketEntity
import com.travelingsalesman.agap.data.local.entity.RelayQueueEntity
import com.travelingsalesman.agap.domain.model.LocationSource
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.security.MessageDigest
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Runtime state for AGAP's connectionless BLE mesh transport.
 */
data class BleMeshState(
    val isSupported: Boolean = true,
    val isAdvertisingSupported: Boolean = true,
    val isEnabled: Boolean = false,
    val isScanning: Boolean = false,
    val isAdvertising: Boolean = false,
    val nearbyDeviceCount: Int = 0,
    val relayedPacketCount: Int = 0,
    val activeAdPacketHash: String? = null,
    val needsPermissionsPrompt: Boolean = false,
    val needsBluetoothEnable: Boolean = false,
    val lastError: String? = null,
    val lastRxToken: String? = null,
    val lastTxToken: String? = null
)

@Singleton
class BleMeshManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val agencyDao: AgencyDao,
    private val authTokenManager: AuthTokenManager,
    private val relayScheduler: BleRelayScheduler,
    private val packetProcessor: BlePacketProcessor
) {
    companion object {
        // AGAP Mesh 16-bit Service Data UUID (0xFEAF).
        val SERVICE_UUID: UUID = UUID.fromString("0000FEAF-0000-1000-8000-00805F9B34FB")

        private const val MESH_PULSE_MS = 2_000L
        private const val PEER_STALE_MS = 30_000L
        private const val ADVERTISER_SETTLE_MS = 120L
        private const val ADVERTISE_START_TIMEOUT_MS = 5_000L
        private const val EMERGENCY_ADVERTISE_BURST_MS = 1_250L
        private const val SCAN_SILENCE_RESTART_MS = 60_000L
        private const val SCAN_HEALTH_LOG_INTERVAL_MS = 10_000L
        private const val PEER_PREFS = "agap_ble_mesh_identity"
        private const val PEER_ID_KEY = "peer_id_hex"

    }

    private val serviceParcelUuid = ParcelUuid(SERVICE_UUID)
    private val bluetoothManager: BluetoothManager? =
        context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager
    private val bluetoothAdapter: BluetoothAdapter? get() = bluetoothManager?.adapter

    private var bluetoothLeAdvertiser: BluetoothLeAdvertiser? = null
    private var bluetoothLeScanner: BluetoothLeScanner? = null

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var periodicJob: Job? = null
    private val radioMutex = Mutex()

    private val _meshState = MutableStateFlow(BleMeshState())
    val meshState: StateFlow<BleMeshState> = _meshState.asStateFlow()

    // Installation-scoped AGAP peers discovered through the tiny presence beacon.
    // Random nearby Bluetooth devices are intentionally not counted as mesh peers.
    private val discoveredPeers = ConcurrentHashMap<String, Long>()

    private enum class AdvertisementKind { NONE, PRESENCE, MESH_PACKET }

    private val localPeerIdBytes: ByteArray by lazy { loadOrCreatePeerId() }
    private val localPeerIdHex: String by lazy {
        localPeerIdBytes.joinToString("") { "%02x".format(it) }
    }

    @Volatile
    private var currentActivePayload: ByteArray? = null

    @Volatile
    private var pendingAdvertisedEntity: RelayQueueEntity? = null

    @Volatile
    private var pendingAdvertisementKind: AdvertisementKind = AdvertisementKind.NONE

    @Volatile
    private var activeAdvertisementKind: AdvertisementKind = AdvertisementKind.NONE

    private var emergencyBurstJob: Job? = null

    @Volatile
    private var advertiseRequestStartedAtMs: Long = 0L

    @Volatile
    private var scanStartedAtMs: Long = 0L

    @Volatile
    private var lastAnyScanCallbackMs: Long = 0L

    @Volatile
    private var lastScanHealthLogMs: Long = 0L

    @Volatile
    private var scanCallbackCount: Long = 0L

    init {
        checkHardwareSupport()
    }

    fun hasBluetoothScanPermission(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_SCAN) ==
                PackageManager.PERMISSION_GRANTED
        } else {
            ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) ==
                PackageManager.PERMISSION_GRANTED
        }
    }

    fun hasBluetoothAdvertisePermission(): Boolean {
        return Build.VERSION.SDK_INT < Build.VERSION_CODES.S ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_ADVERTISE) ==
            PackageManager.PERMISSION_GRANTED
    }

    fun hasBluetoothConnectPermission(): Boolean {
        return Build.VERSION.SDK_INT < Build.VERSION_CODES.S ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_CONNECT) ==
            PackageManager.PERMISSION_GRANTED
    }

    private fun hasRequiredBluetoothPermissions(): Boolean {
        return hasBluetoothScanPermission() &&
            hasBluetoothAdvertisePermission() &&
            hasBluetoothConnectPermission()
    }

    @SuppressLint("MissingPermission")
    fun checkHardwareSupport() {
        val adapter = bluetoothAdapter
        if (adapter == null) {
            BleMeshLog.e("HARDWARE_UNAVAILABLE", "sdk" to Build.VERSION.SDK_INT)
            _meshState.update {
                it.copy(
                    isSupported = false,
                    isAdvertisingSupported = false,
                    isEnabled = false,
                    isScanning = false,
                    isAdvertising = false,
                    lastError = "Bluetooth is not available on this device."
                )
            }
            return
        }

        val enabled = try {
            if (hasBluetoothConnectPermission()) adapter.isEnabled else false
        } catch (_: SecurityException) {
            false
        }

        val advertisingSupported = try {
            if (hasBluetoothConnectPermission()) adapter.isMultipleAdvertisementSupported else true
        } catch (_: Throwable) {
            true
        }

        _meshState.update {
            it.copy(
                isSupported = true,
                isAdvertisingSupported = advertisingSupported,
                isEnabled = enabled,
                lastError = if (!advertisingSupported) {
                    "This device reports that BLE peripheral advertising is unsupported."
                } else {
                    it.lastError
                }
            )
        }
        BleMeshLog.i(
            "HARDWARE_CHECK",
            "sdk" to Build.VERSION.SDK_INT,
            "enabled" to enabled,
            "advertiserSupported" to advertisingSupported,
            "scanPermission" to hasBluetoothScanPermission(),
            "advertisePermission" to hasBluetoothAdvertisePermission(),
            "connectPermission" to hasBluetoothConnectPermission()
        )
    }

    /** Starts scanning and the fair relay scheduler. Safe to call repeatedly. */
    @SuppressLint("MissingPermission")
    fun startMeshServices() {
        BleMeshLog.i(
            "MESH_START_REQUEST",
            "scanPermission" to hasBluetoothScanPermission(),
            "advertisePermission" to hasBluetoothAdvertisePermission(),
            "connectPermission" to hasBluetoothConnectPermission()
        )
        if (!hasRequiredBluetoothPermissions()) {
            _meshState.update {
                it.copy(
                    needsPermissionsPrompt = true,
                    isScanning = false,
                    lastError = "Nearby devices Bluetooth permissions are required for BLE mesh."
                )
            }
            BleMeshLog.w(
                "PERMISSION_MISSING",
                "scan" to hasBluetoothScanPermission(),
                "advertise" to hasBluetoothAdvertisePermission(),
                "connect" to hasBluetoothConnectPermission()
            )
            return
        }

        val adapter = bluetoothAdapter
        if (adapter == null) {
            checkHardwareSupport()
            return
        }

        if (!adapter.isEnabled) {
            BleMeshLog.w("BLUETOOTH_DISABLED")
            _meshState.update {
                it.copy(
                    isEnabled = false,
                    isScanning = false,
                    isAdvertising = false,
                    needsBluetoothEnable = true,
                    lastError = "Bluetooth is turned off."
                )
            }
            return
        }

        bluetoothLeScanner = adapter.bluetoothLeScanner
        bluetoothLeAdvertiser = adapter.bluetoothLeAdvertiser

        val advertiserSupported = bluetoothLeAdvertiser != null
        _meshState.update {
            it.copy(
                isSupported = true,
                isAdvertisingSupported = advertiserSupported,
                isEnabled = true,
                needsBluetoothEnable = false,
                needsPermissionsPrompt = false,
                lastError = if (!advertiserSupported) {
                    "BLE scanning is available, but this phone cannot advertise BLE packets."
                } else {
                    null
                }
            )
        }

        BleMeshLog.i(
            "MESH_RADIO_READY",
            "scannerAvailable" to (bluetoothLeScanner != null),
            "advertiserAvailable" to advertiserSupported,
            "peer" to BleMeshLog.token(localPeerIdHex)
        )
        startScanning()
        startPeriodicMeshPulse()
        requestImmediateRelayPulse()
    }

    fun stopMeshServices() {
        BleMeshLog.i("MESH_STOP_REQUEST")
        periodicJob?.cancel()
        periodicJob = null
        stopScanning()
        stopAdvertising()
    }

    /** Called by the foreground service when Android reports Bluetooth state changes. */
    fun onBluetoothStateChanged(enabled: Boolean) {
        BleMeshLog.i("BLUETOOTH_STATE", "enabled" to enabled)
        if (!enabled) {
            periodicJob?.cancel()
            periodicJob = null
            bluetoothLeScanner = null
            bluetoothLeAdvertiser = null
            currentActivePayload = null
            pendingAdvertisedEntity = null
            pendingAdvertisementKind = AdvertisementKind.NONE
            activeAdvertisementKind = AdvertisementKind.NONE
            emergencyBurstJob?.cancel()
            emergencyBurstJob = null
            advertiseRequestStartedAtMs = 0L
            scanStartedAtMs = 0L
            lastAnyScanCallbackMs = 0L
            lastScanHealthLogMs = 0L
            scanCallbackCount = 0L
            discoveredPeers.clear()
            _meshState.update {
                it.copy(
                    isEnabled = false,
                    isScanning = false,
                    isAdvertising = false,
                    nearbyDeviceCount = 0,
                    activeAdPacketHash = null,
                    lastError = "Bluetooth is turned off."
                )
            }
        } else {
            checkHardwareSupport()
            startMeshServices()
        }
    }

    private fun startPeriodicMeshPulse() {
        if (periodicJob?.isActive == true) return

        periodicJob = scope.launch {
            BleMeshLog.i("RADIO_SCHEDULER_STARTED", "pulseMs" to MESH_PULSE_MS)
            while (isActive) {
                pruneStaleObservations()
                relayScheduler.cleanupExpired()

                if (hasRequiredBluetoothPermissions() && bluetoothAdapter?.isEnabled == true) {
                    ensureScanHealthy()
                    if (!_meshState.value.isScanning) startScanning()
                    relayPulse()
                }
                delay(MESH_PULSE_MS)
            }
        }
    }

    /** Makes a newly queued local SOS eligible for radio transmission immediately. */
    fun requestImmediateRelayPulse() {
        scope.launch {
            if (hasRequiredBluetoothPermissions() && bluetoothAdapter?.isEnabled == true) {
                relayPulse()
            } else {
                startMeshServices()
            }
        }
    }

    private suspend fun relayPulse() = radioMutex.withLock {
        val scheduled = try {
            relayScheduler.getNextPacketToBroadcast()
        } catch (e: Exception) {
            BleMeshLog.e("RELAY_QUEUE_READ_FAILED", error = e)
            return@withLock
        }

        if (scheduled == null) {
            ensurePresenceAdvertisingLocked()
            return@withLock
        }

        BleMeshLog.d(
            "RELAY_SELECTED",
            "queueId" to scheduled.queueId,
            "token" to BleMeshLog.token(scheduled.transportToken),
            "type" to scheduled.packetType,
            "hop" to scheduled.hopCount,
            "advertiseCount" to scheduled.advertiseCount
        )

        val pending = pendingAdvertisedEntity
        if (pending != null && !_meshState.value.isAdvertising) {
            val inFlightForMs = System.currentTimeMillis() - advertiseRequestStartedAtMs
            if (inFlightForMs < ADVERTISE_START_TIMEOUT_MS) {
                BleMeshLog.d(
                    "ADVERTISE_START_IN_FLIGHT",
                    "queueId" to pending.queueId,
                    "token" to BleMeshLog.token(pending.transportToken),
                    "elapsedMs" to inFlightForMs
                )
                return@withLock
            }

            BleMeshLog.w(
                "ADVERTISE_START_WATCHDOG",
                "queueId" to pending.queueId,
                "token" to BleMeshLog.token(pending.transportToken),
                "elapsedMs" to inFlightForMs
            )
            bluetoothLeAdvertiser?.let(::stopAdvertisingInternal)
        }

        val payload = scheduled.payloadBytesHex.hexToBytesOrNull()
        if (payload == null) {
            BleMeshLog.e(
                "RELAY_PAYLOAD_INVALID",
                "queueId" to scheduled.queueId,
                "token" to BleMeshLog.token(scheduled.transportToken)
            )
            relayScheduler.removeQueueEntry(scheduled.queueId)
            return@withLock
        }

        // Advertising is continuous. If this exact payload is already on-air, it has
        // already been transmitted many times; simply advance its fair-scheduling time.
        if (_meshState.value.isAdvertising && currentActivePayload?.contentEquals(payload) == true) {
            BleMeshLog.d(
                "RELAY_ALREADY_ON_AIR",
                "queueId" to scheduled.queueId,
                "token" to BleMeshLog.token(scheduled.transportToken)
            )
            relayScheduler.markAdvertised(scheduled)
            return@withLock
        }

        val advertiser = bluetoothLeAdvertiser ?: bluetoothAdapter?.bluetoothLeAdvertiser
        if (advertiser == null) {
            BleMeshLog.e("ADVERTISER_UNAVAILABLE")
            _meshState.update {
                it.copy(
                    isAdvertisingSupported = false,
                    isAdvertising = false,
                    lastError = "BLE advertiser is unavailable on this phone."
                )
            }
            return@withLock
        }

        stopAdvertisingInternal(advertiser)
        delay(ADVERTISER_SETTLE_MS)
        startAdvertisingPayload(
            advertiser = advertiser,
            payload = payload,
            activeHash = scheduled.transportToken,
            scheduledEntity = scheduled,
            kind = AdvertisementKind.MESH_PACKET
        )
    }

    @SuppressLint("MissingPermission")
    fun startScanning() {
        if (!hasBluetoothScanPermission() || !hasBluetoothConnectPermission()) {
            _meshState.update { it.copy(needsPermissionsPrompt = true, isScanning = false) }
            return
        }

        val adapter = bluetoothAdapter ?: return
        if (!adapter.isEnabled) {
            _meshState.update { it.copy(isEnabled = false, isScanning = false, needsBluetoothEnable = true) }
            return
        }

        val scanner = bluetoothLeScanner ?: adapter.bluetoothLeScanner
        if (scanner == null) {
            _meshState.update { it.copy(isScanning = false, lastError = "BLE scanner is unavailable.") }
            return
        }
        bluetoothLeScanner = scanner

        if (_meshState.value.isScanning) return

        try {
            // Use an unfiltered foreground scan for maximum vendor compatibility.
            // Some Android BLE stacks silently miss service-data hardware filters even
            // though advertising works. AGAP still accepts ONLY FEAF packets below in
            // consumeScanResult(), so unrelated advertisements are discarded immediately.
            val settings = ScanSettings.Builder()
                .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
                .setCallbackType(ScanSettings.CALLBACK_TYPE_ALL_MATCHES)
                .setReportDelay(0L)
                .build()

            scanner.startScan(null, settings, scanCallback)
            scanStartedAtMs = System.currentTimeMillis()
            lastAnyScanCallbackMs = 0L
            lastScanHealthLogMs = scanStartedAtMs
            scanCallbackCount = 0L
            _meshState.update {
                it.copy(
                    isEnabled = true,
                    isScanning = true,
                    needsPermissionsPrompt = false,
                    lastError = null
                )
            }
            BleMeshLog.i(
                "SCAN_STARTED",
                "mode" to "LOW_LATENCY_UNFILTERED",
                "validation" to "FEAF_SERVICE_DATA_V2"
            )
        } catch (e: SecurityException) {
            _meshState.update {
                it.copy(isScanning = false, needsPermissionsPrompt = true, lastError = e.message)
            }
            BleMeshLog.e("SCAN_PERMISSION_FAILURE", error = e)
        } catch (e: Exception) {
            _meshState.update { it.copy(isScanning = false, lastError = "BLE scan start failed: ${e.message}") }
            BleMeshLog.e("SCAN_START_FAILED", "message" to e.message, error = e)
        }
    }

    @SuppressLint("MissingPermission")
    fun stopScanning() {
        val scanner = bluetoothLeScanner
        try {
            if (scanner != null && hasBluetoothScanPermission()) {
                scanner.stopScan(scanCallback)
            }
        } catch (e: Exception) {
            BleMeshLog.w("SCAN_STOP_FAILED", error = e)
        } finally {
            scanStartedAtMs = 0L
            lastAnyScanCallbackMs = 0L
            _meshState.update { it.copy(isScanning = false) }
            BleMeshLog.i("SCAN_STOPPED")
        }
    }

    @SuppressLint("MissingPermission")
    fun ensureBluetoothEnabled(): Boolean {
        if (!hasBluetoothConnectPermission()) {
            _meshState.update { it.copy(needsPermissionsPrompt = true) }
            return false
        }

        val adapter = bluetoothAdapter ?: return false
        val enabled = try {
            adapter.isEnabled
        } catch (_: SecurityException) {
            false
        }

        _meshState.update { it.copy(isEnabled = enabled) }
        if (!enabled) {
            _meshState.update { it.copy(needsBluetoothEnable = true) }
        }
        return enabled
    }

    fun resetPermissionsPrompt() {
        _meshState.update { it.copy(needsPermissionsPrompt = false) }
    }

    fun resetBluetoothEnablePrompt() {
        _meshState.update { it.copy(needsBluetoothEnable = false) }
    }

    /** Compatibility API used by Sent Messages. The packet is queued, not one-shot advertised. */
    fun startAdvertisingSosPacket(packet: PendingPacketEntity) {
        scope.launch {
            val token = tokenBytesFromIdentifier(packet.packetHash)
            val bytes = BleMeshPacketCodec.encodeEmergencyPacket(
                packetType = BleMeshPacketType.SOS,
                emergencyType = packet.emergencyType,
                immediateDanger = true,
                subjectRelation = "SELF",
                hopCount = packet.hopCount.coerceIn(0, BLE_MESH_MAX_HOPS),
                locationSource = LocationSource.GPS,
                transportToken = token,
                latitude = packet.latitude,
                longitude = packet.longitude
            )
            val decoded = BleMeshPacketCodec.decode(bytes) ?: run {
                BleMeshLog.e("LOCAL_PACKET_ENCODE_DECODE_FAILED", "type" to "SOS")
                return@launch
            }
            val queueId = relayScheduler.enqueueForRelay(decoded, bytes, isOriginLocal = true)
            BleMeshLog.i(
                "LOCAL_PACKET_QUEUED",
                "type" to decoded.packetType.name,
                "token" to BleMeshLog.token(decoded.transportTokenHex),
                "queueId" to queueId,
                "hop" to decoded.hopCount
            )
            requestImmediateRelayPulse()
        }
    }

    fun startAdvertisingAcknowledgmentByToken(
        transportTokenBytes: ByteArray,
        messageCode: Int = 1
    ) {
        queueResponsePacket(BleMeshPacketType.ACK, transportTokenBytes, messageCode)
    }

    fun startAdvertisingStatusByToken(
        transportTokenBytes: ByteArray,
        statusCode: Int
    ) {
        queueResponsePacket(BleMeshPacketType.STATUS, transportTokenBytes, statusCode)
    }

    fun startAdvertisingAcknowledgment(
        incidentIdentifier: String,
        @Suppress("UNUSED_PARAMETER") emergencyType: String,
        messageCode: Int = 1
    ) {
        startAdvertisingAcknowledgmentByToken(
            tokenBytesFromIdentifier(incidentIdentifier),
            messageCode
        )
    }

    private fun queueResponsePacket(
        packetType: BleMeshPacketType,
        transportTokenBytes: ByteArray,
        statusCode: Int
    ) {
        scope.launch {
            val exactToken = transportTokenBytes.copyOf(6)
            val bytes = BleMeshPacketCodec.encodeResponsePacket(
                packetType = packetType,
                hopCount = 0,
                transportToken = exactToken,
                statusCode = statusCode
            )
            val decoded = BleMeshPacketCodec.decode(bytes) ?: run {
                BleMeshLog.e("LOCAL_PACKET_ENCODE_DECODE_FAILED", "type" to packetType.name)
                return@launch
            }
            val queueId = relayScheduler.enqueueForRelay(decoded, bytes, isOriginLocal = true)
            BleMeshLog.i(
                "LOCAL_RESPONSE_QUEUED",
                "type" to packetType.name,
                "status" to statusCode,
                "token" to BleMeshLog.token(decoded.transportTokenHex),
                "queueId" to queueId
            )
            requestImmediateRelayPulse()
        }
    }

    @SuppressLint("MissingPermission")
    private fun startAdvertisingPayload(
        advertiser: BluetoothLeAdvertiser,
        payload: ByteArray,
        activeHash: String?,
        scheduledEntity: RelayQueueEntity?,
        kind: AdvertisementKind
    ) {
        if (!hasBluetoothAdvertisePermission() || !hasBluetoothConnectPermission()) {
            _meshState.update { it.copy(needsPermissionsPrompt = true, isAdvertising = false) }
            return
        }

        if (payload.size > BleMeshPacketCodec.EMERGENCY_PACKET_LENGTH) {
            _meshState.update {
                it.copy(isAdvertising = false, lastError = "BLE mesh payload is too large: ${payload.size} bytes.")
            }
            return
        }

        try {
            val settings = AdvertiseSettings.Builder()
                .setAdvertiseMode(AdvertiseSettings.ADVERTISE_MODE_LOW_LATENCY)
                .setTxPowerLevel(AdvertiseSettings.ADVERTISE_TX_POWER_HIGH)
                .setConnectable(false)
                .setTimeout(0)
                .build()

            // IMPORTANT: do NOT also addServiceUuid(). FEAF + 21-byte service data is
            // enough for receivers to identify AGAP and fits the legacy advertising budget.
            val data = AdvertiseData.Builder()
                .addServiceData(serviceParcelUuid, payload)
                .setIncludeDeviceName(false)
                .setIncludeTxPowerLevel(false)
                .build()

            pendingAdvertisedEntity = scheduledEntity
            pendingAdvertisementKind = kind
            advertiseRequestStartedAtMs = System.currentTimeMillis()
            currentActivePayload = payload.copyOf()
            _meshState.update {
                it.copy(
                    isAdvertising = false, // becomes true only in onStartSuccess
                    activeAdPacketHash = activeHash,
                    lastError = null
                )
            }

            BleMeshLog.d(
                "ADVERTISE_START_REQUEST",
                "bytes" to payload.size,
                "kind" to kind.name,
                "token" to BleMeshLog.token(scheduledEntity?.transportToken ?: activeHash),
                "queueId" to scheduledEntity?.queueId
            )
            advertiser.startAdvertising(settings, data, advertiseCallback)
        } catch (e: SecurityException) {
            pendingAdvertisedEntity = null
            pendingAdvertisementKind = AdvertisementKind.NONE
            activeAdvertisementKind = AdvertisementKind.NONE
            advertiseRequestStartedAtMs = 0L
            currentActivePayload = null
            _meshState.update {
                it.copy(
                    isAdvertising = false,
                    activeAdPacketHash = null,
                    needsPermissionsPrompt = true,
                    lastError = "BLE advertise permission denied."
                )
            }
            BleMeshLog.e("ADVERTISE_PERMISSION_FAILURE", error = e)
        } catch (e: Exception) {
            pendingAdvertisedEntity = null
            pendingAdvertisementKind = AdvertisementKind.NONE
            activeAdvertisementKind = AdvertisementKind.NONE
            advertiseRequestStartedAtMs = 0L
            currentActivePayload = null
            _meshState.update {
                it.copy(
                    isAdvertising = false,
                    activeAdPacketHash = null,
                    lastError = "BLE advertising failed to start: ${e.message}"
                )
            }
            BleMeshLog.e("ADVERTISE_START_EXCEPTION", "message" to e.message, error = e)
        }
    }

    @SuppressLint("MissingPermission")
    fun stopAdvertising() {
        val advertiser = bluetoothLeAdvertiser
        try {
            if (advertiser != null && hasBluetoothAdvertisePermission()) {
                stopAdvertisingInternal(advertiser)
            }
        } catch (e: Exception) {
            BleMeshLog.w("ADVERTISE_STOP_FAILED", error = e)
            emergencyBurstJob?.cancel()
            emergencyBurstJob = null
            currentActivePayload = null
            pendingAdvertisedEntity = null
            pendingAdvertisementKind = AdvertisementKind.NONE
            activeAdvertisementKind = AdvertisementKind.NONE
            advertiseRequestStartedAtMs = 0L
            _meshState.update { it.copy(isAdvertising = false, activeAdPacketHash = null) }
        }
    }

    @SuppressLint("MissingPermission")
    private fun stopAdvertisingInternal(advertiser: BluetoothLeAdvertiser) {
        try {
            advertiser.stopAdvertising(advertiseCallback)
        } catch (_: Exception) {
            // Vendor stacks occasionally throw when no advertiser is active.
        }
        emergencyBurstJob?.cancel()
        emergencyBurstJob = null
        currentActivePayload = null
        pendingAdvertisedEntity = null
        pendingAdvertisementKind = AdvertisementKind.NONE
        activeAdvertisementKind = AdvertisementKind.NONE
        advertiseRequestStartedAtMs = 0L
        _meshState.update { it.copy(isAdvertising = false, activeAdPacketHash = null) }
    }

    private val scanCallback = object : ScanCallback() {
        override fun onScanResult(callbackType: Int, result: ScanResult) {
            consumeScanResult(result)
        }

        override fun onBatchScanResults(results: MutableList<ScanResult>) {
            results.forEach(::consumeScanResult)
        }

        override fun onScanFailed(errorCode: Int) {
            val reason = scanErrorName(errorCode)
            BleMeshLog.e("SCAN_FAILED", "code" to errorCode, "reason" to reason)
            _meshState.update {
                it.copy(isScanning = false, lastError = "BLE scan failed: $reason")
            }

            // A transient Android BLE stack failure should not permanently kill mesh mode.
            scope.launch {
                delay(1_500L)
                if (bluetoothAdapter?.isEnabled == true) startScanning()
            }
        }
    }

    private fun consumeScanResult(result: ScanResult) {
        val now = System.currentTimeMillis()
        lastAnyScanCallbackMs = now
        scanCallbackCount += 1

        if (now - lastScanHealthLogMs >= SCAN_HEALTH_LOG_INTERVAL_MS) {
            lastScanHealthLogMs = now
            BleMeshLog.d(
                "SCAN_ACTIVITY",
                "callbacks" to scanCallbackCount,
                "peers" to discoveredPeers.size,
                "scanning" to _meshState.value.isScanning
            )
        }

        val serviceData = result.scanRecord?.getServiceData(serviceParcelUuid) ?: return

        val peerId = BlePeerBeaconCodec.decodePeerIdHex(serviceData)
        if (peerId != null) {
            if (peerId == localPeerIdHex) return

            val previous = discoveredPeers.put(peerId, now)
            pruneStaleObservations(now)
            _meshState.update {
                it.copy(
                    nearbyDeviceCount = discoveredPeers.size,
                    lastError = null
                )
            }

            if (previous == null || now - previous > PEER_STALE_MS) {
                BleMeshLog.i(
                    "PEER_DISCOVERED",
                    "peer" to BleMeshLog.token(peerId),
                    "rssi" to result.rssi,
                    "peerCount" to discoveredPeers.size
                )
            }
            return
        }

        val packet = BleMeshPacketCodec.decode(serviceData) ?: run {
            BleMeshLog.w("RX_DECODE_REJECTED", "bytes" to serviceData.size, "rssi" to result.rssi)
            return
        }

        BleMeshLog.d(
            "RX_PACKET",
            "token" to BleMeshLog.token(packet.transportTokenHex),
            "type" to packet.packetType.name,
            "status" to packet.statusCode,
            "hop" to packet.hopCount,
            "rssi" to result.rssi,
            "bytes" to serviceData.size
        )

        _meshState.update {
            it.copy(
                lastRxToken = packet.transportTokenHex,
                lastError = null
            )
        }

        scope.launch {
            val roleStr = authTokenManager.userRole.first()
            val role = UserRole.fromString(roleStr)
            val agencyType = authTokenManager.agencyId.first()
                ?.let { agencyId -> agencyDao.getAgencyById(agencyId)?.type }

            val accepted = packetProcessor.processIncomingPacket(
                data = serviceData,
                userRole = role,
                userAgencyType = agencyType
            )

            if (accepted != null) {
                BleMeshLog.i(
                    "RX_ACCEPTED",
                    "token" to BleMeshLog.token(accepted.transportTokenHex),
                    "type" to accepted.packetType.name,
                    "hop" to accepted.hopCount
                )
                _meshState.update { state ->
                    state.copy(relayedPacketCount = state.relayedPacketCount + 1)
                }
                requestImmediateRelayPulse()
            }
        }
    }

    private val advertiseCallback = object : AdvertiseCallback() {
        override fun onStartSuccess(settingsInEffect: AdvertiseSettings?) {
            val entity = pendingAdvertisedEntity
            val kind = pendingAdvertisementKind
            advertiseRequestStartedAtMs = 0L
            activeAdvertisementKind = kind

            _meshState.update {
                it.copy(
                    isAdvertising = true,
                    lastTxToken = if (kind == AdvertisementKind.MESH_PACKET) {
                        entity?.transportToken ?: it.lastTxToken
                    } else {
                        it.lastTxToken
                    },
                    lastError = null
                )
            }

            if (kind == AdvertisementKind.PRESENCE) {
                BleMeshLog.d(
                    "PEER_BEACON_STARTED",
                    "peer" to BleMeshLog.token(localPeerIdHex)
                )
                return
            }

            BleMeshLog.i(
                "ADVERTISE_STARTED",
                "token" to BleMeshLog.token(entity?.transportToken),
                "queueId" to entity?.queueId,
                "type" to entity?.packetType,
                "hop" to entity?.hopCount
            )

            if (entity != null) {
                scope.launch {
                    try {
                        relayScheduler.markAdvertised(entity)
                    } catch (e: Exception) {
                        BleMeshLog.e(
                            "RELAY_MARK_ADVERTISED_FAILED",
                            "queueId" to entity.queueId,
                            "token" to BleMeshLog.token(entity.transportToken),
                            error = e
                        )
                    }
                }

                emergencyBurstJob?.cancel()
                emergencyBurstJob = scope.launch {
                    delay(EMERGENCY_ADVERTISE_BURST_MS)
                    radioMutex.withLock {
                        if (activeAdvertisementKind == AdvertisementKind.MESH_PACKET &&
                            pendingAdvertisedEntity?.queueId == entity.queueId
                        ) {
                            BleMeshLog.d(
                                "ADVERTISE_BURST_COMPLETE",
                                "queueId" to entity.queueId,
                                "token" to BleMeshLog.token(entity.transportToken),
                                "durationMs" to EMERGENCY_ADVERTISE_BURST_MS
                            )
                            emergencyBurstJob = null
                            bluetoothLeAdvertiser?.let(::stopAdvertisingInternal)
                            delay(ADVERTISER_SETTLE_MS)
                            ensurePresenceAdvertisingLocked()
                        }
                    }
                }
            }
        }

        override fun onStartFailure(errorCode: Int) {
            val reason = advertiseErrorName(errorCode)
            BleMeshLog.e(
                "ADVERTISE_FAILED",
                "code" to errorCode,
                "reason" to reason,
                "kind" to pendingAdvertisementKind.name,
                "token" to BleMeshLog.token(pendingAdvertisedEntity?.transportToken)
            )
            advertiseRequestStartedAtMs = 0L
            pendingAdvertisedEntity = null
            pendingAdvertisementKind = AdvertisementKind.NONE
            activeAdvertisementKind = AdvertisementKind.NONE
            currentActivePayload = null
            _meshState.update {
                it.copy(
                    isAdvertising = false,
                    activeAdPacketHash = null,
                    lastError = "BLE advertising failed: $reason"
                )
            }
        }
    }

    @SuppressLint("MissingPermission")
    private fun ensureScanHealthy() {
        if (!_meshState.value.isScanning) return
        val startedAt = scanStartedAtMs
        if (startedAt <= 0L) return

        val now = System.currentTimeMillis()
        val reference = if (lastAnyScanCallbackMs > 0L) lastAnyScanCallbackMs else startedAt
        val silenceMs = now - reference
        if (silenceMs < SCAN_SILENCE_RESTART_MS) return

        BleMeshLog.w(
            "SCAN_WATCHDOG_RESTART",
            "silenceMs" to silenceMs,
            "hadAnyCallback" to (lastAnyScanCallbackMs > 0L)
        )
        stopScanning()
        startScanning()
    }

    private fun pruneStaleObservations(now: Long = System.currentTimeMillis()) {
        val before = discoveredPeers.size
        discoveredPeers.entries.removeAll { now - it.value > PEER_STALE_MS }
        val after = discoveredPeers.size
        if (after != before) {
            BleMeshLog.d(
                "PEER_SET_CHANGED",
                "before" to before,
                "after" to after
            )
        }
        _meshState.update { it.copy(nearbyDeviceCount = after) }
    }

    @SuppressLint("MissingPermission")
    private suspend fun ensurePresenceAdvertisingLocked() {
        if (!hasRequiredBluetoothPermissions() || bluetoothAdapter?.isEnabled != true) return

        if (activeAdvertisementKind == AdvertisementKind.PRESENCE && _meshState.value.isAdvertising) {
            return
        }

        if (pendingAdvertisementKind == AdvertisementKind.PRESENCE && !_meshState.value.isAdvertising) {
            val elapsed = System.currentTimeMillis() - advertiseRequestStartedAtMs
            if (advertiseRequestStartedAtMs > 0L && elapsed < ADVERTISE_START_TIMEOUT_MS) return
        }

        val advertiser = bluetoothLeAdvertiser ?: bluetoothAdapter?.bluetoothLeAdvertiser ?: return
        bluetoothLeAdvertiser = advertiser

        if (_meshState.value.isAdvertising || pendingAdvertisementKind != AdvertisementKind.NONE) {
            stopAdvertisingInternal(advertiser)
            delay(ADVERTISER_SETTLE_MS)
        }

        val beacon = BlePeerBeaconCodec.encode(localPeerIdBytes)
        startAdvertisingPayload(
            advertiser = advertiser,
            payload = beacon,
            activeHash = null,
            scheduledEntity = null,
            kind = AdvertisementKind.PRESENCE
        )
    }

    private fun loadOrCreatePeerId(): ByteArray {
        val prefs = context.getSharedPreferences(PEER_PREFS, Context.MODE_PRIVATE)
        val existing = prefs.getString(PEER_ID_KEY, null)
        val parsed = existing?.hexToBytesOrNull()
        if (parsed != null && parsed.size == 6) {
            return parsed
        }

        val created = BleMeshPacketCodec.generateTransportToken()
        val hex = created.joinToString("") { "%02x".format(it) }
        prefs.edit().putString(PEER_ID_KEY, hex).apply()
        BleMeshLog.i("PEER_ID_CREATED", "peer" to BleMeshLog.token(hex))
        return created
    }

    /**
     * If the identifier is already the protocol's 12-hex token, preserve it exactly.
     * Otherwise derive a stable 6-byte token for legacy call sites.
     */
    private fun tokenBytesFromIdentifier(identifier: String): ByteArray {
        val clean = identifier.trim().lowercase()
        if (clean.matches(Regex("^[0-9a-f]{12}$"))) {
            return clean.chunked(2).map { it.toInt(16).toByte() }.toByteArray()
        }
        return MessageDigest.getInstance("SHA-256")
            .digest(identifier.toByteArray(Charsets.UTF_8))
            .copyOfRange(0, 6)
    }

    private fun String.hexToBytesOrNull(): ByteArray? {
        if (length % 2 != 0 || !matches(Regex("^[0-9a-fA-F]+$"))) return null
        return try {
            chunked(2).map { it.toInt(16).toByte() }.toByteArray()
        } catch (_: Exception) {
            null
        }
    }

    private fun advertiseErrorName(errorCode: Int): String = when (errorCode) {
        AdvertiseCallback.ADVERTISE_FAILED_DATA_TOO_LARGE -> "DATA_TOO_LARGE"
        AdvertiseCallback.ADVERTISE_FAILED_TOO_MANY_ADVERTISERS -> "TOO_MANY_ADVERTISERS"
        AdvertiseCallback.ADVERTISE_FAILED_ALREADY_STARTED -> "ALREADY_STARTED"
        AdvertiseCallback.ADVERTISE_FAILED_INTERNAL_ERROR -> "INTERNAL_ERROR"
        AdvertiseCallback.ADVERTISE_FAILED_FEATURE_UNSUPPORTED -> "FEATURE_UNSUPPORTED"
        else -> "ERROR_$errorCode"
    }

    private fun scanErrorName(errorCode: Int): String = when (errorCode) {
        ScanCallback.SCAN_FAILED_ALREADY_STARTED -> "ALREADY_STARTED"
        ScanCallback.SCAN_FAILED_APPLICATION_REGISTRATION_FAILED -> "APP_REGISTRATION_FAILED"
        ScanCallback.SCAN_FAILED_INTERNAL_ERROR -> "INTERNAL_ERROR"
        ScanCallback.SCAN_FAILED_FEATURE_UNSUPPORTED -> "FEATURE_UNSUPPORTED"
        else -> "ERROR_$errorCode"
    }
}

internal const val BLE_MESH_MAX_HOPS = 15

internal fun isBleSosExpired(hopCount: Int): Boolean = hopCount >= BLE_MESH_MAX_HOPS

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

internal fun shouldDisplayBleSosForRole(
    userRole: String?,
    agencyType: String?,
    emergencyType: String
): Boolean {
    return when (userRole?.uppercase()) {
        "ADMIN" -> true
        "RESPONDER" -> agencyType?.uppercase() == targetAgencyTypeForEmergency(emergencyType)
        else -> false
    }
}
