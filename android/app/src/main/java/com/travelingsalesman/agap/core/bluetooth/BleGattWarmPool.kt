package com.travelingsalesman.agap.core.bluetooth

import android.annotation.SuppressLint
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattDescriptor
import android.bluetooth.BluetoothProfile
import android.bluetooth.BluetoothStatusCodes
import android.content.Context
import android.os.Build
import com.travelingsalesman.agap.data.local.entity.RelayQueueEntity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Keeps a very small set of nearby AGAP peers already connected and ready.
 *
 * The emergency send path therefore does not pay connectGatt + MTU + service
 * discovery for every SOS. Discovery/advertising remain owned by BleMeshManager
 * and run continuously in parallel with these links.
 */
internal class BleGattWarmPool(
    private val context: Context,
    private val canConnect: () -> Boolean,
    private val serviceUuid: UUID,
    private val packetCharacteristicUuid: UUID,
    private val ackCharacteristicUuid: UUID,
    private val cccdUuid: UUID,
    private val onReadyCountChanged: (Int) -> Unit,
    private val onPacketAcknowledged: (RelayQueueEntity, String) -> Unit,
    private val onPacketFailed: (RelayQueueEntity, String, String) -> Unit
) {
    companion object {
        const val MAX_WARM_GATT_PEERS = 3
        private const val REQUESTED_MTU = 517
        private const val SETUP_TIMEOUT_MS = 10_000L
        private const val TRANSPORT_ACK_TIMEOUT_MS = 2_500L
        private const val CONNECT_RETRY_COOLDOWN_MS = 2_000L
        private const val WARM_LINK_STALE_MS = 45_000L
    }

    internal data class Candidate(
        val peerIdHex: String,
        val device: BluetoothDevice,
        val rssi: Int,
        val lastSeenMs: Long
    )

    private data class ActiveWrite(
        val queue: RelayQueueEntity,
        val expectedTokenHex: String,
        val startedAtMs: Long
    )

    private class PeerLink(
        val peerIdHex: String,
        val device: BluetoothDevice,
        val gatt: BluetoothGatt,
        rssi: Int,
        lastSeenMs: Long
    ) {
        @Volatile var rssi: Int = rssi
        @Volatile var lastSeenMs: Long = lastSeenMs
        @Volatile var ready: Boolean = false
        @Volatile var setupStage: String = "CONNECTING"
        @Volatile var packetCharacteristic: BluetoothGattCharacteristic? = null
        @Volatile var ackCharacteristic: BluetoothGattCharacteristic? = null
        @Volatile var activeWrite: ActiveWrite? = null
        @Volatile var setupTimeoutJob: Job? = null
        @Volatile var ackTimeoutJob: Job? = null
        val busy = AtomicBoolean(false)
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val candidates = ConcurrentHashMap<String, Candidate>()
    private val links = ConcurrentHashMap<String, PeerLink>()
    private val pendingPeerByDevice = ConcurrentHashMap<BluetoothDevice, Candidate>()
    private val recentConnectAttempts = ConcurrentHashMap<String, Long>()
    private val maintenanceRunning = AtomicBoolean(false)

    fun updateCandidate(candidate: Candidate) {
        candidates[candidate.peerIdHex] = candidate
        links[candidate.peerIdHex]?.let { link ->
            link.rssi = candidate.rssi
            link.lastSeenMs = candidate.lastSeenMs
        }
    }

    fun removeCandidate(peerIdHex: String) {
        candidates.remove(peerIdHex)
    }

    fun readyPeerIds(): Set<String> = links.values
        .asSequence()
        .filter { it.ready }
        .map { it.peerIdHex }
        .toSet()

    fun readyPeerCount(): Int = links.values.count { it.ready }

    /**
     * Called frequently and intentionally cheap. It only opens connections for
     * missing slots and closes links that have been silent for a long time.
     */
    fun maintainConnections(now: Long = System.currentTimeMillis()) {
        if (!canConnect()) return
        if (!maintenanceRunning.compareAndSet(false, true)) return

        try {
            links.values
                .filter { now - it.lastSeenMs > WARM_LINK_STALE_MS }
                .forEach { closeLink(it, "stale") }

            val occupiedPeerIds = HashSet<String>().apply {
                addAll(links.keys)
                addAll(pendingPeerByDevice.values.map { it.peerIdHex })
            }
            var slots = MAX_WARM_GATT_PEERS - occupiedPeerIds.size
            if (slots <= 0) return

            val toConnect = candidates.values
                .asSequence()
                .filter { now - it.lastSeenMs <= WARM_LINK_STALE_MS }
                .filter { it.peerIdHex !in occupiedPeerIds }
                .filter { now - (recentConnectAttempts[it.peerIdHex] ?: 0L) >= CONNECT_RETRY_COOLDOWN_MS }
                .sortedByDescending { it.rssi }
                .take(slots)
                .toList()

            toConnect.forEach { candidate ->
                slots -= 1
                connect(candidate, now)
            }
        } finally {
            maintenanceRunning.set(false)
        }
    }

    /**
     * Starts a packet write immediately on the strongest ready warm peer.
     * Returns the selected peer ID, or null if no warm peer is ready yet.
     */
    @SuppressLint("MissingPermission")
    fun trySend(
        queue: RelayQueueEntity,
        payload: ByteArray,
        excludedPeerIds: Set<String> = emptySet(),
        onStarted: (String) -> Unit = {}
    ): String? {
        if (!canConnect()) return null

        val link = links.values
            .asSequence()
            .filter { it.ready && it.peerIdHex !in excludedPeerIds }
            .sortedByDescending { it.rssi }
            .firstOrNull { it.busy.compareAndSet(false, true) }
            ?: return null

        val characteristic = link.packetCharacteristic
        if (characteristic == null) {
            link.busy.set(false)
            closeLink(link, "packet characteristic lost")
            return null
        }

        val transfer = ActiveWrite(
            queue = queue,
            expectedTokenHex = queue.transportToken.lowercase(),
            startedAtMs = System.currentTimeMillis()
        )
        link.activeWrite = transfer
        onStarted(link.peerIdHex)
        link.ackTimeoutJob?.cancel()
        link.ackTimeoutJob = scope.launch {
            delay(TRANSPORT_ACK_TIMEOUT_MS)
            val current = link.activeWrite
            if (current === transfer) {
                failWrite(link, "transport ACK timeout", invalidateLink = true)
            }
        }

        val started = try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                link.gatt.writeCharacteristic(
                    characteristic,
                    payload,
                    BluetoothGattCharacteristic.WRITE_TYPE_NO_RESPONSE
                ) == BluetoothStatusCodes.SUCCESS
            } else {
                @Suppress("DEPRECATION")
                run {
                    characteristic.writeType = BluetoothGattCharacteristic.WRITE_TYPE_NO_RESPONSE
                    characteristic.value = payload
                    link.gatt.writeCharacteristic(characteristic)
                }
            }
        } catch (e: Exception) {
            BleMeshLog.w(
                "WARM_GATT_WRITE_EXCEPTION",
                "peer" to BleMeshLog.token(link.peerIdHex),
                "token" to BleMeshLog.token(queue.transportToken),
                error = e
            )
            false
        }

        if (!started) {
            failWrite(link, "write-without-response could not start", invalidateLink = false)
            return null
        }

        BleMeshLog.i(
            "WARM_GATT_WRITE_STARTED",
            "peer" to BleMeshLog.token(link.peerIdHex),
            "queueId" to queue.queueId,
            "token" to BleMeshLog.token(queue.transportToken),
            "bytes" to payload.size,
            "rssi" to link.rssi
        )
        return link.peerIdHex
    }

    @SuppressLint("MissingPermission")
    fun closeAll() {
        val snapshot = links.values.toList()
        links.clear()
        pendingPeerByDevice.clear()
        candidates.clear()
        recentConnectAttempts.clear()
        snapshot.forEach { link ->
            link.setupTimeoutJob?.cancel()
            link.ackTimeoutJob?.cancel()
            runCatching { link.gatt.disconnect() }
            runCatching { link.gatt.close() }
        }
        onReadyCountChanged(0)
    }

    @SuppressLint("MissingPermission")
    private fun connect(candidate: Candidate, now: Long) {
        if (!canConnect()) return
        if (links.containsKey(candidate.peerIdHex)) return
        if (pendingPeerByDevice.putIfAbsent(candidate.device, candidate) != null) return
        recentConnectAttempts[candidate.peerIdHex] = now

        BleMeshLog.d(
            "WARM_GATT_CONNECT_START",
            "peer" to BleMeshLog.token(candidate.peerIdHex),
            "rssi" to candidate.rssi
        )

        try {
            val gatt = candidate.device.connectGatt(
                context,
                false,
                callback,
                BluetoothDevice.TRANSPORT_LE
            )
            if (gatt == null) {
                pendingPeerByDevice.remove(candidate.device)
                BleMeshLog.w("WARM_GATT_CONNECT_NULL", "peer" to BleMeshLog.token(candidate.peerIdHex))
                return
            }

            // Some vendor stacks can invoke the callback before connectGatt()
            // returns. A successful early callback may already have attached this
            // exact object; a failed early callback removes the pending marker.
            val alreadyAttached = links[candidate.peerIdHex]
            if (alreadyAttached?.gatt === gatt) return
            if (pendingPeerByDevice[candidate.device]?.peerIdHex != candidate.peerIdHex) {
                runCatching { gatt.close() }
                return
            }
            attachLink(candidate, gatt)
        } catch (e: Exception) {
            pendingPeerByDevice.remove(candidate.device)
            BleMeshLog.w(
                "WARM_GATT_CONNECT_EXCEPTION",
                "peer" to BleMeshLog.token(candidate.peerIdHex),
                error = e
            )
        }
    }

    private fun attachLink(candidate: Candidate, gatt: BluetoothGatt): PeerLink {
        val created = PeerLink(
            peerIdHex = candidate.peerIdHex,
            device = candidate.device,
            gatt = gatt,
            rssi = candidate.rssi,
            lastSeenMs = candidate.lastSeenMs
        )
        val existing = links.putIfAbsent(candidate.peerIdHex, created)
        val link = existing ?: created
        pendingPeerByDevice.remove(candidate.device)

        if (existing != null && existing.gatt !== gatt) {
            runCatching { gatt.close() }
        } else if (existing == null) {
            created.setupTimeoutJob = scope.launch {
                delay(SETUP_TIMEOUT_MS)
                if (!created.ready && links[created.peerIdHex] === created) {
                    closeLink(created, "setup timeout")
                    maintainConnections()
                }
            }
        }
        return link
    }

    private fun findOrAttachLink(gatt: BluetoothGatt): PeerLink? {
        links.values.firstOrNull { it.gatt === gatt }?.let { return it }
        val candidate = pendingPeerByDevice[gatt.device] ?: return null
        return attachLink(candidate, gatt)
    }

    @SuppressLint("MissingPermission")
    private fun startFastSetup(link: PeerLink) {
        if (!canConnect()) return
        link.setupStage = "MTU"

        runCatching {
            link.gatt.requestConnectionPriority(BluetoothGatt.CONNECTION_PRIORITY_HIGH)
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            runCatching {
                link.gatt.setPreferredPhy(
                    BluetoothDevice.PHY_LE_2M_MASK,
                    BluetoothDevice.PHY_LE_2M_MASK,
                    BluetoothDevice.PHY_OPTION_NO_PREFERRED
                )
            }
        }

        val mtuStarted = runCatching { link.gatt.requestMtu(REQUESTED_MTU) }.getOrDefault(false)
        if (!mtuStarted) startServiceDiscovery(link)
    }

    @SuppressLint("MissingPermission")
    private fun startServiceDiscovery(link: PeerLink) {
        if (link.setupStage == "DISCOVERING" || link.ready) return
        link.setupStage = "DISCOVERING"
        if (!runCatching { link.gatt.discoverServices() }.getOrDefault(false)) {
            closeLink(link, "service discovery could not start")
            maintainConnections()
        }
    }

    @SuppressLint("MissingPermission")
    private fun enableTransportAckNotifications(link: PeerLink) {
        val ackCharacteristic = link.ackCharacteristic ?: run {
            closeLink(link, "ACK characteristic missing")
            return
        }
        val descriptor = ackCharacteristic.getDescriptor(cccdUuid) ?: run {
            closeLink(link, "ACK CCCD missing")
            return
        }

        if (!runCatching { link.gatt.setCharacteristicNotification(ackCharacteristic, true) }.getOrDefault(false)) {
            closeLink(link, "could not enable ACK notification locally")
            return
        }

        link.setupStage = "ENABLING_ACK"
        val started = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            runCatching {
                link.gatt.writeDescriptor(
                    descriptor,
                    BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
                ) == BluetoothStatusCodes.SUCCESS
            }.getOrDefault(false)
        } else {
            @Suppress("DEPRECATION")
            runCatching {
                descriptor.value = BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
                link.gatt.writeDescriptor(descriptor)
            }.getOrDefault(false)
        }

        if (!started) {
            closeLink(link, "ACK CCCD write could not start")
            maintainConnections()
        }
    }

    private fun handleTransportAck(link: PeerLink, raw: ByteArray) {
        val ack = BleGattTransportAckCodec.decode(raw) ?: return
        val transfer = link.activeWrite ?: return
        if (!ack.tokenHex.equals(transfer.expectedTokenHex, ignoreCase = true)) {
            BleMeshLog.d(
                "WARM_GATT_STALE_ACK",
                "peer" to BleMeshLog.token(link.peerIdHex),
                "expected" to BleMeshLog.token(transfer.expectedTokenHex),
                "actual" to BleMeshLog.token(ack.tokenHex)
            )
            return
        }

        link.ackTimeoutJob?.cancel()
        link.ackTimeoutJob = null
        link.activeWrite = null
        link.busy.set(false)

        if (ack.accepted) {
            BleMeshLog.i(
                "WARM_GATT_TRANSPORT_ACK",
                "peer" to BleMeshLog.token(link.peerIdHex),
                "queueId" to transfer.queue.queueId,
                "token" to BleMeshLog.token(transfer.queue.transportToken),
                "latencyMs" to (System.currentTimeMillis() - transfer.startedAtMs)
            )
            onPacketAcknowledged(transfer.queue, link.peerIdHex)
        } else {
            BleMeshLog.w(
                "WARM_GATT_TRANSPORT_REJECT",
                "peer" to BleMeshLog.token(link.peerIdHex),
                "queueId" to transfer.queue.queueId,
                "token" to BleMeshLog.token(transfer.queue.transportToken)
            )
            onPacketFailed(transfer.queue, link.peerIdHex, "remote transport reject")
        }
    }

    private fun failWrite(link: PeerLink, reason: String, invalidateLink: Boolean) {
        val transfer = link.activeWrite ?: return
        link.ackTimeoutJob?.cancel()
        link.ackTimeoutJob = null
        link.activeWrite = null
        link.busy.set(false)
        onPacketFailed(transfer.queue, link.peerIdHex, reason)
        if (invalidateLink) {
            closeLink(link, reason)
            maintainConnections()
        }
    }

    @SuppressLint("MissingPermission")
    private fun closeLink(link: PeerLink, reason: String) {
        if (!links.remove(link.peerIdHex, link)) return
        link.setupTimeoutJob?.cancel()
        link.ackTimeoutJob?.cancel()
        val active = link.activeWrite
        link.activeWrite = null
        link.busy.set(false)
        runCatching { link.gatt.disconnect() }
        runCatching { link.gatt.close() }
        if (active != null) {
            onPacketFailed(active.queue, link.peerIdHex, "link closed: $reason")
        }
        BleMeshLog.d(
            "WARM_GATT_CLOSED",
            "peer" to BleMeshLog.token(link.peerIdHex),
            "reason" to reason,
            "readyPeers" to readyPeerCount()
        )
        onReadyCountChanged(readyPeerCount())
    }

    @SuppressLint("MissingPermission")
    private val callback = object : BluetoothGattCallback() {
        override fun onConnectionStateChange(gatt: BluetoothGatt, status: Int, newState: Int) {
            if (status == BluetoothGatt.GATT_SUCCESS && newState == BluetoothProfile.STATE_CONNECTED) {
                val link = findOrAttachLink(gatt) ?: run {
                    runCatching { gatt.disconnect() }
                    runCatching { gatt.close() }
                    return
                }
                BleMeshLog.i(
                    "WARM_GATT_CONNECTED",
                    "peer" to BleMeshLog.token(link.peerIdHex),
                    "readyPeers" to readyPeerCount()
                )
                startFastSetup(link)
                return
            }

            if (newState == BluetoothProfile.STATE_DISCONNECTED || status != BluetoothGatt.GATT_SUCCESS) {
                val link = links.values.firstOrNull { it.gatt === gatt }
                if (link != null) {
                    closeLink(link, "connection status=$status state=$newState")
                } else {
                    pendingPeerByDevice.remove(gatt.device)
                    runCatching { gatt.close() }
                }
                maintainConnections()
            }
        }

        override fun onMtuChanged(gatt: BluetoothGatt, mtu: Int, status: Int) {
            val link = links.values.firstOrNull { it.gatt === gatt } ?: return
            BleMeshLog.d(
                "WARM_GATT_MTU",
                "peer" to BleMeshLog.token(link.peerIdHex),
                "mtu" to mtu,
                "status" to status
            )
            startServiceDiscovery(link)
        }

        override fun onServicesDiscovered(gatt: BluetoothGatt, status: Int) {
            val link = links.values.firstOrNull { it.gatt === gatt } ?: return
            if (status != BluetoothGatt.GATT_SUCCESS) {
                closeLink(link, "service discovery failed: $status")
                maintainConnections()
                return
            }

            val service = gatt.getService(serviceUuid)
            val packetCharacteristic = service?.getCharacteristic(packetCharacteristicUuid)
            val ackCharacteristic = service?.getCharacteristic(ackCharacteristicUuid)
            if (packetCharacteristic == null || ackCharacteristic == null) {
                closeLink(link, "AGAP fast GATT characteristics missing")
                maintainConnections()
                return
            }

            link.packetCharacteristic = packetCharacteristic
            link.ackCharacteristic = ackCharacteristic
            enableTransportAckNotifications(link)
        }

        override fun onDescriptorWrite(gatt: BluetoothGatt, descriptor: BluetoothGattDescriptor, status: Int) {
            val link = links.values.firstOrNull { it.gatt === gatt } ?: return
            if (descriptor.uuid != cccdUuid || descriptor.characteristic.uuid != ackCharacteristicUuid) return

            if (status != BluetoothGatt.GATT_SUCCESS) {
                closeLink(link, "ACK CCCD write failed: $status")
                maintainConnections()
                return
            }

            link.setupTimeoutJob?.cancel()
            link.setupTimeoutJob = null
            link.setupStage = "READY"
            link.ready = true
            BleMeshLog.i(
                "WARM_GATT_READY",
                "peer" to BleMeshLog.token(link.peerIdHex),
                "rssi" to link.rssi,
                "readyPeers" to readyPeerCount()
            )
            onReadyCountChanged(readyPeerCount())
        }

        override fun onCharacteristicWrite(
            gatt: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic,
            status: Int
        ) {
            if (characteristic.uuid != packetCharacteristicUuid || status == BluetoothGatt.GATT_SUCCESS) return
            val link = links.values.firstOrNull { it.gatt === gatt } ?: return
            failWrite(link, "local GATT write failed: $status", invalidateLink = false)
        }

        @Deprecated("Deprecated in API 33")
        override fun onCharacteristicChanged(
            gatt: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic
        ) {
            if (characteristic.uuid != ackCharacteristicUuid) return
            val link = links.values.firstOrNull { it.gatt === gatt } ?: return
            @Suppress("DEPRECATION")
            val value = characteristic.value ?: return
            handleTransportAck(link, value.copyOf())
        }

        override fun onCharacteristicChanged(
            gatt: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic,
            value: ByteArray
        ) {
            if (characteristic.uuid != ackCharacteristicUuid) return
            val link = links.values.firstOrNull { it.gatt === gatt } ?: return
            handleTransportAck(link, value)
        }
    }
}
