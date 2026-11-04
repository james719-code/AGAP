package com.travelingsalesman.agap.core.bluetooth

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.BluetoothGattCharacteristic
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
import com.travelingsalesman.agap.data.local.dao.MeshDeliveryEventDao
import com.travelingsalesman.agap.data.local.dao.RelayDao
import com.travelingsalesman.agap.data.local.entity.AdminAlertEntity
import com.travelingsalesman.agap.data.local.entity.MeshDeliveryEventEntity
import com.travelingsalesman.agap.data.local.entity.PendingPacketEntity
import com.travelingsalesman.agap.data.local.entity.RelayQueueEntity
import com.travelingsalesman.agap.domain.model.LocationSource
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collect
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
 * Live AGAP nearby-relay state.
 *
 * Transport model (v4):
 *  - Google Nearby Connections P2P_CLUSTER is preferred when healthy;
 *  - native BLE scanning + GATT remains a permanent non-Google fallback;
 *  - one shared queue arbiter prevents both transports sending the same work;
 *  - a relay is successful only after remote application persistence is ACKed.
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
    val lastTxToken: String? = null,
    val nearbyTransportAvailable: Boolean = false,
    val nearbyTransportActive: Boolean = false,
    val nearbyConnectedCount: Int = 0,
    val nearbyConnectedPeerIds: Set<String> = emptySet(),
    val activeTransport: String = "NONE",
    val activePeerNodeId: String? = null,
    val transferStage: String = "IDLE",
    val transferProgress: Float = 0f,
    val nearbyLastError: String? = null
)

