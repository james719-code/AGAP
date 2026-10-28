package com.travelingsalesman.agap.core.bluetooth

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat
import com.google.android.gms.common.ConnectionResult
import com.google.android.gms.common.GoogleApiAvailability
import com.google.android.gms.nearby.Nearby
import com.google.android.gms.nearby.connection.AdvertisingOptions
import com.google.android.gms.nearby.connection.ConnectionInfo
import com.google.android.gms.nearby.connection.ConnectionLifecycleCallback
import com.google.android.gms.nearby.connection.ConnectionResolution
import com.google.android.gms.nearby.connection.ConnectionsClient
import com.google.android.gms.nearby.connection.ConnectionsStatusCodes
import com.google.android.gms.nearby.connection.DiscoveredEndpointInfo
import com.google.android.gms.nearby.connection.DiscoveryOptions
import com.google.android.gms.nearby.connection.EndpointDiscoveryCallback
import com.google.android.gms.nearby.connection.Payload
import com.google.android.gms.nearby.connection.PayloadCallback
import com.google.android.gms.nearby.connection.PayloadTransferUpdate
import com.google.android.gms.nearby.connection.Strategy
import com.travelingsalesman.agap.data.local.AuthTokenManager
import com.travelingsalesman.agap.data.local.dao.AgencyDao
import com.travelingsalesman.agap.data.local.dao.RelayDao
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import java.security.SecureRandom
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean
import javax.inject.Inject
import javax.inject.Singleton

/** Runtime state for Google's Nearby Connections transport. */
data class NearbyMeshState(
    val isPlayServicesAvailable: Boolean = false,
    val hasPermissions: Boolean = false,
    val isAdvertising: Boolean = false,
    val isDiscovering: Boolean = false,
    val discoveredPeerIds: Set<String> = emptySet(),
    val connectedPeerIds: Set<String> = emptySet(),
    val isTransferring: Boolean = false,
    val activePeerNodeId: String? = null,
    val transferStage: String = "IDLE",
    val transferProgress: Float = 0f,
    val lastError: String? = null
) {
    val isHealthy: Boolean
        get() = isPlayServicesAvailable && hasPermissions &&
            (isAdvertising || isDiscovering || connectedPeerIds.isNotEmpty())
}

internal data class NearbySendResult(
    val queueId: Long,
    val peerNodeId: String,
    val success: Boolean,
    val reason: String? = null
)

/**
 * Preferred AGAP transport on devices with healthy Google Play services.
 *
 * This class NEVER removes relay_queue rows. BleMeshManager remains the single
 * queue owner/arbiter so Nearby and raw GATT cannot race to complete one message.
 */
