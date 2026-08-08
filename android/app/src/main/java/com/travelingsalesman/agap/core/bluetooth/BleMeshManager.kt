package com.travelingsalesman.agap.core.bluetooth

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattDescriptor
import android.bluetooth.BluetoothGattServer
import android.bluetooth.BluetoothGattServerCallback
import android.bluetooth.BluetoothGattService
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.bluetooth.BluetoothStatusCodes
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
import com.travelingsalesman.agap.data.local.dao.MeshDeliveryEventDao
import com.travelingsalesman.agap.data.local.dao.RelayDao
import com.travelingsalesman.agap.data.local.entity.MeshDeliveryEventEntity
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
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.security.MessageDigest
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Live BLE mesh state.
 *
 * Transport model (v5 fast warm GATT):
 *  - advertisements carry ONLY a tiny AGAP presence beacon;
 *  - scanning stays active while presence advertising is active;
 *  - up to three nearby peers are pre-connected and kept GATT-ready;
 *  - actual SOS / ACK / STATUS packets use WRITE_TYPE_NO_RESPONSE;
 *  - delivery is counted only after the receiver sends an application-level
 *    transport ACK notification after storing/already-seeing the packet.
 */
data class BleMeshState(
    val isSupported: Boolean = true,
    val isAdvertisingSupported: Boolean = true,
    val isEnabled: Boolean = false,
    val isScanning: Boolean = false,
    val isAdvertising: Boolean = false,
    val isGattServerRunning: Boolean = false,
    val isTransferring: Boolean = false,
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
    private val packetProcessor: BlePacketProcessor,
    private val relayDao: RelayDao,
    private val meshDeliveryEventDao: MeshDeliveryEventDao
) {
    companion object {
        /** Presence-beacon UUID kept compatible with the previous scanner. */
        val SERVICE_UUID: UUID = UUID.fromString("0000FEAF-0000-1000-8000-00805F9B34FB")

        /** Private AGAP GATT service used for reliable packet transfer. */
        val GATT_SERVICE_UUID: UUID = UUID.fromString("2f5a0001-6d8a-4f1d-9c47-a7e1e96a0001")
        val GATT_PACKET_CHARACTERISTIC_UUID: UUID = UUID.fromString("2f5a0002-6d8a-4f1d-9c47-a7e1e96a0001")
        val GATT_TRANSPORT_ACK_CHARACTERISTIC_UUID: UUID = UUID.fromString("2f5a0003-6d8a-4f1d-9c47-a7e1e96a0001")
        val CLIENT_CHARACTERISTIC_CONFIG_UUID: UUID = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb")

        private const val MESH_PULSE_MS = 1_500L
        private const val PEER_STALE_MS = 15_000L
        // BLE delivery itself has no scheduled delay. This is only a tiny guard
        // against reconnecting to the exact same failing peer in a hot loop.
        // A different/new peer is eligible immediately.
        private const val SAME_PEER_FAILURE_COOLDOWN_MS = 1_000L
        private const val SCAN_SILENCE_RESTART_MS = 60_000L
        private const val SCAN_HEALTH_LOG_INTERVAL_MS = 10_000L
        private const val PREFS_NAME = "agap_ble_mesh_v3"
        private const val PREF_LOCAL_PEER_ID = "local_peer_id_hex"
    }

    private data class PeerObservation(
        val peerIdHex: String,
        val device: BluetoothDevice,
        val lastSeenMs: Long,
        val rssi: Int
    )

    private val serviceParcelUuid = ParcelUuid(SERVICE_UUID)
    private val bluetoothManager: BluetoothManager? =
        context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager
    private val bluetoothAdapter: BluetoothAdapter? get() = bluetoothManager?.adapter

    private var bluetoothLeAdvertiser: BluetoothLeAdvertiser? = null
    private var bluetoothLeScanner: BluetoothLeScanner? = null
    private var gattServer: BluetoothGattServer? = null

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var periodicJob: Job? = null

    private val _meshState = MutableStateFlow(BleMeshState())
    val meshState: StateFlow<BleMeshState> = _meshState.asStateFlow()

    /** Unique peers are keyed by the installation-scoped ID inside the AGAP beacon. */
    private val discoveredPeers = ConcurrentHashMap<String, PeerObservation>()
    private val peerAttemptTimes = ConcurrentHashMap<String, Long>()
    private val transportAckSubscribers = ConcurrentHashMap.newKeySet<BluetoothDevice>()

    private val transferInProgress = AtomicBoolean(false)

    @Volatile
    private var scanStartedAtMs: Long = 0L

    @Volatile
    private var lastAnyScanCallbackMs: Long = 0L

    @Volatile
    private var lastScanHealthLogMs: Long = 0L

    @Volatile
    private var scanCallbackCount: Long = 0L

    private val localPeerIdHex: String = getOrCreateLocalPeerIdHex()
    private val localPeerBeacon: ByteArray = BlePeerBeaconCodec.encode(localPeerIdHex.hexToBytesOrNull() ?: ByteArray(6))

    /**
     * Fast path: nearby peers are connected before an emergency packet exists.
     * The normal scanner/advertiser continues independently while this pool keeps
     * up to three GATT client links ready for immediate writes.
     */
    private val warmGattPool = BleGattWarmPool(
        context = context,
        canConnect = { hasBluetoothConnectPermission() && bluetoothAdapter?.isEnabled == true },
        serviceUuid = GATT_SERVICE_UUID,
        packetCharacteristicUuid = GATT_PACKET_CHARACTERISTIC_UUID,
        ackCharacteristicUuid = GATT_TRANSPORT_ACK_CHARACTERISTIC_UUID,
        cccdUuid = CLIENT_CHARACTERISTIC_CONFIG_UUID,
        onReadyCountChanged = { readyCount ->
            BleMeshLog.d("WARM_GATT_POOL_STATE", "readyPeers" to readyCount)
            refreshNearbyDeviceCount()
            if (readyCount > 0) requestImmediateRelayPulse()
        },
        onPacketAcknowledged = ::onWarmGattPacketAcknowledged,
        onPacketFailed = ::onWarmGattPacketFailed
    )

    init {
        checkHardwareSupport()
        observeSuccessfulRelayCount()
    }

    /**
     * Keep the UI relay counter sourced from durable delivery events instead of
     * incrementing a session-local counter. This survives process restarts and
     * ignores the false RELAYED rows written by older queue-only builds.
     */
    private fun observeSuccessfulRelayCount() {
        scope.launch {
            meshDeliveryEventDao
                .observeSuccessfulRelayCount()
                .distinctUntilChanged()
                .collect { count ->
                    _meshState.update { it.copy(relayedPacketCount = count) }
                }
        }
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

    /**
     * Core transport permission deliberately excludes BLUETOOTH_ADVERTISE.
     * A phone that cannot advertise can still scan and GATT-connect to a peer and
     * therefore still forward its own queue. This also prevents endless permission
     * prompting when only the optional advertiser permission was denied.
     */
    private fun hasRequiredBluetoothPermissions(): Boolean =
        hasBluetoothScanPermission() && hasBluetoothConnectPermission()

    @SuppressLint("MissingPermission")
    fun checkHardwareSupport() {
        val adapter = bluetoothAdapter
        if (adapter == null) {
            _meshState.update {
                it.copy(
                    isSupported = false,
                    isAdvertisingSupported = false,
                    isEnabled = false,
                    isScanning = false,
                    isAdvertising = false,
                    isGattServerRunning = false,
                    lastError = "Bluetooth is not available on this device."
                )
            }
            BleMeshLog.e("HARDWARE_UNAVAILABLE", "sdk" to Build.VERSION.SDK_INT)
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
                lastError = null
            )
        }
        BleMeshLog.i(
            "HARDWARE_CHECK",
            "sdk" to Build.VERSION.SDK_INT,
            "enabled" to enabled,
            "advertiserSupported" to advertisingSupported,
            "scanPermission" to hasBluetoothScanPermission(),
            "advertisePermission" to hasBluetoothAdvertisePermission(),
            "connectPermission" to hasBluetoothConnectPermission(),
            "peerId" to BleMeshLog.token(localPeerIdHex)
        )
    }

    /** Starts concurrent scanner + presence advertiser + GATT server. Safe to call repeatedly. */
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
                    lastError = "Nearby devices permission is required for BLE peer discovery."
                )
            }
            return
        }

        val adapter = bluetoothAdapter ?: run {
            checkHardwareSupport()
            return
        }

        if (!adapter.isEnabled) {
            _meshState.update {
                it.copy(
                    isEnabled = false,
                    isScanning = false,
                    isAdvertising = false,
                    isGattServerRunning = false,
                    needsBluetoothEnable = true,
                    lastError = "Bluetooth is turned off."
                )
            }
            return
        }

        bluetoothLeScanner = adapter.bluetoothLeScanner
        bluetoothLeAdvertiser = if (hasBluetoothAdvertisePermission()) adapter.bluetoothLeAdvertiser else null

        _meshState.update {
            it.copy(
                isSupported = true,
                isEnabled = true,
                needsBluetoothEnable = false,
                needsPermissionsPrompt = false,
                isAdvertisingSupported = adapter.isMultipleAdvertisementSupported,
                lastError = null
            )
        }

        startGattServer()
        startScanning()
        startPresenceAdvertising()
        startPeriodicMeshPulse()
        requestImmediateRelayPulse()
    }

    fun stopMeshServices() {
        BleMeshLog.i("MESH_STOP_REQUEST")
        periodicJob?.cancel()
        periodicJob = null
        stopScanning()
        stopPresenceAdvertising()
        warmGattPool.closeAll()
        stopGattServer()
        discoveredPeers.clear()
        transportAckSubscribers.clear()
        peerAttemptTimes.clear()
        _meshState.update {
            it.copy(
                isScanning = false,
                isAdvertising = false,
                isGattServerRunning = false,
                isTransferring = false,
                nearbyDeviceCount = 0
            )
        }
    }

    fun onBluetoothStateChanged(enabled: Boolean) {
        BleMeshLog.i("BLUETOOTH_STATE", "enabled" to enabled)
        if (!enabled) {
            stopMeshServices()
            bluetoothLeScanner = null
            bluetoothLeAdvertiser = null
            _meshState.update {
                it.copy(
                    isEnabled = false,
                    needsBluetoothEnable = false,
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
            BleMeshLog.i("GATT_RELAY_SCHEDULER_STARTED", "pulseMs" to MESH_PULSE_MS)
            while (isActive) {
                pruneStaleObservations()
                pruneAttemptHistory()
                relayScheduler.cleanupExpired()

                if (hasRequiredBluetoothPermissions() && bluetoothAdapter?.isEnabled == true) {
                    ensureScanHealthy()
                    if (!_meshState.value.isScanning) startScanning()
                    if (hasBluetoothAdvertisePermission() && !_meshState.value.isAdvertising) {
                        startPresenceAdvertising()
                    }
                    syncWarmGattCandidates()
                    warmGattPool.maintainConnections()
                    relayPulse()
                }
                delay(MESH_PULSE_MS)
            }
        }
    }

    fun requestImmediateRelayPulse() {
        scope.launch {
            if (hasRequiredBluetoothPermissions() && bluetoothAdapter?.isEnabled == true) {
                relayPulse()
            } else {
                startMeshServices()
            }
        }
    }

    /**
     * Fast relay path. The packet is written only to an already-ready warm GATT
     * link; connection setup is kept out of the emergency send critical path.
     * Only one logical packet is in flight at a time so queue accounting and ACK
     * ownership remain deterministic, while scanning/advertising and warm-link
     * maintenance continue concurrently.
     */
    private suspend fun relayPulse() {
        if (!transferInProgress.compareAndSet(false, true)) return

        val queue = try {
            relayScheduler.getNextPacketToTransfer()
        } catch (e: Exception) {
            transferInProgress.set(false)
            BleMeshLog.e("RELAY_QUEUE_READ_FAILED", error = e)
            return
        }

        if (queue == null) {
            transferInProgress.set(false)
            return
        }

        val payload = queue.payloadBytesHex.hexToBytesOrNull()
        if (payload == null || BleMeshPacketCodec.decode(payload) == null) {
            transferInProgress.set(false)
            relayScheduler.removeQueueEntry(queue.queueId)
            BleMeshLog.e(
                "GATT_RELAY_INVALID_PAYLOAD",
                "queueId" to queue.queueId,
                "token" to BleMeshLog.token(queue.transportToken)
            )
            return
        }

        syncWarmGattCandidates()
        warmGattPool.maintainConnections()

        val now = System.currentTimeMillis()
        val excludedPeers = peerAttemptTimes
            .asSequence()
            .filter { (key, lastAttempt) ->
                key.startsWith("${queue.queueId}:") && now - lastAttempt < SAME_PEER_FAILURE_COOLDOWN_MS
            }
            .map { (key, _) -> key.substringAfter(':') }
            .toSet()

        val selectedPeerId = warmGattPool.trySend(
            queue = queue,
            payload = payload,
            excludedPeerIds = excludedPeers,
            onStarted = { peerId ->
                // Set UI/queue transfer state before the write is issued so an
                // extremely fast transport ACK cannot race ahead of this state.
                _meshState.update {
                    it.copy(
                        isTransferring = true,
                        activeAdPacketHash = queue.transportToken,
                        lastError = null
                    )
                }
                BleMeshLog.i(
                    "GATT_RELAY_FAST_PATH",
                    "queueId" to queue.queueId,
                    "token" to BleMeshLog.token(queue.transportToken),
                    "peer" to BleMeshLog.token(peerId),
                    "warmReady" to warmGattPool.readyPeerCount(),
                    "bytes" to payload.size
                )
            }
        )

        if (selectedPeerId == null) {
            transferInProgress.set(false)
            return
        }
    }

    private fun onWarmGattPacketAcknowledged(queue: RelayQueueEntity, peerIdHex: String) {
        scope.launch {
            try {
                relayDao.removeQueueEntry(queue.queueId)
                meshDeliveryEventDao.insertEvent(
                    MeshDeliveryEventEntity(
                        packetHash = queue.transportToken,
                        packetType = queue.packetType,
                        eventType = "RELAYED",
                        deviceIdentifier = peerIdHex,
                        hopCount = queue.hopCount
                    )
                )
                peerAttemptTimes.keys.removeAll { it.startsWith("${queue.queueId}:") }
                _meshState.update { state ->
                    state.copy(
                        isTransferring = false,
                        lastTxToken = queue.transportToken,
                        activeAdPacketHash = null,
                        lastError = null
                    )
                }
                BleMeshLog.i(
                    "GATT_RELAY_SUCCESS",
                    "queueId" to queue.queueId,
                    "token" to BleMeshLog.token(queue.transportToken),
                    "peer" to BleMeshLog.token(peerIdHex),
                    "mode" to "WARM_NO_RESPONSE_PLUS_APP_ACK",
                    "hop" to queue.hopCount
                )
            } catch (e: Exception) {
                BleMeshLog.e("GATT_RELAY_SUCCESS_DB_FAILED", error = e)
            } finally {
                transferInProgress.set(false)
                requestImmediateRelayPulse()
            }
        }
    }

    private fun onWarmGattPacketFailed(queue: RelayQueueEntity, peerIdHex: String, reason: String) {
        peerAttemptTimes[attemptKey(queue.queueId, peerIdHex)] = System.currentTimeMillis()
        _meshState.update {
            it.copy(
                isTransferring = false,
                activeAdPacketHash = null,
                lastError = null
            )
        }
        transferInProgress.set(false)

        scope.launch {
            runCatching { relayScheduler.markAttempted(queue) }
            BleMeshLog.w(
                "GATT_RELAY_RETRY",
                "queueId" to queue.queueId,
                "token" to BleMeshLog.token(queue.transportToken),
                "peer" to BleMeshLog.token(peerIdHex),
                "reason" to reason,
                "scheduledDelayMs" to 0
            )
            // Other ready peers can be selected almost immediately. The failed
            // peer alone is guarded by SAME_PEER_FAILURE_COOLDOWN_MS.
            delay(50L)
            requestImmediateRelayPulse()
        }
    }

    private fun syncWarmGattCandidates() {
        discoveredPeers.values.forEach { observation ->
            warmGattPool.updateCandidate(
                BleGattWarmPool.Candidate(
                    peerIdHex = observation.peerIdHex,
                    device = observation.device,
                    rssi = observation.rssi,
                    lastSeenMs = observation.lastSeenMs
                )
            )
        }
    }

    @SuppressLint("MissingPermission")
    private fun startGattServer() {
        if (!hasBluetoothConnectPermission()) {
            _meshState.update { it.copy(needsPermissionsPrompt = true, isGattServerRunning = false) }
            return
        }
        if (gattServer != null) return

        val manager = bluetoothManager ?: return
        try {
            val server = manager.openGattServer(context, inboundGattServerCallback) ?: run {
                _meshState.update { it.copy(isGattServerRunning = false) }
                BleMeshLog.e("GATT_SERVER_OPEN_FAILED")
                return
            }

            val service = BluetoothGattService(
                GATT_SERVICE_UUID,
                BluetoothGattService.SERVICE_TYPE_PRIMARY
            )
            val packetCharacteristic = BluetoothGattCharacteristic(
                GATT_PACKET_CHARACTERISTIC_UUID,
                BluetoothGattCharacteristic.PROPERTY_WRITE or
                    BluetoothGattCharacteristic.PROPERTY_WRITE_NO_RESPONSE,
                BluetoothGattCharacteristic.PERMISSION_WRITE
            )
            val transportAckCharacteristic = BluetoothGattCharacteristic(
                GATT_TRANSPORT_ACK_CHARACTERISTIC_UUID,
                BluetoothGattCharacteristic.PROPERTY_NOTIFY,
                BluetoothGattCharacteristic.PERMISSION_READ
            )
            transportAckCharacteristic.addDescriptor(
                BluetoothGattDescriptor(
                    CLIENT_CHARACTERISTIC_CONFIG_UUID,
                    BluetoothGattDescriptor.PERMISSION_READ or BluetoothGattDescriptor.PERMISSION_WRITE
                )
            )
            service.addCharacteristic(packetCharacteristic)
            service.addCharacteristic(transportAckCharacteristic)
            gattServer = server
            val added = server.addService(service)
            if (!added) {
                BleMeshLog.e("GATT_SERVICE_ADD_START_FAILED")
                stopGattServer()
            }
        } catch (e: Exception) {
            BleMeshLog.e("GATT_SERVER_START_FAILED", error = e)
            stopGattServer()
        }
    }

    @SuppressLint("MissingPermission")
    private val inboundGattServerCallback = object : BluetoothGattServerCallback() {
        override fun onServiceAdded(status: Int, service: BluetoothGattService) {
            if (service.uuid != GATT_SERVICE_UUID) return
            val running = status == BluetoothGatt.GATT_SUCCESS
            _meshState.update { it.copy(isGattServerRunning = running) }
            BleMeshLog.i("GATT_SERVICE_ADDED", "status" to status, "running" to running)
        }

        override fun onConnectionStateChange(device: BluetoothDevice, status: Int, newState: Int) {
            if (newState == BluetoothProfile.STATE_DISCONNECTED || status != BluetoothGatt.GATT_SUCCESS) {
                transportAckSubscribers.remove(device)
            }
            BleMeshLog.d(
                "GATT_SERVER_CONNECTION",
                "status" to status,
                "state" to newState,
                "ackSubscribers" to transportAckSubscribers.size
            )
        }

        override fun onDescriptorWriteRequest(
            device: BluetoothDevice,
            requestId: Int,
            descriptor: BluetoothGattDescriptor,
            preparedWrite: Boolean,
            responseNeeded: Boolean,
            offset: Int,
            value: ByteArray
        ) {
            val isAckCccd = descriptor.uuid == CLIENT_CHARACTERISTIC_CONFIG_UUID &&
                descriptor.characteristic.uuid == GATT_TRANSPORT_ACK_CHARACTERISTIC_UUID
            if (!isAckCccd || preparedWrite || offset != 0) {
                if (responseNeeded) sendGattServerResponse(device, requestId, BluetoothGatt.GATT_FAILURE)
                return
            }

            val enableNotifications = value.contentEquals(BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE) ||
                value.contentEquals(BluetoothGattDescriptor.ENABLE_INDICATION_VALUE)
            val disableNotifications = value.contentEquals(BluetoothGattDescriptor.DISABLE_NOTIFICATION_VALUE)
            when {
                enableNotifications -> transportAckSubscribers.add(device)
                disableNotifications -> transportAckSubscribers.remove(device)
                else -> {
                    if (responseNeeded) sendGattServerResponse(device, requestId, BluetoothGatt.GATT_FAILURE)
                    return
                }
            }

            BleMeshLog.d(
                "GATT_ACK_SUBSCRIPTION",
                "enabled" to enableNotifications,
                "subscribers" to transportAckSubscribers.size
            )
            if (responseNeeded) sendGattServerResponse(device, requestId, BluetoothGatt.GATT_SUCCESS)
        }

        override fun onCharacteristicWriteRequest(
            device: BluetoothDevice,
            requestId: Int,
            characteristic: BluetoothGattCharacteristic,
            preparedWrite: Boolean,
            responseNeeded: Boolean,
            offset: Int,
            value: ByteArray
        ) {
            if (characteristic.uuid != GATT_PACKET_CHARACTERISTIC_UUID || preparedWrite || offset != 0) {
                if (responseNeeded) sendGattServerResponse(device, requestId, BluetoothGatt.GATT_FAILURE)
                return
            }

            val decoded = BleMeshPacketCodec.decode(value)
            if (decoded == null) {
                BleMeshLog.w("GATT_RX_DECODE_REJECTED", "bytes" to value.size)
                if (responseNeeded) sendGattServerResponse(device, requestId, BluetoothGatt.GATT_FAILURE)
                return
            }

            scope.launch {
                val roleStr = authTokenManager.userRole.first()
                val role = UserRole.fromString(roleStr)
                val agencyType = authTokenManager.agencyId.first()
                    ?.let { agencyId -> agencyDao.getAgencyById(agencyId)?.type }

                // If an ACK notification was lost and the sender retries, an
                // already-seen packet is still a successful direct handoff. ACK it
                // again so the sender can safely remove its queue row.
                val alreadySeen = relayDao.getSeenPacket(
                    decoded.transportTokenHex,
                    decoded.seenIdentity()
                ) != null

                val accepted = runCatching {
                    packetProcessor.processIncomingPacket(
                        data = value,
                        userRole = role,
                        userAgencyType = agencyType
                    )
                }.getOrNull()

                val transportAccepted = accepted != null || alreadySeen
                if (transportAccepted) {
                    val packet = accepted ?: decoded
                    _meshState.update {
                        it.copy(
                            lastRxToken = packet.transportTokenHex,
                            lastError = null
                        )
                    }
                    BleMeshLog.i(
                        "GATT_RX_ACCEPTED",
                        "token" to BleMeshLog.token(packet.transportTokenHex),
                        "type" to packet.packetType.name,
                        "hop" to packet.hopCount,
                        "alreadySeen" to alreadySeen
                    )
                } else {
                    BleMeshLog.w(
                        "GATT_RX_REJECTED",
                        "token" to BleMeshLog.token(decoded.transportTokenHex),
                        "type" to decoded.packetType.name,
                        "hop" to decoded.hopCount
                    )
                }

                // WRITE_TYPE_NO_RESPONSE deliberately does not use the ATT write
                // response as delivery proof. Send a separate tiny notification
                // only after the application has processed/stored the packet.
                if (responseNeeded) {
                    sendGattServerResponse(
                        device = device,
                        requestId = requestId,
                        status = if (transportAccepted) BluetoothGatt.GATT_SUCCESS else BluetoothGatt.GATT_FAILURE
                    )
                }
                sendTransportAck(
                    device = device,
                    tokenHex = decoded.transportTokenHex,
                    accepted = transportAccepted
                )
                requestImmediateRelayPulse()
            }
        }
    }

    @SuppressLint("MissingPermission")
    private fun sendTransportAck(device: BluetoothDevice, tokenHex: String, accepted: Boolean) {
        if (!hasBluetoothConnectPermission()) return
        if (device !in transportAckSubscribers) {
            BleMeshLog.w(
                "GATT_TRANSPORT_ACK_NOT_SUBSCRIBED",
                "token" to BleMeshLog.token(tokenHex),
                "accepted" to accepted
            )
            return
        }

        val payload = BleGattTransportAckCodec.encode(tokenHex, accepted) ?: return
        val server = gattServer ?: return
        val characteristic = server
            .getService(GATT_SERVICE_UUID)
            ?.getCharacteristic(GATT_TRANSPORT_ACK_CHARACTERISTIC_UUID)
            ?: return

        val sent = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            runCatching {
                server.notifyCharacteristicChanged(
                    device,
                    characteristic,
                    false,
                    payload
                ) == BluetoothStatusCodes.SUCCESS
            }.getOrDefault(false)
        } else {
            @Suppress("DEPRECATION")
            runCatching {
                characteristic.value = payload
                server.notifyCharacteristicChanged(device, characteristic, false)
            }.getOrDefault(false)
        }

        BleMeshLog.d(
            "GATT_TRANSPORT_ACK_SENT",
            "token" to BleMeshLog.token(tokenHex),
            "accepted" to accepted,
            "started" to sent
        )
    }

    @SuppressLint("MissingPermission")
    private fun sendGattServerResponse(device: BluetoothDevice, requestId: Int, status: Int) {
        if (!hasBluetoothConnectPermission()) return
        runCatching {
            gattServer?.sendResponse(device, requestId, status, 0, null)
        }.onFailure {
            BleMeshLog.w("GATT_SERVER_RESPONSE_FAILED", "status" to status, error = it)
        }
    }

    @SuppressLint("MissingPermission")
    private fun stopGattServer() {
        val server = gattServer
        gattServer = null
        transportAckSubscribers.clear()
        runCatching { server?.clearServices() }
        runCatching { server?.close() }
        _meshState.update { it.copy(isGattServerRunning = false) }
    }

    /** Scanning never pauses for sending in the GATT transport. */
    @SuppressLint("MissingPermission")
    fun startScanning() {
        if (!hasBluetoothScanPermission() || !hasBluetoothConnectPermission()) {
            _meshState.update { it.copy(needsPermissionsPrompt = true, isScanning = false) }
            return
        }

        val adapter = bluetoothAdapter ?: return
        if (!adapter.isEnabled) {
            _meshState.update {
                it.copy(isEnabled = false, isScanning = false, needsBluetoothEnable = true)
            }
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
            val settings = ScanSettings.Builder()
                .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
                .setCallbackType(ScanSettings.CALLBACK_TYPE_ALL_MATCHES)
                .setReportDelay(0L)
                .build()

            // Unfiltered scan is intentionally retained for vendor compatibility;
            // only valid AGAP presence service-data is accepted below.
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
            BleMeshLog.i("SCAN_STARTED", "mode" to "LOW_LATENCY", "payload" to "PRESENCE_ONLY")
        } catch (e: SecurityException) {
            _meshState.update {
                it.copy(isScanning = false, needsPermissionsPrompt = true, lastError = e.message)
            }
            BleMeshLog.e("SCAN_PERMISSION_FAILURE", error = e)
        } catch (e: Exception) {
            _meshState.update {
                it.copy(isScanning = false, lastError = "BLE scan start failed: ${e.message}")
            }
            BleMeshLog.e("SCAN_START_FAILED", error = e)
        }
    }

    @SuppressLint("MissingPermission")
    fun stopScanning() {
        val scanner = bluetoothLeScanner
        runCatching {
            if (scanner != null && hasBluetoothScanPermission()) scanner.stopScan(scanCallback)
        }
        scanStartedAtMs = 0L
        lastAnyScanCallbackMs = 0L
        _meshState.update { it.copy(isScanning = false) }
    }

    @SuppressLint("MissingPermission")
    private fun startPresenceAdvertising() {
        if (!hasBluetoothAdvertisePermission()) {
            // Optional capability: keep scanning/GATT client operation alive without it.
            _meshState.update { it.copy(isAdvertising = false) }
            return
        }
        if (_meshState.value.isAdvertising) return

        val adapter = bluetoothAdapter ?: return
        if (!adapter.isEnabled) return
        val advertiser = bluetoothLeAdvertiser ?: adapter.bluetoothLeAdvertiser ?: run {
            _meshState.update { it.copy(isAdvertisingSupported = false, isAdvertising = false) }
            return
        }
        bluetoothLeAdvertiser = advertiser

        try {
            val settings = AdvertiseSettings.Builder()
                .setAdvertiseMode(AdvertiseSettings.ADVERTISE_MODE_LOW_LATENCY)
                .setTxPowerLevel(AdvertiseSettings.ADVERTISE_TX_POWER_MEDIUM)
                .setConnectable(true)
                .setTimeout(0)
                .build()

            val data = AdvertiseData.Builder()
                .addServiceData(serviceParcelUuid, localPeerBeacon)
                .setIncludeDeviceName(false)
                .setIncludeTxPowerLevel(false)
                .build()

            advertiser.startAdvertising(settings, data, presenceAdvertiseCallback)
            BleMeshLog.d("PRESENCE_ADVERTISE_START_REQUEST")
        } catch (e: Exception) {
            _meshState.update { it.copy(isAdvertising = false) }
            BleMeshLog.e("PRESENCE_ADVERTISE_START_FAILED", error = e)
        }
    }

    @SuppressLint("MissingPermission")
    private fun stopPresenceAdvertising() {
        val advertiser = bluetoothLeAdvertiser
        runCatching {
            if (advertiser != null && hasBluetoothAdvertisePermission()) {
                advertiser.stopAdvertising(presenceAdvertiseCallback)
            }
        }
        _meshState.update { it.copy(isAdvertising = false) }
    }

    private val presenceAdvertiseCallback = object : AdvertiseCallback() {
        override fun onStartSuccess(settingsInEffect: AdvertiseSettings?) {
            _meshState.update {
                it.copy(isAdvertising = true, isAdvertisingSupported = true, lastError = null)
            }
            BleMeshLog.i("PRESENCE_ADVERTISE_STARTED", "peerId" to BleMeshLog.token(localPeerIdHex))
        }

        override fun onStartFailure(errorCode: Int) {
            _meshState.update { it.copy(isAdvertising = false) }
            BleMeshLog.w(
                "PRESENCE_ADVERTISE_FAILED",
                "code" to errorCode,
                "reason" to advertiseErrorName(errorCode)
            )
            // Do not turn this into a permission/error loop. Scanning + outbound GATT
            // remains fully operational even when this vendor cannot advertise.
        }
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
            _meshState.update { it.copy(isScanning = false, lastError = "BLE scan failed: $reason") }
            BleMeshLog.e("SCAN_FAILED", "code" to errorCode, "reason" to reason)
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
                "scanning" to _meshState.value.isScanning,
                "advertisingPresence" to _meshState.value.isAdvertising,
                "gattTransfer" to _meshState.value.isTransferring
            )
        }

        val serviceData = result.scanRecord?.getServiceData(serviceParcelUuid) ?: return
        val peerIdHex = BlePeerBeaconCodec.decodePeerIdHex(serviceData) ?: return
        if (peerIdHex == localPeerIdHex) return

        registerPeer(result, peerIdHex, now)
    }

    @SuppressLint("MissingPermission")
    private fun registerPeer(result: ScanResult, peerIdHex: String, now: Long) {
        val observation = PeerObservation(
            peerIdHex = peerIdHex,
            device = result.device,
            lastSeenMs = now,
            rssi = result.rssi
        )
        val previous = discoveredPeers.put(peerIdHex, observation)
        warmGattPool.updateCandidate(
            BleGattWarmPool.Candidate(
                peerIdHex = observation.peerIdHex,
                device = observation.device,
                rssi = observation.rssi,
                lastSeenMs = observation.lastSeenMs
            )
        )
        warmGattPool.maintainConnections(now)
        pruneStaleObservations(now)

        if (previous == null || now - previous.lastSeenMs > PEER_STALE_MS / 2) {
            BleMeshLog.i(
                "PEER_DISCOVERED",
                "peer" to BleMeshLog.token(peerIdHex),
                "rssi" to result.rssi,
                "peerCount" to discoveredPeers.size
            )
            requestImmediateRelayPulse()
        }
    }

    @SuppressLint("MissingPermission")
    fun ensureBluetoothEnabled(): Boolean {
        if (!hasBluetoothConnectPermission()) {
            _meshState.update { it.copy(needsPermissionsPrompt = true) }
            return false
        }
        val adapter = bluetoothAdapter ?: return false
        val enabled = runCatching { adapter.isEnabled }.getOrDefault(false)
        _meshState.update { it.copy(isEnabled = enabled) }
        if (!enabled) _meshState.update { it.copy(needsBluetoothEnable = true) }
        return enabled
    }

    fun resetPermissionsPrompt() {
        _meshState.update { it.copy(needsPermissionsPrompt = false) }
    }

    fun resetBluetoothEnablePrompt() {
        _meshState.update { it.copy(needsBluetoothEnable = false) }
    }

    /** Compatibility API: queues a pending packet for GATT relay; it does not broadcast packet data. */
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
            val decoded = BleMeshPacketCodec.decode(bytes) ?: return@launch
            val queueId = relayScheduler.enqueueForRelay(decoded, bytes, isOriginLocal = packet.hopCount == 0)
            BleMeshLog.i(
                "LOCAL_PACKET_GATT_QUEUED",
                "queueId" to queueId,
                "token" to BleMeshLog.token(decoded.transportTokenHex),
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
        startAdvertisingAcknowledgmentByToken(tokenBytesFromIdentifier(incidentIdentifier), messageCode)
    }

    private fun queueResponsePacket(
        packetType: BleMeshPacketType,
        transportTokenBytes: ByteArray,
        statusCode: Int
    ) {
        scope.launch {
            val bytes = BleMeshPacketCodec.encodeResponsePacket(
                packetType = packetType,
                hopCount = 0,
                transportToken = transportTokenBytes.copyOf(6),
                statusCode = statusCode
            )
            val decoded = BleMeshPacketCodec.decode(bytes) ?: return@launch
            val queueId = relayScheduler.enqueueForRelay(decoded, bytes, isOriginLocal = true)
            BleMeshLog.i(
                "LOCAL_RESPONSE_GATT_QUEUED",
                "queueId" to queueId,
                "token" to BleMeshLog.token(decoded.transportTokenHex),
                "type" to packetType.name,
                "status" to statusCode
            )
            requestImmediateRelayPulse()
        }
    }

    /** Kept for old call sites; only the presence beacon is stopped. */
    fun stopAdvertising() = stopPresenceAdvertising()

    @SuppressLint("MissingPermission")
    private fun ensureScanHealthy() {
        if (!_meshState.value.isScanning) return
        val startedAt = scanStartedAtMs
        if (startedAt <= 0L) return

        val now = System.currentTimeMillis()
        val reference = if (lastAnyScanCallbackMs > 0L) lastAnyScanCallbackMs else startedAt
        val silenceMs = now - reference
        if (silenceMs < SCAN_SILENCE_RESTART_MS) return

        BleMeshLog.w("SCAN_WATCHDOG_RESTART", "silenceMs" to silenceMs)
        stopScanning()
        startScanning()
    }

    private fun refreshNearbyDeviceCount() {
        val uniqueNearby = discoveredPeers.keys.toSet() + warmGattPool.readyPeerIds()
        _meshState.update { it.copy(nearbyDeviceCount = uniqueNearby.size) }
    }

    private fun pruneStaleObservations(now: Long = System.currentTimeMillis()) {
        val before = discoveredPeers.size
        val stalePeerIds = discoveredPeers.entries
            .filter { now - it.value.lastSeenMs > PEER_STALE_MS }
            .map { it.key }
        stalePeerIds.forEach { peerId ->
            discoveredPeers.remove(peerId)
            warmGattPool.removeCandidate(peerId)
        }
        val connectedIds = warmGattPool.readyPeerIds()
        refreshNearbyDeviceCount()
        val uniqueNearbyCount = (discoveredPeers.keys.toSet() + connectedIds).size
        if (discoveredPeers.size != before) {
            BleMeshLog.d(
                "PEER_SET_CHANGED",
                "before" to before,
                "visible" to discoveredPeers.size,
                "warmReady" to connectedIds.size,
                "uniqueNearby" to uniqueNearbyCount
            )
        }
    }

    private fun pruneAttemptHistory(now: Long = System.currentTimeMillis()) {
        peerAttemptTimes.entries.removeAll { now - it.value > 5 * 60_000L }
    }

    private fun attemptKey(queueId: Long, peerIdHex: String): String = "$queueId:$peerIdHex"

    private fun getOrCreateLocalPeerIdHex(): String {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val existing = prefs.getString(PREF_LOCAL_PEER_ID, null)
        if (existing != null && existing.matches(Regex("^[0-9a-f]{12}$"))) return existing

        val generated = MessageDigest.getInstance("SHA-256")
            .digest(UUID.randomUUID().toString().toByteArray(Charsets.UTF_8))
            .copyOfRange(0, 6)
            .joinToString("") { "%02x".format(it) }
        prefs.edit().putString(PREF_LOCAL_PEER_ID, generated).apply()
        return generated
    }

    /** Preserve protocol token if already 12 hex; otherwise derive a stable 6-byte token. */
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
        return runCatching {
            chunked(2).map { it.toInt(16).toByte() }.toByteArray()
        }.getOrNull()
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