@Singleton
class BleMeshManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val agencyDao: AgencyDao,
    private val authTokenManager: AuthTokenManager,
    private val relayScheduler: BleRelayScheduler,
    private val nearbyMeshManager: NearbyMeshManager,
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

        private const val MESH_PULSE_MS = 1_500L
        private const val PEER_STALE_MS = 15_000L
        // BLE delivery itself has no scheduled delay. This is only a tiny guard
        // against reconnecting to the exact same failing peer in a hot loop.
        // A different/new peer is eligible immediately.
        private const val SAME_PEER_FAILURE_COOLDOWN_MS = 1_000L
        private const val GATT_TRANSFER_TIMEOUT_MS = 12_000L
        private const val GATT_REQUESTED_MTU = 64
        private const val SCAN_SILENCE_RESTART_MS = 60_000L
        private const val SCAN_HEALTH_LOG_INTERVAL_MS = 10_000L
        private const val MAINTENANCE_INTERVAL_MS = 60_000L
        private const val NEARBY_HEALTH_RECHECK_MS = 15_000L
        private const val DUPLICATE_EVENT_RETENTION_MS = 24 * 60 * 60_000L
        private const val MAX_GATT_INBOUND_QUEUE = 16
        private const val BROADCAST_SPREAD_TARGET = 3
        private const val RELAY_SELECTION_WINDOW = 8
    }

    private data class PeerObservation(
        val peerIdHex: String,
        val device: BluetoothDevice,
        val lastSeenMs: Long,
        val rssi: Int
    )

    private data class OutboundTransfer(
        val queue: RelayQueueEntity,
        val peer: PeerObservation,
        val payload: ByteArray,
        val lease: RelayTransferGate.Lease,
        val discoveryStarted: AtomicBoolean = AtomicBoolean(false)
    )

    private data class GattInboundWrite(
        val device: BluetoothDevice,
        val requestId: Int,
        val responseNeeded: Boolean,
        val value: ByteArray,
        val decoded: BleMeshPacket,
        val queuedAtMs: Long = System.nanoTime() / 1_000_000
    )

    private val serviceParcelUuid = ParcelUuid(SERVICE_UUID)
    private val bluetoothManager: BluetoothManager? =
        context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager
    private val bluetoothAdapter: BluetoothAdapter? get() = bluetoothManager?.adapter

    private var bluetoothLeAdvertiser: BluetoothLeAdvertiser? = null
    private var bluetoothLeScanner: BluetoothLeScanner? = null
    private var gattServer: BluetoothGattServer? = null

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val gattInboundQueue = Channel<GattInboundWrite>(capacity = MAX_GATT_INBOUND_QUEUE)
    private var periodicJob: Job? = null
    private var transferTimeoutJob: Job? = null

    private val _meshState = MutableStateFlow(BleMeshState())
    val meshState: StateFlow<BleMeshState> = _meshState.asStateFlow()

    /** Unique peers are keyed by the installation-scoped ID inside the AGAP beacon. */
    private val discoveredPeers = ConcurrentHashMap<String, PeerObservation>()
    private val peerAttemptTimes = ConcurrentHashMap<String, Long>()

    private val transferGate = RelayTransferGate()
    private val scanStartGate = RadioStartGate()
    private val advertiseStartGate = RadioStartGate()
    private val immediatePulseScheduled = AtomicBoolean(false)
    private val activeTransferLock = Any()

    @Volatile
    private var queuedRelayCount: Int = 0

    @Volatile
    private var cachedUserRole: UserRole = UserRole.CITIZEN

    @Volatile
    private var cachedAgencyType: String? = null

    @Volatile
    private var lastMaintenanceAtMs: Long = 0L

    @Volatile
    private var lastNearbyHealthCheckAtMs: Long = 0L

    @Volatile
    private var activeTransfer: OutboundTransfer? = null

    @Volatile
    private var activeGatt: BluetoothGatt? = null

    @Volatile
    private var activeNearbyQueueId: Long? = null

    @Volatile
    private var scanStartedAtMs: Long = 0L

    @Volatile
    private var lastAnyScanCallbackMs: Long = 0L

    @Volatile
    private var lastScanHealthLogMs: Long = 0L

    @Volatile
    private var scanCallbackCount: Long = 0L

    private val localPeerIdHex: String = AgapRelayIdentity.getOrCreate(context)
    private val localPeerBeacon: ByteArray = BlePeerBeaconCodec.encode(localPeerIdHex.hexToBytesOrNull() ?: ByteArray(6))

    init {
        nearbyMeshManager.onRelayWorkAvailable = ::requestImmediateRelayPulse
        scope.launch {
            for (write in gattInboundQueue) {
                processGattInboundWrite(write)
            }
        }
        checkHardwareSupport()
        observeSuccessfulRelayCount()
        observeNearbyTransportState()
        observeRelayQueuePressure()
        observeUserContext()
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

    /**
     * Keep queue pressure in memory so the 1.5-second radio health pulse does not
     * hit Room when there is no relay work. A positive count also acts as the
     * event-driven wakeup for newly queued packets.
     */
    /** Cache role/agency once per auth change instead of reading DataStore + Room for every packet. */
    private fun observeUserContext() {
        scope.launch {
            authTokenManager.userRole
                .distinctUntilChanged()
                .collect { role ->
                    cachedUserRole = UserRole.fromString(role)
                }
        }
        scope.launch {
            authTokenManager.agencyId
                .distinctUntilChanged()
                .collect { agencyId ->
                    cachedAgencyType = agencyId
                        ?.let { id -> runCatching { agencyDao.getAgencyById(id)?.type }.getOrNull() }
                }
        }
    }

    private fun observeRelayQueuePressure() {
        scope.launch {
            relayScheduler.observeQueuedRelayCount()
                .distinctUntilChanged()
                .collect { count ->
                    queuedRelayCount = count.coerceAtLeast(0)
                    if (queuedRelayCount > 0) {
                        requestImmediateRelayPulse()
                    }
                }
        }
    }

    private fun observeNearbyTransportState() {
        scope.launch {
            nearbyMeshManager.state.collect { nearby ->
                _meshState.update { state ->
                    state.copy(
                        nearbyTransportAvailable = nearby.isPlayServicesAvailable && nearby.hasPermissions,
                        nearbyTransportActive = nearby.isHealthy,
                        nearbyConnectedCount = nearby.connectedPeerIds.size,
                        nearbyConnectedPeerIds = nearby.connectedPeerIds,
                        nearbyDeviceCount = (discoveredPeers.keys.toSet() + nearby.discoveredPeerIds + nearby.connectedPeerIds).size,
                        activePeerNodeId = if (state.activeTransport == "NEARBY") nearby.activePeerNodeId else state.activePeerNodeId,
                        transferStage = if (state.activeTransport == "NEARBY" && nearby.isTransferring) nearby.transferStage else state.transferStage,
                        transferProgress = if (state.activeTransport == "NEARBY" && nearby.isTransferring) nearby.transferProgress else state.transferProgress,
                        nearbyLastError = nearby.lastError
                    )
                }
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
        if (com.travelingsalesman.agap.BuildConfig.OFFLINE_TEST) return
        BleMeshLog.i(
            "MESH_START_REQUEST",
            "scanPermission" to hasBluetoothScanPermission(),
            "advertisePermission" to hasBluetoothAdvertisePermission(),
            "connectPermission" to hasBluetoothConnectPermission()
        )

        // Nearby is preferred when Google Play services + permissions are healthy,
        // but failure here must never prevent the native BLE/GATT baseline.
        nearbyMeshManager.ensureStarted()
        // The relay scheduler also services Nearby. Start it even when native
        // Bluetooth is unavailable so the preferred transport is not coupled to
        // the fallback radio lifecycle.
        startPeriodicMeshPulse()

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
        nearbyMeshManager.stop()
        synchronized(activeTransferLock) { activeNearbyQueueId = null }
        transferGate.cancelSending()
        periodicJob?.cancel()
        periodicJob = null
        transferTimeoutJob?.cancel()
        transferTimeoutJob = null
        stopScanning()
        stopPresenceAdvertising()
        stopGattServer()
        closeActiveGatt("mesh_stop")
        discoveredPeers.clear()
        peerAttemptTimes.clear()
        _meshState.update {
            it.copy(
                isScanning = false,
                isAdvertising = false,
                isGattServerRunning = false,
                isTransferring = false,
                nearbyDeviceCount = 0,
                nearbyConnectedCount = 0,
                nearbyConnectedPeerIds = emptySet(),
                activeTransport = "NONE",
                activePeerNodeId = null,
                transferStage = "IDLE",
                transferProgress = 0f
            )
        }
    }

    fun onBluetoothStateChanged(enabled: Boolean) {
        BleMeshLog.i("BLUETOOTH_STATE", "enabled" to enabled)
        if (!enabled) {
            // Pause only native BLE. Nearby is intentionally independent and may
            // continue using another transport selected by Google Play services.
            stopScanning()
            stopPresenceAdvertising()
            stopGattServer()
            closeActiveGatt("bluetooth_off")
            discoveredPeers.clear()
            peerAttemptTimes.clear()
            bluetoothLeScanner = null
            bluetoothLeAdvertiser = null
            _meshState.update {
                it.copy(
                    isEnabled = false,
                    isScanning = false,
                    isAdvertising = false,
                    isGattServerRunning = false,
                    needsBluetoothEnable = false,
                    nearbyDeviceCount = (nearbyMeshManager.state.value.discoveredPeerIds + nearbyMeshManager.state.value.connectedPeerIds).size,
                    lastError = "Bluetooth is turned off; Nearby remains preferred when available."
                )
            }
            nearbyMeshManager.ensureStarted()
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
                val now = System.currentTimeMillis()
                pruneStaleObservations()
                pruneAttemptHistory()

                // Expiry/duplicate cleanup performs database writes. Running it on
                // every 1.5-second radio pulse created needless WAL/invalidation load
                // under long-running deployments, so maintenance is minute-bounded.
                if (now - lastMaintenanceAtMs >= MAINTENANCE_INTERVAL_MS) {
                    lastMaintenanceAtMs = now
                    runCatching { relayScheduler.cleanupExpired() }
                        .onFailure { BleMeshLog.e("RELAY_MAINTENANCE_FAILED", error = it) }
                    runCatching {
                        meshDeliveryEventDao.deleteOldDuplicateEvents(
                            beforeMs = now - DUPLICATE_EVENT_RETENTION_MS
                        )
                    }.onFailure {
                        BleMeshLog.e("RELAY_EVENT_CLEANUP_FAILED", error = it)
                    }
                }

                // Healthy Nearby sessions do not need a Play Services capability
                // probe every 1.5 seconds. Recheck only when unhealthy/idle.
                if (
                    !nearbyMeshManager.state.value.isHealthy &&
                    now - lastNearbyHealthCheckAtMs >= NEARBY_HEALTH_RECHECK_MS
                ) {
                    lastNearbyHealthCheckAtMs = now
                    nearbyMeshManager.ensureStarted()
                }

                val nativeBleReady = hasRequiredBluetoothPermissions() && bluetoothAdapter?.isEnabled == true
                if (nativeBleReady) {
                    ensureScanHealthy()
                    if (!_meshState.value.isScanning) startScanning()
                    if (hasBluetoothAdvertisePermission() && !_meshState.value.isAdvertising) {
                        startPresenceAdvertising()
                    }
                }

                // The queue-count Flow is our hot-path guard. With an empty queue,
                // radio health remains active but Room is not queried every pulse.
                if (queuedRelayCount > 0 && (nearbyMeshManager.hasConnectedPeer() || nativeBleReady)) {
                    relayPulse()
                }
                delay(MESH_PULSE_MS)
            }
        }
    }

    fun requestImmediateRelayPulse() {
        if (com.travelingsalesman.agap.BuildConfig.OFFLINE_TEST) return
        if (!immediatePulseScheduled.compareAndSet(false, true)) return
        scope.launch {
            try {
                val nativeBleReady = hasRequiredBluetoothPermissions() && bluetoothAdapter?.isEnabled == true
                if (nearbyMeshManager.hasConnectedPeer() || nativeBleReady) {
                    relayPulse()
                } else {
                    // Re-evaluate both transports. This is idempotent and applies
                    // independent retry/backoff inside the individual managers.
                    startMeshServices()
                }
            } finally {
                immediatePulseScheduled.set(false)
            }
        }
    }

    /**
     * Picks one queued packet and one useful peer. Only one logical relay owns
     * the queue at a time. Nearby is preferred; native GATT is the immediate
     * fallback. Discovery may continue in parallel on both transport families.
     */
    private suspend fun relayPulse() {
        val now = System.currentTimeMillis()
        // An enabled adapter is not a peer. Avoid queue reads/decodes while alone.
        val nativePeers = discoveredPeers.values.filter { now - it.lastSeenMs <= PEER_STALE_MS }
        if (nativePeers.isEmpty() && !nearbyMeshManager.hasConnectedPeer()) return
        val lease = transferGate.tryAcquire() ?: return
        var handedOff = false
        try {
            for (queue in relayScheduler.getNextPacketsToTransfer(RELAY_SELECTION_WINDOW)) {
                if (!transferGate.isCurrent(lease)) return
                val payload = queue.payloadBytesHex.hexToBytesOrNull()
                if (payload == null || BleMeshPacketCodec.decode(payload) == null) {
                    relayScheduler.removeQueueEntry(queue.queueId)
                    BleMeshLog.e("GATT_RELAY_INVALID_PAYLOAD", "queueId" to queue.queueId,
                        "token" to BleMeshLog.token(queue.transportToken))
                    continue
                }
                val sourcePeerId = meshDeliveryEventDao.getLatestReceivedPeer(queue.transportToken)
                val excludedPeers = buildSet {
                    sourcePeerId?.let(::add)
                    if (queue.packetType.startsWith("ANNOUNCEMENT")) {
                        addAll(meshDeliveryEventDao.getAllRelayedPeersForPacket(queue.transportToken))
                    }
                }
                if (nearbyMeshManager.hasConnectedPeer(excludedPeers)) {
                    synchronized(activeTransferLock) { activeNearbyQueueId = queue.queueId }
                    lease.trace.advance("NEARBY_SENDING")
                    val nearbyStarted = nearbyMeshManager.trySend(
                        queueId = queue.queueId, payload = payload, excludedNodeIds = excludedPeers
                    ) { result -> scope.launch { handleNearbySendResult(queue, result, lease) } }
                    if (nearbyStarted) {
                        handedOff = true
                        synchronized(activeTransferLock) {
                            if (activeNearbyQueueId == queue.queueId && transferGate.isCurrent(lease)) {
                                _meshState.update {
                                    it.copy(isTransferring = true, activeAdPacketHash = queue.transportToken,
                                        activeTransport = "NEARBY",
                                        activePeerNodeId = nearbyMeshManager.state.value.activePeerNodeId,
                                        transferStage = nearbyMeshManager.state.value.transferStage,
                                        transferProgress = nearbyMeshManager.state.value.transferProgress, lastError = null)
                                }
                            }
                        }
                        BleMeshLog.i("RELAY_TRANSPORT_SELECTED", "transport" to "NEARBY",
                            "queueId" to queue.queueId, "attemptId" to lease.attemptId,
                            "token" to BleMeshLog.token(queue.transportToken),
                            "queueAgeMs" to (now - queue.createdAtMs).coerceAtLeast(0))
                        return
                    }
                    synchronized(activeTransferLock) {
                        if (activeNearbyQueueId == queue.queueId) activeNearbyQueueId = null
                    }
                }
                val peer = nativePeers.asSequence().filter { observation ->
                    val lastAttempt = peerAttemptTimes[attemptKey(queue.queueId, observation.peerIdHex)]
                    isRelayPeerEligible(observation.peerIdHex, excludedPeers,
                        lastAttempt?.plus(SAME_PEER_FAILURE_COOLDOWN_MS) ?: 0L, now)
                }.maxByOrNull { it.rssi }
                if (peer != null) {
                    peerAttemptTimes[attemptKey(queue.queueId, peer.peerIdHex)] = now
                    handedOff = true
                    beginGattTransfer(queue, peer, payload, lease)
                    return
                }
                // Keep priority order among sendable work; an exhausted broadcast
                // must not monopolize the queue while waiting for a new neighbor.
            }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Exception) {
            BleMeshLog.e("RELAY_SELECTION_FAILED", "attemptId" to lease.attemptId, error = error)
        } finally {
            if (!handedOff) transferGate.release(lease)
        }
    }

    private suspend fun handleNearbySendResult(
        queue: RelayQueueEntity,
        result: NearbySendResult,
        lease: RelayTransferGate.Lease
    ) {
        val ownsResult = synchronized(activeTransferLock) {
            if (activeNearbyQueueId != result.queueId || queue.queueId != result.queueId || !transferGate.beginPersistence(lease)) {
                false
            } else {
                activeNearbyQueueId = null
                true
            }
        }
        if (!ownsResult) return
        lease.trace.advance("PERSISTING")

        try {
            if (result.success) {
                try {
                    meshDeliveryEventDao.insertEvent(
                        MeshDeliveryEventEntity(
                            packetHash = queue.transportToken,
                            packetType = queue.packetType,
                            eventType = "RELAYED",
                            deviceIdentifier = result.peerNodeId,
                            hopCount = queue.hopCount
                        )
                    )
                
                    val uniqueRelays = meshDeliveryEventDao.getUniqueRelayCount(queue.transportToken)
                    val isAnnouncement = queue.packetType.startsWith("ANNOUNCEMENT")
                    // Announcements spread sequentially to up to 3 neighbors
                    val shouldRemove = !isAnnouncement || uniqueRelays >= BROADCAST_SPREAD_TARGET
                
                    if (shouldRemove) {
                        relayDao.removeQueueEntry(queue.queueId)
                        peerAttemptTimes.keys.removeAll { it.startsWith("${queue.queueId}:") }
                    } else {
                        // Mark as attempted so it goes back into the queue for other neighbors
                        relayScheduler.markAttempted(queue)
                    }

                    _meshState.update { state ->
                        state.copy(
                            isTransferring = false,
                            lastTxToken = queue.transportToken,
                            activeAdPacketHash = null,
                            activeTransport = "NONE",
                            activePeerNodeId = result.peerNodeId,
                            transferStage = "DELIVERED",
                            transferProgress = 1f,
                            lastError = null
                        )
                    }
                    val deliveredTiming = lease.trace.advance("DELIVERED")
                    BleMeshLog.i(
                        "NEARBY_RELAY_SUCCESS",
                        "attemptId" to lease.attemptId,
                        "elapsedMs" to deliveredTiming.elapsedMs,
                        "persistenceMs" to deliveredTiming.previousStageMs,
                        "queueId" to queue.queueId,
                        "token" to BleMeshLog.token(queue.transportToken),
                        "peer" to BleMeshLog.token(result.peerNodeId),
                        "hop" to queue.hopCount
                    )
                } catch (error: Exception) {
                    BleMeshLog.e("NEARBY_RELAY_SUCCESS_DB_FAILED", error = error)
                }
            } else {
                runCatching { relayScheduler.markAttempted(queue) }
                _meshState.update {
                    it.copy(
                        isTransferring = false,
                        activeAdPacketHash = null,
                        activeTransport = "NONE",
                        activePeerNodeId = result.peerNodeId,
                        transferStage = "FALLBACK",
                        transferProgress = 0f,
                        // Nearby failure is diagnostic only; BLE is still a live fallback.
                        nearbyLastError = result.reason
                    )
                }
                BleMeshLog.w(
                    "NEARBY_RELAY_FALLBACK",
                    "attemptId" to lease.attemptId,
                    "elapsedMs" to lease.trace.advance("FALLBACK").elapsedMs,
                    "queueId" to queue.queueId,
                    "token" to BleMeshLog.token(queue.transportToken),
                    "peer" to BleMeshLog.token(result.peerNodeId),
                    "reason" to (result.reason ?: "unknown")
                )
            }
        } finally {
            transferGate.release(lease)
            // The failed peer is cooled down; another peer is eligible immediately.
            requestImmediateRelayPulse()
        }
    }

    @SuppressLint("MissingPermission")
    private fun beginGattTransfer(
        queue: RelayQueueEntity,
        peer: PeerObservation,
        payload: ByteArray,
        lease: RelayTransferGate.Lease
    ) {
        if (!transferGate.isCurrent(lease)) return
        if (!hasBluetoothConnectPermission()) {
            transferGate.release(lease)
            _meshState.update { it.copy(needsPermissionsPrompt = true) }
            return
        }

        val transfer = OutboundTransfer(queue = queue, peer = peer, payload = payload, lease = lease)
        synchronized(activeTransferLock) {
            activeTransfer = transfer
        }

        _meshState.update {
            it.copy(
                isTransferring = true,
                activeAdPacketHash = queue.transportToken,
                activeTransport = "BLE_GATT",
                activePeerNodeId = peer.peerIdHex,
                transferStage = "CONNECTING",
                transferProgress = 0.18f,
                lastError = null
            )
        }

        BleMeshLog.i(
            "GATT_RELAY_CONNECT",
            "attemptId" to lease.attemptId,
            "queueId" to queue.queueId,
            "token" to BleMeshLog.token(queue.transportToken),
            "peer" to BleMeshLog.token(peer.peerIdHex),
            "rssi" to peer.rssi,
            "bytes" to payload.size,
            "hop" to queue.hopCount
        )
        logGattTransferStage(transfer, "CONNECTING")

        try {
            val gatt = peer.device.connectGatt(
                context,
                false,
                outboundGattCallback,
                BluetoothDevice.TRANSPORT_LE
            )
            if (gatt == null) {
                failActiveTransfer(null, "connectGatt returned null")
                return
            }

            // Some vendor Bluetooth stacks can dispatch the connection callback
            // extremely quickly. Only attach this GATT object if this exact transfer
            // is still active; otherwise a completed/stale callback could leave a
            // closed GATT instance installed as the current connection.
            val stillActive = synchronized(activeTransferLock) {
                if (activeTransfer === transfer) {
                    if (activeGatt == null) activeGatt = gatt
                    activeGatt === gatt
                } else {
                    false
                }
            }
            if (!stillActive) {
                runCatching { gatt.close() }
                return
            }

            transferTimeoutJob?.cancel()
            transferTimeoutJob = scope.launch {
                delay(GATT_TRANSFER_TIMEOUT_MS)
                if (isActiveGatt(gatt)) {
                    val timeoutTiming = lease.trace.advance("TIMED_OUT")
                    BleMeshLog.w(
                        "GATT_RELAY_TIMEOUT",
                        "attemptId" to lease.attemptId,
                        "elapsedMs" to timeoutTiming.elapsedMs,
                        "failedStage" to timeoutTiming.previousStage,
                        "queueId" to queue.queueId,
                        "token" to BleMeshLog.token(queue.transportToken),
                        "peer" to BleMeshLog.token(peer.peerIdHex)
                    )
                    failActiveTransfer(gatt, "timeout")
                }
            }
        } catch (e: Exception) {
            failActiveTransfer(null, "connect exception: ${e.message}")
        }
    }

    private fun isActiveGatt(gatt: BluetoothGatt): Boolean = synchronized(activeTransferLock) {
        activeGatt === gatt
    }

    private fun logGattTransferStage(transfer: OutboundTransfer, stage: String): RelayTransferTrace.Timing {
        val timing = transfer.lease.trace.advance(stage)
        BleMeshLog.d("GATT_TRANSFER_STAGE", "queueId" to transfer.queue.queueId,
            "attemptId" to transfer.lease.attemptId, "token" to BleMeshLog.token(transfer.queue.transportToken),
            "peer" to BleMeshLog.token(transfer.peer.peerIdHex), "stage" to timing.stage,
            "previousStage" to timing.previousStage, "elapsedMs" to timing.elapsedMs,
            "previousStageMs" to timing.previousStageMs)
        return timing
    }

    @SuppressLint("MissingPermission")
    private fun discoverGattServices(gatt: BluetoothGatt) {
        val transfer = synchronized(activeTransferLock) {
            activeTransfer?.takeIf { activeGatt === gatt }
        } ?: return
        if (!transfer.discoveryStarted.compareAndSet(false, true)) return
        _meshState.update { it.copy(transferStage = "DISCOVERING", transferProgress = 0.44f) }
        logGattTransferStage(transfer, "DISCOVERING")
        if (!runCatching { gatt.discoverServices() }.getOrDefault(false)) {
            failActiveTransfer(gatt, "service discovery could not start")
        }
    }

    /**
     * Validates a callback against the current transfer and also closes the tiny
     * connectGatt-return/callback race where activeGatt has not been assigned yet.
     */
    private fun claimOrValidateActiveGatt(gatt: BluetoothGatt): Boolean = synchronized(activeTransferLock) {
        val transfer = activeTransfer ?: return@synchronized false
        val current = activeGatt
        if (current == null) {
            if (transfer.peer.device != gatt.device) return@synchronized false
            activeGatt = gatt
            true
        } else {
            current === gatt
        }
    }

    @SuppressLint("MissingPermission")
    private val outboundGattCallback = object : BluetoothGattCallback() {
        override fun onConnectionStateChange(gatt: BluetoothGatt, status: Int, newState: Int) {
            if (!claimOrValidateActiveGatt(gatt)) {
                runCatching { gatt.close() }
                return
            }

            if (status == BluetoothGatt.GATT_SUCCESS && newState == BluetoothProfile.STATE_CONNECTED) {
                val transfer = activeTransfer ?: return
                if (!needsGattMtuExchange(transfer.payload.size)) {
                    BleMeshLog.d("GATT_MTU_SKIPPED", "attemptId" to transfer.lease.attemptId,
                        "bytes" to transfer.payload.size, "reason" to "fits_default_att_payload")
                    discoverGattServices(gatt)
                    return
                }
                _meshState.update { it.copy(transferStage = "NEGOTIATING", transferProgress = 0.32f) }
                logGattTransferStage(transfer, "NEGOTIATING")
                BleMeshLog.d("GATT_CONNECTED", "status" to status)
                val mtuRequestStarted = runCatching { gatt.requestMtu(GATT_REQUESTED_MTU) }.getOrDefault(false)
                if (!mtuRequestStarted) {
                    discoverGattServices(gatt)
                }
                return
            }

            if (newState == BluetoothProfile.STATE_DISCONNECTED || status != BluetoothGatt.GATT_SUCCESS) {
                failActiveTransfer(gatt, "connection status=$status state=$newState")
            }
        }

        override fun onMtuChanged(gatt: BluetoothGatt, mtu: Int, status: Int) {
            if (!isActiveGatt(gatt)) return
            BleMeshLog.d("GATT_MTU", "mtu" to mtu, "status" to status)
            discoverGattServices(gatt)
        }

        override fun onServicesDiscovered(gatt: BluetoothGatt, status: Int) {
            if (!isActiveGatt(gatt)) return
            if (status != BluetoothGatt.GATT_SUCCESS) {
                failActiveTransfer(gatt, "service discovery failed: $status")
                return
            }

            val characteristic = gatt
                .getService(GATT_SERVICE_UUID)
                ?.getCharacteristic(GATT_PACKET_CHARACTERISTIC_UUID)

            if (characteristic == null) {
                failActiveTransfer(gatt, "AGAP packet characteristic missing")
                return
            }

            val transfer = synchronized(activeTransferLock) {
                activeTransfer?.takeIf { activeGatt === gatt }
            } ?: return
            val payload = transfer.payload

            _meshState.update { it.copy(transferStage = "SENDING", transferProgress = 0.68f) }
            logGattTransferStage(transfer, "SENDING")

            val writeStarted = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                gatt.writeCharacteristic(
                    characteristic,
                    payload,
                    BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT
                ) == BluetoothStatusCodes.SUCCESS
            } else {
                @Suppress("DEPRECATION")
                run {
                    characteristic.writeType = BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT
                    characteristic.value = payload
                    gatt.writeCharacteristic(characteristic)
                }
            }

            if (!writeStarted) {
                failActiveTransfer(gatt, "characteristic write could not start")
            } else {
                // A fast ACK may already have detached this transfer. Never put
                // the UI back into AWAITING_ACK after delivery has completed.
                synchronized(activeTransferLock) {
                    if (activeTransfer === transfer && activeGatt === gatt) {
                        _meshState.update { it.copy(transferStage = "AWAITING_ACK", transferProgress = 0.84f) }
                        logGattTransferStage(transfer, "AWAITING_ACK")
                    }
                }
                BleMeshLog.d("GATT_WRITE_STARTED", "bytes" to payload.size)
            }
        }

        override fun onCharacteristicWrite(
            gatt: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic,
            status: Int
        ) {
            if (!isActiveGatt(gatt) || characteristic.uuid != GATT_PACKET_CHARACTERISTIC_UUID) return
            if (status == BluetoothGatt.GATT_SUCCESS) {
                completeActiveTransfer(gatt)
            } else {
                failActiveTransfer(gatt, "remote rejected packet: $status")
            }
        }
    }

    /**
     * Processes server-side GATT writes on one bounded consumer instead of
     * launching an unbounded coroutine for every Bluetooth callback. The GATT
     * success response is intentionally sent only after the packet is durably
     * accepted (or confirmed as an already-held duplicate), preserving AGAP's
     * receiver-possession ACK semantics.
     */
    private suspend fun processGattInboundWrite(write: GattInboundWrite) {
        val processingStartedAt = System.nanoTime() / 1_000_000
        val sourcePeerId = discoveredPeers.values
            .firstOrNull { observation -> observation.device == write.device }
            ?.peerIdHex

        val alreadySeen = relayDao.getSeenPacket(
            write.decoded.transportTokenHex,
            write.decoded.seenIdentity()
        ) != null

        val accepted = runCatching {
            packetProcessor.processIncomingPacket(
                data = write.value,
                userRole = cachedUserRole,
                userAgencyType = cachedAgencyType,
                sourcePeerId = sourcePeerId
            )
        }.onFailure { error ->
            BleMeshLog.e(
                "GATT_RX_PROCESS_FAILED",
                "token" to BleMeshLog.token(write.decoded.transportTokenHex),
                error = error
            )
        }.getOrNull()

        // Nearby and GATT may receive the same logical packet concurrently.
        // The shared processor mutex serializes the persistence path; this
        // second lookup lets the losing transport truthfully ACK possession.
        val seenAfterProcessing = alreadySeen || relayDao.getSeenPacket(
            write.decoded.transportTokenHex,
            write.decoded.seenIdentity()
        ) != null

        val shouldAck = accepted != null || seenAfterProcessing
        if (!shouldAck) {
            if (write.responseNeeded) {
                sendGattServerResponse(
                    write.device,
                    write.requestId,
                    BluetoothGatt.GATT_FAILURE
                )
            }
            return
        }

        val rxPacket = accepted ?: write.decoded
        _meshState.update {
            it.copy(
                lastRxToken = rxPacket.transportTokenHex,
                lastError = null
            )
        }

        BleMeshLog.i(
            "GATT_RX_ACCEPTED",
            "queueWaitMs" to (processingStartedAt - write.queuedAtMs).coerceAtLeast(0),
            "processingMs" to (System.nanoTime() / 1_000_000 - processingStartedAt).coerceAtLeast(0),
            "token" to BleMeshLog.token(rxPacket.transportTokenHex),
            "type" to rxPacket.packetType.name,
            "hop" to rxPacket.hopCount,
            "duplicate" to (accepted == null && seenAfterProcessing),
            "sourcePeer" to BleMeshLog.token(sourcePeerId)
        )

        if (write.responseNeeded) {
            sendGattServerResponse(
                write.device,
                write.requestId,
                BluetoothGatt.GATT_SUCCESS
            )
        }
        requestImmediateRelayPulse()
    }

    @SuppressLint("MissingPermission")
    private fun completeActiveTransfer(gatt: BluetoothGatt) {
        val transfer = detachActiveTransfer(gatt) ?: return
        logGattTransferStage(transfer, "PERSISTING")
        runCatching { gatt.disconnect() }
        runCatching { gatt.close() }

        scope.launch {
            try {
                meshDeliveryEventDao.insertEvent(
                    MeshDeliveryEventEntity(
                        packetHash = transfer.queue.transportToken,
                        packetType = transfer.queue.packetType,
                        eventType = "RELAYED",
                        deviceIdentifier = transfer.peer.peerIdHex,
                        hopCount = transfer.queue.hopCount
                    )
                )

                val uniqueRelays = meshDeliveryEventDao.getUniqueRelayCount(transfer.queue.transportToken)
                val isAnnouncement = transfer.queue.packetType.startsWith("ANNOUNCEMENT")
                val shouldRemove = !isAnnouncement || uniqueRelays >= BROADCAST_SPREAD_TARGET

                if (shouldRemove) {
                    relayDao.removeQueueEntry(transfer.queue.queueId)
                    peerAttemptTimes.keys.removeAll { it.startsWith("${transfer.queue.queueId}:") }
                } else {
                    relayScheduler.markAttempted(transfer.queue)
                }

                _meshState.update { state ->
                    state.copy(
                        isTransferring = false,
                        lastTxToken = transfer.queue.transportToken,
                        activeAdPacketHash = null,
                        activeTransport = "NONE",
                        activePeerNodeId = transfer.peer.peerIdHex,
                        transferStage = "DELIVERED",
                        transferProgress = 1f,
                        lastError = null
                    )
                }
                val deliveredTiming = logGattTransferStage(transfer, "DELIVERED")
                BleMeshLog.i(
                    "GATT_RELAY_SUCCESS",
                    "attemptId" to transfer.lease.attemptId,
                    "elapsedMs" to deliveredTiming.elapsedMs,
                    "persistenceMs" to deliveredTiming.previousStageMs,
                    "queueId" to transfer.queue.queueId,
                    "token" to BleMeshLog.token(transfer.queue.transportToken),
                    "peer" to BleMeshLog.token(transfer.peer.peerIdHex),
                    "hop" to transfer.queue.hopCount
                )
            } catch (e: Exception) {
                BleMeshLog.e("GATT_RELAY_SUCCESS_DB_FAILED", error = e)
            } finally {
                transferGate.release(transfer.lease)
                requestImmediateRelayPulse()
            }
        }
    }

    @SuppressLint("MissingPermission")
    private fun failActiveTransfer(gatt: BluetoothGatt?, reason: String) {
        val transfer = detachActiveTransfer(gatt) ?: return
        val failureTiming = transfer.lease.trace.advance("RETRYING")
        runCatching { gatt?.disconnect() }
        runCatching { gatt?.close() }

        _meshState.update {
            it.copy(
                isTransferring = false,
                activeAdPacketHash = null,
                activeTransport = "NONE",
                activePeerNodeId = transfer.peer.peerIdHex,
                transferStage = "RETRYING",
                transferProgress = 0f,
                lastError = null
            )
        }

        scope.launch {
            try {
                // A legacy peer may reject the v3 identity extension. Keep the
                // origin identity in Room, but retry this peer with an anonymous
                // v2 frame so older AGAP installations remain interoperable.
                if (transfer.payload.firstOrNull()?.toInt()?.ushr(6)?.and(0x03) == 3) {
                    val packet = BleMeshPacketCodec.decode(transfer.payload)
                    if (packet != null) {
                        val legacyPayload = BleMeshPacketCodec.encodeEmergencyPacket(
                            packetType = packet.packetType,
                            emergencyType = packet.emergencyType,
                            immediateDanger = packet.immediateDanger,
                            subjectRelation = packet.subjectRelation,
                            hopCount = packet.hopCount,
                            locationSource = packet.locationSource,
                            transportToken = packet.transportToken,
                            latitude = packet.latitude,
                            longitude = packet.longitude,
                            horizontalAccuracyMeters = packet.horizontalAccuracyMeters,
                            creationTimeSlot = packet.creationTimeSlot,
                            originDeviceId = null
                        )
                        relayDao.enqueueRelay(
                            transfer.queue.copy(
                                payloadBytesHex = legacyPayload.toMeshHex()
                            )
                        )
                        BleMeshLog.i(
                            "GATT_RELAY_V3_LEGACY_FALLBACK",
                            "token" to BleMeshLog.token(transfer.queue.transportToken),
                            "peer" to BleMeshLog.token(transfer.peer.peerIdHex)
                        )
                    }
                }
                // Record the failed attempt, but DO NOT delay the BLE queue.
                // The packet stays immediately eligible for every other discovered peer.
                runCatching { relayScheduler.markAttempted(transfer.queue) }
                BleMeshLog.w(
                    "GATT_RELAY_RETRY",
                    "attemptId" to transfer.lease.attemptId,
                    "elapsedMs" to failureTiming.elapsedMs,
                    "failedStage" to failureTiming.previousStage,
                    "queueId" to transfer.queue.queueId,
                    "token" to BleMeshLog.token(transfer.queue.transportToken),
                    "peer" to BleMeshLog.token(transfer.peer.peerIdHex),
                    "reason" to reason
                )
                // Retry quickly. The exact same peer is protected by the 1-second
                // cooldown above; a different peer can be chosen immediately.
            } finally {
                transferGate.release(transfer.lease)
                requestImmediateRelayPulse()
            }
        }
    }

    private fun detachActiveTransfer(gatt: BluetoothGatt?): OutboundTransfer? {
        synchronized(activeTransferLock) {
            if (gatt != null && activeGatt !== gatt) return null
            val transfer = activeTransfer ?: return null
            if (!transferGate.beginPersistence(transfer.lease)) return null
            transferTimeoutJob?.cancel()
            transferTimeoutJob = null
            activeTransfer = null
            activeGatt = null
            return transfer
        }
    }

    @SuppressLint("MissingPermission")
    private fun closeActiveGatt(reason: String) {
        val gatt: BluetoothGatt?
        val hadGattTransfer: Boolean
        synchronized(activeTransferLock) {
            gatt = activeGatt
            hadGattTransfer = activeGatt != null || activeTransfer != null
            activeTransfer?.let { transferGate.release(it.lease) }
            activeGatt = null
            activeTransfer = null
            if (hadGattTransfer) {
                transferTimeoutJob?.cancel()
                transferTimeoutJob = null
            }
        }
        runCatching { gatt?.disconnect() }
        runCatching { gatt?.close() }
        if (hadGattTransfer) {
            val nearbyStillActive = synchronized(activeTransferLock) { activeNearbyQueueId != null }
            _meshState.update {
                it.copy(
                    isTransferring = nearbyStillActive,
                    activeAdPacketHash = if (nearbyStillActive) it.activeAdPacketHash else null,
                    activeTransport = if (nearbyStillActive) "NEARBY" else "NONE",
                    activePeerNodeId = if (nearbyStillActive) it.activePeerNodeId else null,
                    transferStage = if (nearbyStillActive) it.transferStage else "IDLE",
                    transferProgress = if (nearbyStillActive) it.transferProgress else 0f
                )
            }
        }
        if (gatt != null) BleMeshLog.d("GATT_CLOSED", "reason" to reason)
    }

    @SuppressLint("MissingPermission")
    @Synchronized
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
            val characteristic = BluetoothGattCharacteristic(
                GATT_PACKET_CHARACTERISTIC_UUID,
                BluetoothGattCharacteristic.PROPERTY_WRITE,
                BluetoothGattCharacteristic.PERMISSION_WRITE
            )
            service.addCharacteristic(characteristic)
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
            BleMeshLog.d("GATT_SERVER_CONNECTION", "status" to status, "state" to newState)
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

            val queued = gattInboundQueue.trySend(
                GattInboundWrite(
                    device = device,
                    requestId = requestId,
                    responseNeeded = responseNeeded,
                    value = value.copyOf(),
                    decoded = decoded
                )
            ).isSuccess

            if (!queued) {
                BleMeshLog.w(
                    "GATT_RX_BACKPRESSURE",
                    "bytes" to value.size,
                    "queueCapacity" to MAX_GATT_INBOUND_QUEUE
                )
                if (responseNeeded) {
                    sendGattServerResponse(device, requestId, BluetoothGatt.GATT_FAILURE)
                }
            }
        }
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
    @Synchronized
    private fun stopGattServer() {
        val server = gattServer
        gattServer = null
        runCatching { server?.clearServices() }
        runCatching { server?.close() }
        _meshState.update { it.copy(isGattServerRunning = false) }
    }

    /** Scanning never pauses for sending in the GATT transport. */
    @SuppressLint("MissingPermission")
    @Synchronized
    fun startScanning() {
        if (com.travelingsalesman.agap.BuildConfig.OFFLINE_TEST) return
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
        if (!scanStartGate.tryStart()) return

        try {
            val settings = ScanSettings.Builder()
                .setScanMode(ScanSettings.SCAN_MODE_BALANCED)
                .setCallbackType(ScanSettings.CALLBACK_TYPE_ALL_MATCHES)
                .build()

            val scanFilter = ScanFilter.Builder()
                .setServiceData(serviceParcelUuid, null)
                .build()

            try {
                scanner.startScan(listOf(scanFilter), settings, scanCallback)
            } catch (_: Exception) {
                // Fallback to unfiltered scan if device vendor driver rejects service-data filter
                scanner.startScan(null, settings, scanCallback)
            }
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
            BleMeshLog.i("SCAN_STARTED", "mode" to "BALANCED", "payload" to "PRESENCE_FILTERED")
        } catch (e: SecurityException) {
            scanStartGate.stopped()
            _meshState.update {
                it.copy(
                    isScanning = false,
                    needsPermissionsPrompt = true,
                    lastError = "Nearby-device permission is required to scan for relay peers."
                )
            }
            BleMeshLog.e("SCAN_PERMISSION_FAILURE", error = e)
        } catch (e: Exception) {
            scanStartGate.stopped()
            _meshState.update {
                it.copy(
                    isScanning = false,
                    lastError = "AGAP could not start peer scanning. Check Bluetooth and try again."
                )
            }
            BleMeshLog.e("SCAN_START_FAILED", error = e)
        }
    }

    @SuppressLint("MissingPermission")
    @Synchronized
    fun stopScanning() {
        val scanner = bluetoothLeScanner
        runCatching {
            if (scanner != null && hasBluetoothScanPermission()) scanner.stopScan(scanCallback)
        }
        scanStartedAtMs = 0L
        lastAnyScanCallbackMs = 0L
        scanStartGate.stopped()
        _meshState.update { it.copy(isScanning = false) }
    }

    @SuppressLint("MissingPermission")
    @Synchronized
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
        if (!advertiseStartGate.tryStart()) return

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
            advertiseStartGate.stopped()
            _meshState.update { it.copy(isAdvertising = false) }
            BleMeshLog.e("PRESENCE_ADVERTISE_START_FAILED", error = e)
        }
    }

    @SuppressLint("MissingPermission")
    @Synchronized
    private fun stopPresenceAdvertising() {
        val advertiser = bluetoothLeAdvertiser
        runCatching {
            if (advertiser != null && hasBluetoothAdvertisePermission()) {
                advertiser.stopAdvertising(presenceAdvertiseCallback)
            }
        }
        advertiseStartGate.stopped()
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
            advertiseStartGate.stopped()
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
            if (errorCode == ScanCallback.SCAN_FAILED_ALREADY_STARTED) {
                // Android confirms this app's scan already exists; keep it alive
                // instead of creating a stop/retry loop for a healthy scanner.
                scanStartGate.markRunning()
                _meshState.update { it.copy(isScanning = true, lastError = null) }
                BleMeshLog.i("SCAN_ALREADY_RUNNING", "code" to errorCode)
                return
            }
            scanStartGate.stopped()
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
        // Every beacon refreshes liveness/RSSI, but repeated advertisements do
        // not change membership. The existing mesh pulse handles stale peers.
        if (previous == null) publishNearbyDeviceCount()

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
        if (com.travelingsalesman.agap.BuildConfig.OFFLINE_TEST) return true
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
                longitude = packet.longitude,
                originDeviceId = packet.originDeviceId
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

    fun startAdvertisingAdminAlert(alert: AdminAlertEntity) {
        scope.launch {
            val token = tokenBytesFromIdentifier(alert.uuid)
            
            val categoryCode = when (alert.category.uppercase()) {
                "MEDICAL" -> 1
                "ACCIDENT" -> 2
                "FIRE" -> 3
                "DISASTER" -> 4
                "CRIME" -> 5
                else -> 0
            }
            
            val severityCode = when (alert.severity.uppercase()) {
                "INFO" -> 0
                "WARNING" -> 1
                "CRITICAL" -> 2
                "EMERGENCY" -> 3
                else -> 0
            }

            val bytes = BleMeshPacketCodec.encodeEmergencyPacket(
                packetType = BleMeshPacketType.ANNOUNCEMENT,
                emergencyType = "ANNOUNCEMENT",
                immediateDanger = severityCode > 1,
                subjectRelation = "SELF",
                hopCount = 0,
                locationSource = LocationSource.fromCode(severityCode and 0x03),
                transportToken = token,
                categoryCode = categoryCode,
                severityCode = severityCode
            )
            val decoded = BleMeshPacketCodec.decode(bytes) ?: return@launch
            val queueId = relayScheduler.enqueueForRelay(decoded, bytes, isOriginLocal = true)
            BleMeshLog.i(
                "ADMIN_ALERT_GATT_QUEUED",
                "queueId" to queueId,
                "token" to BleMeshLog.token(decoded.transportTokenHex),
                "category" to alert.category,
                "severity" to alert.severity
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

    /**
     * Queue a server-defined fixed incident reply through the shared relay path.
     * Nearby P2P_CLUSTER gets first opportunity; native GATT remains fallback.
     */
    fun queuePredefinedResponse(
        incidentIdentifier: String,
        messageType: String,
        protocolCode: Int
    ): Boolean {
        if (incidentIdentifier.isBlank() || protocolCode !in 1..255) {
            BleMeshLog.w(
                "PREDEFINED_RESPONSE_REJECTED",
                "protocolCode" to protocolCode,
                "hasIncidentToken" to incidentIdentifier.isNotBlank()
            )
            return false
        }
        val token = tokenBytesFromIdentifier(incidentIdentifier)
        if (messageType.equals("ACK", ignoreCase = true)) {
            startAdvertisingAcknowledgmentByToken(token, protocolCode)
        } else {
            startAdvertisingStatusByToken(token, protocolCode)
        }
        return true
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

    private fun pruneStaleObservations(now: Long = System.currentTimeMillis()) {
        val before = discoveredPeers.size
        val removed = discoveredPeers.entries.removeAll { now - it.value.lastSeenMs > PEER_STALE_MS }
        if (removed) {
            publishNearbyDeviceCount()
            BleMeshLog.d("PEER_SET_CHANGED", "before" to before, "after" to discoveredPeers.size)
        }
    }

    private fun publishNearbyDeviceCount() {
        _meshState.update { state ->
            val nearby = nearbyMeshManager.state.value
            val nearbyIds = nearby.discoveredPeerIds + nearby.connectedPeerIds
            state.copy(nearbyDeviceCount = (discoveredPeers.keys.toSet() + nearbyIds).size)
        }
    }

    private fun pruneAttemptHistory(now: Long = System.currentTimeMillis()) {
        peerAttemptTimes.entries.removeAll { now - it.value > 5 * 60_000L }
    }

    private fun attemptKey(queueId: Long, peerIdHex: String): String = "$queueId:$peerIdHex"

    /** Preserve protocol token if already 12 hex; otherwise derive a stable 6-byte token. */
    private fun tokenBytesFromIdentifier(identifier: String): ByteArray {
        val clean = identifier.trim().lowercase()
        if (clean.matches(Regex("^[0-9a-f]{12}$"))) {
            return clean.meshHexToBytesOrNull()!!
        }
        return MessageDigest.getInstance("SHA-256")
            .digest(identifier.toByteArray(Charsets.UTF_8))
            .copyOfRange(0, 6)
    }

    private fun String.hexToBytesOrNull(): ByteArray? = meshHexToBytesOrNull()

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