@Singleton
class NearbyMeshManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val authTokenManager: AuthTokenManager,
    private val agencyDao: AgencyDao,
    private val packetProcessor: BlePacketProcessor,
    private val relayDao: RelayDao
) {
    companion object {
        private const val SERVICE_ID = "com.travelingsalesman.agap.relay.v1"
        private val STRATEGY: Strategy = Strategy.P2P_CLUSTER
        private const val ENDPOINT_PREFIX = "AGAP-"
        private const val ENDPOINT_SUFFIX = "-v2"
        private const val MAX_CONNECTED_PEERS = 4
        private const val START_RETRY_MS = 15_000L
        private const val DELIVERY_ACK_TIMEOUT_MS = 10_000L
        private const val FAILED_PEER_COOLDOWN_MS = 5_000L
        private const val MAX_INBOUND_QUEUE = 64
    }

    private data class Peer(
        val endpointId: String,
        val nodeId: String,
        val endpointName: String,
        val connectedAtMs: Long = System.currentTimeMillis(),
        val lastSendAtMs: Long = 0L
    )

    private data class PendingSend(
        val transferId: Long,
        val queueId: Long,
        val endpointId: String,
        val peerNodeId: String,
        val callback: (NearbySendResult) -> Unit,
        val timeoutJob: Job,
        val startedAtMs: Long = System.nanoTime() / 1_000_000
    )

    private data class InboundData(
        val endpointId: String,
        val frame: NearbyMeshFrameCodec.Frame.Data,
        val queuedAtMs: Long = System.nanoTime() / 1_000_000
    )

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val localNodeId = AgapRelayIdentity.getOrCreate(context)
    private val localEndpointName = "$ENDPOINT_PREFIX$localNodeId$ENDPOINT_SUFFIX"
    private val secureRandom = SecureRandom()
    private val startRequested = AtomicBoolean(false)
    private val connectingEndpointIds = ConcurrentHashMap.newKeySet<String>()
    private val discovered = ConcurrentHashMap<String, Peer>()
    private val connected = ConcurrentHashMap<String, Peer>()
    private val initiatedPeerByEndpoint = ConcurrentHashMap<String, Peer>()
    private val pendingSends = ConcurrentHashMap<Long, PendingSend>()
    private val peerCooldownUntil = ConcurrentHashMap<String, Long>()
    private val peerDeferredJobs = ConcurrentHashMap<String, Job>()
    private val inboundDataQueue = Channel<InboundData>(capacity = MAX_INBOUND_QUEUE)

    @Volatile private var transferStage: String = "IDLE"
    @Volatile private var transferProgress: Float = 0f
    @Volatile private var lastAdvertisingAttemptMs = 0L
    @Volatile private var lastDiscoveryAttemptMs = 0L
    @Volatile private var cachedUserRole: UserRole = UserRole.CITIZEN
    @Volatile private var cachedAgencyType: String? = null

    private val _state = MutableStateFlow(
        NearbyMeshState(
            isPlayServicesAvailable = playServicesAvailable(),
            hasPermissions = hasRequiredPermissions()
        )
    )
    val state: StateFlow<NearbyMeshState> = _state.asStateFlow()

    /** Called by the shared relay owner when new inbound data created relay work. */
    @Volatile
    var onRelayWorkAvailable: (() -> Unit)? = null

    init {
        // PacketProcessor already serializes durable receive acceptance. Feed it
        // through one bounded consumer instead of launching one coroutine per
        // Nearby callback; when saturated we NACK so the sender can retry later.
        scope.launch {
            for (inbound in inboundDataQueue) {
                processInboundDataNow(inbound.endpointId, inbound.frame, inbound.queuedAtMs)
            }
        }
        scope.launch {
            authTokenManager.userRole
                .distinctUntilChanged()
                .collect { role -> cachedUserRole = UserRole.fromString(role) }
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

    private val client: ConnectionsClient by lazy(LazyThreadSafetyMode.SYNCHRONIZED) {
        Nearby.getConnectionsClient(context.applicationContext)
    }

    fun ensureStarted(): Boolean {
        if (com.travelingsalesman.agap.BuildConfig.OFFLINE_TEST) return false
        val playServicesOk = playServicesAvailable()
        val permissionsOk = hasRequiredPermissions()
        _state.update {
            it.copy(
                isPlayServicesAvailable = playServicesOk,
                hasPermissions = permissionsOk,
                lastError = when {
                    !playServicesOk -> "Nearby unavailable; native BLE fallback remains active."
                    !permissionsOk -> "Nearby permissions missing; native BLE fallback remains active."
                    else -> it.lastError
                }
            )
        }
        if (!playServicesOk || !permissionsOk) return false

        startRequested.set(true)
        maybeStartAdvertising()
        maybeStartDiscovery()
        return true
    }

    fun stop() {
        startRequested.set(false)
        runCatching { client.stopAdvertising() }
        runCatching { client.stopDiscovery() }
        runCatching { client.stopAllEndpoints() }

        transferStage = "IDLE"
        transferProgress = 0f
        pendingSends.values.toList().forEach { pending ->
            if (pendingSends.remove(pending.transferId, pending)) {
                pending.timeoutJob.cancel()
                safeCallback(
                    pending,
                    NearbySendResult(
                        queueId = pending.queueId,
                        peerNodeId = pending.peerNodeId,
                        success = false,
                        reason = "nearby transport stopped"
                    )
                )
            }
        }

        peerDeferredJobs.values.forEach { it.cancel() }
        peerDeferredJobs.clear()
        connectingEndpointIds.clear()
        discovered.clear()
        connected.clear()
        initiatedPeerByEndpoint.clear()
        peerCooldownUntil.clear()
        _state.update {
            it.copy(
                isAdvertising = false,
                isDiscovering = false,
                discoveredPeerIds = emptySet(),
                connectedPeerIds = emptySet(),
                isTransferring = false,
                activePeerNodeId = null,
                transferStage = "IDLE",
                transferProgress = 0f
            )
        }
        BleMeshLog.i("NEARBY_STOPPED")
    }

    internal fun hasConnectedPeer(excludedNodeIds: Set<String> = emptySet()): Boolean {
        val now = System.currentTimeMillis()
        return connected.values.any { peer ->
            isRelayPeerEligible(peer.nodeId, excludedNodeIds, peerCooldownUntil[peer.nodeId] ?: 0L, now)
        }
    }

    /**
     * Sends one queued AGAP packet to one connected Nearby peer.
     * Returns false without consuming queue ownership when no useful peer exists.
     */
    internal fun trySend(
        queueId: Long,
        payload: ByteArray,
        excludedNodeIds: Set<String> = emptySet(),
        callback: (NearbySendResult) -> Unit
    ): Boolean {
        if (!state.value.isHealthy) return false
        if (pendingSends.isNotEmpty()) return false // shared scheduler currently serializes one logical relay

        val now = System.currentTimeMillis()
        val candidate = connected.values
            .asSequence()
            .filter { isRelayPeerEligible(it.nodeId, excludedNodeIds, peerCooldownUntil[it.nodeId] ?: 0L, now) }
            .minByOrNull { it.lastSendAtMs }
            ?: return false

        val transferId = nextTransferId()
        val frameBytes = NearbyMeshFrameCodec.encodeData(transferId, payload)
        val timeoutJob = scope.launch {
            delay(DELIVERY_ACK_TIMEOUT_MS)
            failPending(transferId, "application ACK timeout")
        }
        val pending = PendingSend(
            transferId = transferId,
            queueId = queueId,
            endpointId = candidate.endpointId,
            peerNodeId = candidate.nodeId,
            callback = callback,
            timeoutJob = timeoutJob
        )
        pendingSends[transferId] = pending
        connected.computeIfPresent(candidate.endpointId) { _, old ->
            old.copy(lastSendAtMs = System.currentTimeMillis())
        }
        transferStage = "SENDING"
        transferProgress = 0.55f
        updateState()

        BleMeshLog.i(
            "NEARBY_TX_START",
            "queueId" to queueId,
            "transferId" to transferId,
            "peer" to BleMeshLog.token(candidate.nodeId),
            "bytes" to payload.size
        )

        return try {
            client.sendPayload(candidate.endpointId, Payload.fromBytes(frameBytes))
                .addOnSuccessListener {
                    transferStage = "AWAITING_ACK"
                    transferProgress = 0.82f
                    updateState()
                }
                .addOnFailureListener { error ->
                    failPending(transferId, "sendPayload failed: ${error.message ?: error.javaClass.simpleName}")
                }
            true
        } catch (error: Throwable) {
            // The shared scheduler still owns the queue because Nearby never
            // successfully started this send. Clean our local reservation and
            // return false so the caller can fall through to native GATT now.
            pendingSends.remove(transferId)?.timeoutJob?.cancel()
            transferStage = "IDLE"
            transferProgress = 0f
            updateState()
            BleMeshLog.w(
                "NEARBY_TX_START_EXCEPTION",
                "queueId" to queueId,
                "error" to (error.message ?: error.javaClass.simpleName)
            )
            false
        }
    }

    private fun maybeStartAdvertising() {
        if (!startRequested.get() || _state.value.isAdvertising) return
        val now = System.currentTimeMillis()
        if (now - lastAdvertisingAttemptMs < START_RETRY_MS) return
        lastAdvertisingAttemptMs = now

        val options = AdvertisingOptions.Builder().setStrategy(STRATEGY).build()
        try {
            client.startAdvertising(localEndpointName, SERVICE_ID, connectionLifecycleCallback, options)
                .addOnSuccessListener {
                    _state.update { it.copy(isAdvertising = true, lastError = null) }
                    BleMeshLog.i("NEARBY_ADVERTISING_STARTED", "node" to BleMeshLog.token(localNodeId))
                }
                .addOnFailureListener { error ->
                    _state.update {
                        it.copy(
                            isAdvertising = false,
                            lastError = "Nearby advertising failed; BLE fallback active."
                        )
                    }
                    BleMeshLog.w("NEARBY_ADVERTISING_FAILED", "error" to (error.message ?: error.javaClass.simpleName))
                }
        } catch (error: Throwable) {
            _state.update { it.copy(isAdvertising = false, lastError = "Nearby advertising unavailable; BLE fallback active.") }
            BleMeshLog.w("NEARBY_ADVERTISING_EXCEPTION", "error" to (error.message ?: error.javaClass.simpleName))
        }
    }

    private fun maybeStartDiscovery() {
        if (!startRequested.get() || _state.value.isDiscovering) return
        val now = System.currentTimeMillis()
        if (now - lastDiscoveryAttemptMs < START_RETRY_MS) return
        lastDiscoveryAttemptMs = now

        val options = DiscoveryOptions.Builder().setStrategy(STRATEGY).build()
        try {
            client.startDiscovery(SERVICE_ID, endpointDiscoveryCallback, options)
                .addOnSuccessListener {
                    _state.update { it.copy(isDiscovering = true, lastError = null) }
                    BleMeshLog.i("NEARBY_DISCOVERY_STARTED")
                }
                .addOnFailureListener { error ->
                    _state.update {
                        it.copy(
                            isDiscovering = false,
                            lastError = "Nearby discovery failed; BLE fallback active."
                        )
                    }
                    BleMeshLog.w("NEARBY_DISCOVERY_FAILED", "error" to (error.message ?: error.javaClass.simpleName))
                }
        } catch (error: Throwable) {
            _state.update { it.copy(isDiscovering = false, lastError = "Nearby discovery unavailable; BLE fallback active.") }
            BleMeshLog.w("NEARBY_DISCOVERY_EXCEPTION", "error" to (error.message ?: error.javaClass.simpleName))
        }
    }

    private val endpointDiscoveryCallback = object : EndpointDiscoveryCallback() {
        override fun onEndpointFound(endpointId: String, info: DiscoveredEndpointInfo) {
            val nodeId = parseNodeId(info.endpointName) ?: return
            if (nodeId == localNodeId) return
            val peer = Peer(endpointId = endpointId, nodeId = nodeId, endpointName = info.endpointName)
            discovered[endpointId] = peer
            updateState()
            BleMeshLog.i("NEARBY_PEER_FOUND", "peer" to BleMeshLog.token(nodeId))

            // Deterministic initiator prevents both phones from racing immediately.
            // The higher node schedules a delayed fallback so asymmetric discovery
            // cannot leave two visible phones permanently unconnected.
            if (localNodeId < nodeId) {
                requestConnectionIfUseful(peer)
            } else {
                scheduleConnectionFallback(peer)
            }
            onRelayWorkAvailable?.invoke()
        }

        override fun onEndpointLost(endpointId: String) {
            peerDeferredJobs.remove(endpointId)?.cancel()
            discovered.remove(endpointId)
            if (!connected.containsKey(endpointId)) initiatedPeerByEndpoint.remove(endpointId)
            updateState()
            BleMeshLog.d("NEARBY_PEER_LOST", "endpoint" to endpointId.take(8))
        }
    }

    private fun scheduleConnectionFallback(peer: Peer) {
        schedulePeerDeferred(peer, delayMs = 1_500L)
    }

    private fun requestConnectionIfUseful(peer: Peer) {
        if (connected.containsKey(peer.endpointId)) return
        if (connected.size + connectingEndpointIds.size >= MAX_CONNECTED_PEERS) return
        if (!connectingEndpointIds.add(peer.endpointId)) return
        initiatedPeerByEndpoint[peer.endpointId] = peer

        try {
            client.requestConnection(localEndpointName, peer.endpointId, connectionLifecycleCallback)
                .addOnFailureListener { error ->
                    connectingEndpointIds.remove(peer.endpointId)
                    initiatedPeerByEndpoint.remove(peer.endpointId)
                    BleMeshLog.w(
                        "NEARBY_CONNECT_REQUEST_FAILED",
                        "peer" to BleMeshLog.token(peer.nodeId),
                        "error" to (error.message ?: error.javaClass.simpleName)
                    )
                    scheduleReconnect(peer)
                }
        } catch (error: Throwable) {
            connectingEndpointIds.remove(peer.endpointId)
            initiatedPeerByEndpoint.remove(peer.endpointId)
            BleMeshLog.w("NEARBY_CONNECT_REQUEST_EXCEPTION", "error" to (error.message ?: error.javaClass.simpleName))
            scheduleReconnect(peer)
        }
    }

    private val connectionLifecycleCallback = object : ConnectionLifecycleCallback() {
        override fun onConnectionInitiated(endpointId: String, info: ConnectionInfo) {
            val nodeId = parseNodeId(info.endpointName)
            val alreadyConnecting = connectingEndpointIds.contains(endpointId)
            val capacityUsed = connected.size + connectingEndpointIds.size
            if (
                nodeId == null ||
                nodeId == localNodeId ||
                (!alreadyConnecting && capacityUsed >= MAX_CONNECTED_PEERS)
            ) {
                runCatching { client.rejectConnection(endpointId) }
                return
            }

            val peer = Peer(endpointId = endpointId, nodeId = nodeId, endpointName = info.endpointName)
            initiatedPeerByEndpoint[endpointId] = peer
            connectingEndpointIds.add(endpointId)

            // AGAP's emergency relay is intentionally zero-touch. The application
            // protocol still validates every payload before persistence. Nearby's
            // human-verification authentication digits are therefore not shown here.
            try {
                client.acceptConnection(endpointId, payloadCallback)
                    .addOnFailureListener { error ->
                        connectingEndpointIds.remove(endpointId)
                        initiatedPeerByEndpoint.remove(endpointId)
                        BleMeshLog.w("NEARBY_ACCEPT_FAILED", "error" to (error.message ?: error.javaClass.simpleName))
                    }
            } catch (error: Throwable) {
                connectingEndpointIds.remove(endpointId)
                initiatedPeerByEndpoint.remove(endpointId)
                BleMeshLog.w("NEARBY_ACCEPT_EXCEPTION", "error" to (error.message ?: error.javaClass.simpleName))
            }
        }

        override fun onConnectionResult(endpointId: String, resolution: ConnectionResolution) {
            connectingEndpointIds.remove(endpointId)
            val peer = initiatedPeerByEndpoint[endpointId] ?: discovered[endpointId]
            if (resolution.status.statusCode == ConnectionsStatusCodes.STATUS_OK && peer != null) {
                peerDeferredJobs.remove(endpointId)?.cancel()
                connected[endpointId] = peer.copy(connectedAtMs = System.currentTimeMillis())
                BleMeshLog.i("NEARBY_CONNECTED", "peer" to BleMeshLog.token(peer.nodeId), "count" to connected.size)
                onRelayWorkAvailable?.invoke()
            } else {
                initiatedPeerByEndpoint.remove(endpointId)
                BleMeshLog.w(
                    "NEARBY_CONNECTION_RESULT",
                    "status" to resolution.status.statusCode,
                    "peer" to (peer?.nodeId?.let(BleMeshLog::token) ?: "unknown")
                )
                if (peer != null) scheduleReconnect(peer)
            }
            updateState()
        }

        override fun onDisconnected(endpointId: String) {
            val peer = connected.remove(endpointId) ?: initiatedPeerByEndpoint[endpointId]
            initiatedPeerByEndpoint.remove(endpointId)
            connectingEndpointIds.remove(endpointId)
            failPendingForEndpoint(endpointId, "endpoint disconnected")
            updateState()
            BleMeshLog.i("NEARBY_DISCONNECTED", "peer" to (peer?.nodeId?.let(BleMeshLog::token) ?: "unknown"))
            if (peer != null) scheduleReconnect(peer)
        }
    }

    private fun scheduleReconnect(peer: Peer) {
        if (!startRequested.get() || localNodeId >= peer.nodeId) return
        schedulePeerDeferred(peer, delayMs = 2_000L)
    }

    private fun schedulePeerDeferred(peer: Peer, delayMs: Long) {
        peerDeferredJobs.remove(peer.endpointId)?.cancel()
        val job = scope.launch {
            try {
                delay(delayMs)
                if (!startRequested.get()) return@launch
                val current = discovered[peer.endpointId] ?: return@launch
                if (!connected.containsKey(peer.endpointId) &&
                    !connectingEndpointIds.contains(peer.endpointId)) {
                    requestConnectionIfUseful(current)
                }
            } finally {
                peerDeferredJobs.remove(peer.endpointId)
            }
        }
        peerDeferredJobs[peer.endpointId] = job
    }

    private val payloadCallback = object : PayloadCallback() {
        override fun onPayloadReceived(endpointId: String, payload: Payload) {
            if (payload.type != Payload.Type.BYTES) return
            val bytes = payload.asBytes() ?: return
            val frame = NearbyMeshFrameCodec.decode(bytes) ?: run {
                BleMeshLog.w("NEARBY_FRAME_REJECTED", "bytes" to bytes.size)
                return
            }
            when (frame) {
                is NearbyMeshFrameCodec.Frame.Data -> {
                    val queued = inboundDataQueue.trySend(
                        InboundData(endpointId = endpointId, frame = frame)
                    ).isSuccess
                    if (!queued) {
                        BleMeshLog.w(
                            "NEARBY_RX_BACKPRESSURE",
                            "endpoint" to endpointId.take(8),
                            "queueCapacity" to MAX_INBOUND_QUEUE
                        )
                        sendAck(endpointId, frame.transferId, false)
                    }
                }
                is NearbyMeshFrameCodec.Frame.Ack -> processInboundAck(endpointId, frame)
            }
        }

        override fun onPayloadTransferUpdate(endpointId: String, update: PayloadTransferUpdate) {
            // BYTES arrives atomically at onPayloadReceived. Application ACK, not
            // this transport callback, is the success condition for AGAP delivery.
            if (update.status == PayloadTransferUpdate.Status.FAILURE) {
                BleMeshLog.w("NEARBY_PAYLOAD_TRANSPORT_ERROR", "endpoint" to endpointId.take(8))
            }
        }
    }

    private suspend fun processInboundDataNow(
        endpointId: String,
        frame: NearbyMeshFrameCodec.Frame.Data,
        queuedAtMs: Long
    ) {
        val processingStartedAt = System.nanoTime() / 1_000_000
        val peer = connected[endpointId] ?: initiatedPeerByEndpoint[endpointId] ?: return
        val decoded = BleMeshPacketCodec.decode(frame.agapPayload)
        if (decoded == null) {
            sendAck(endpointId, frame.transferId, false)
            return
        }

        // Duplicate means the receiver already possesses the logical message;
        // ACKing it is correct and stops needless retransmission.
        val alreadySeen = relayDao.getSeenPacket(
            decoded.transportTokenHex,
            decoded.seenIdentity()
        ) != null
        val role = cachedUserRole
        val agencyType = cachedAgencyType

        val accepted = runCatching {
            packetProcessor.processIncomingPacket(
                data = frame.agapPayload,
                userRole = role,
                userAgencyType = agencyType,
                sourcePeerId = peer.nodeId
            )
        }.getOrNull()

        // Re-check after the serialized processor. A concurrent transport may
        // have persisted this token while this callback waited for the receive lock.
        val seenAfterProcessing = alreadySeen || relayDao.getSeenPacket(
            decoded.transportTokenHex,
            decoded.seenIdentity()
        ) != null
        val success = accepted != null || seenAfterProcessing
        sendAck(endpointId, frame.transferId, success)
        if (success) {
            BleMeshLog.i(
                "NEARBY_RX_ACCEPTED",
                "queueWaitMs" to (processingStartedAt - queuedAtMs).coerceAtLeast(0),
                "processingMs" to (System.nanoTime() / 1_000_000 - processingStartedAt).coerceAtLeast(0),
                "token" to BleMeshLog.token(decoded.transportTokenHex),
                "peer" to BleMeshLog.token(peer.nodeId),
                "duplicate" to (accepted == null && seenAfterProcessing)
            )
            onRelayWorkAvailable?.invoke()
        } else {
            BleMeshLog.w(
                "NEARBY_RX_REJECTED",
                "token" to BleMeshLog.token(decoded.transportTokenHex),
                "peer" to BleMeshLog.token(peer.nodeId)
            )
        }
    }

    private fun processInboundAck(endpointId: String, frame: NearbyMeshFrameCodec.Frame.Ack) {
        val pending = pendingSends[frame.transferId] ?: return
        if (pending.endpointId != endpointId) return
        if (!frame.accepted) {
            failPending(frame.transferId, "remote rejected AGAP payload")
            return
        }

        if (!pendingSends.remove(frame.transferId, pending)) return
        pending.timeoutJob.cancel()
        peerCooldownUntil.remove(pending.peerNodeId)
        transferStage = "DELIVERED"
        transferProgress = 1f
        updateState()
        BleMeshLog.i(
            "NEARBY_APP_ACK",
            "transferId" to pending.transferId,
            "elapsedMs" to (System.nanoTime() / 1_000_000 - pending.startedAtMs).coerceAtLeast(0),
            "queueId" to pending.queueId,
            "peer" to BleMeshLog.token(pending.peerNodeId)
        )
        safeCallback(
            pending,
            NearbySendResult(
                queueId = pending.queueId,
                peerNodeId = pending.peerNodeId,
                success = true
            )
        )
    }

    private fun sendAck(endpointId: String, transferId: Long, accepted: Boolean) {
        try {
            client.sendPayload(endpointId, Payload.fromBytes(NearbyMeshFrameCodec.encodeAck(transferId, accepted)))
                .addOnFailureListener { error ->
                    BleMeshLog.w("NEARBY_ACK_SEND_FAILED", "error" to (error.message ?: error.javaClass.simpleName))
                }
        } catch (error: Throwable) {
            BleMeshLog.w("NEARBY_ACK_SEND_EXCEPTION", "error" to (error.message ?: error.javaClass.simpleName))
        }
    }

    private fun failPending(transferId: Long, reason: String) {
        val pending = pendingSends.remove(transferId) ?: return
        pending.timeoutJob.cancel()
        peerCooldownUntil[pending.peerNodeId] = System.currentTimeMillis() + FAILED_PEER_COOLDOWN_MS
        transferStage = "FAILED"
        transferProgress = 0f
        updateState()
        BleMeshLog.w(
            "NEARBY_TX_FAILED",
            "transferId" to pending.transferId,
            "elapsedMs" to (System.nanoTime() / 1_000_000 - pending.startedAtMs).coerceAtLeast(0),
            "queueId" to pending.queueId,
            "peer" to BleMeshLog.token(pending.peerNodeId),
            "reason" to reason
        )
        safeCallback(
            pending,
            NearbySendResult(
                queueId = pending.queueId,
                peerNodeId = pending.peerNodeId,
                success = false,
                reason = reason
            )
        )
    }

    private fun failPendingForEndpoint(endpointId: String, reason: String) {
        pendingSends.values
            .filter { it.endpointId == endpointId }
            .forEach { failPending(it.transferId, reason) }
    }

    private fun safeCallback(pending: PendingSend, result: NearbySendResult) {
        runCatching { pending.callback(result) }
            .onFailure { BleMeshLog.e("NEARBY_RESULT_CALLBACK_FAILED", error = it) }
    }

    private fun updateState() {
        val activePending = pendingSends.values.firstOrNull()
        _state.update {
            it.copy(
                discoveredPeerIds = discovered.values.mapTo(linkedSetOf()) { peer -> peer.nodeId },
                connectedPeerIds = connected.values.mapTo(linkedSetOf()) { peer -> peer.nodeId },
                isTransferring = activePending != null,
                activePeerNodeId = activePending?.peerNodeId,
                transferStage = transferStage,
                transferProgress = transferProgress
            )
        }
    }

    private fun nextTransferId(): Long {
        var id: Long
        do {
            id = secureRandom.nextLong()
        } while (id == 0L || pendingSends.containsKey(id))
        return id
    }

    private fun parseNodeId(endpointName: String): String? {
        if (!endpointName.startsWith(ENDPOINT_PREFIX) || !endpointName.endsWith(ENDPOINT_SUFFIX)) return null
        val id = endpointName
            .removePrefix(ENDPOINT_PREFIX)
            .removeSuffix(ENDPOINT_SUFFIX)
            .lowercase()
        return id.takeIf { it.matches(Regex("^[0-9a-f]{12}$")) }
    }

    private fun playServicesAvailable(): Boolean = runCatching {
        GoogleApiAvailability.getInstance().isGooglePlayServicesAvailable(context) == ConnectionResult.SUCCESS
    }.getOrDefault(false)

    private fun hasRequiredPermissions(): Boolean {
        fun granted(permission: String): Boolean =
            ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED

        return when {
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU ->
                granted(Manifest.permission.BLUETOOTH_SCAN) &&
                    granted(Manifest.permission.BLUETOOTH_CONNECT) &&
                    granted(Manifest.permission.BLUETOOTH_ADVERTISE) &&
                    granted(Manifest.permission.NEARBY_WIFI_DEVICES)

            Build.VERSION.SDK_INT >= Build.VERSION_CODES.S ->
                granted(Manifest.permission.BLUETOOTH_SCAN) &&
                    granted(Manifest.permission.BLUETOOTH_CONNECT) &&
                    granted(Manifest.permission.BLUETOOTH_ADVERTISE) &&
                    granted(Manifest.permission.ACCESS_FINE_LOCATION)

            Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q ->
                granted(Manifest.permission.ACCESS_FINE_LOCATION)

            else -> granted(Manifest.permission.ACCESS_COARSE_LOCATION)
        }
    }
}
