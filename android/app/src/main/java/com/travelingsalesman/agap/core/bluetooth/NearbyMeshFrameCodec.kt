package com.travelingsalesman.agap.core.bluetooth

import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Tiny application envelope used over Nearby BYTES payloads.
 *
 * Nearby owns radio framing/retransmission. This envelope only correlates an
 * AGAP DATA payload with the application ACK that means "receiver persisted it".
 */
internal object NearbyMeshFrameCodec {
    private const val MAGIC_0: Byte = 0x41 // A
    private const val MAGIC_1: Byte = 0x47 // G
    private const val VERSION: Byte = 1
    private const val KIND_DATA: Byte = 1
    private const val KIND_ACK: Byte = 2
    private const val FIXED_HEADER_BYTES = 12 // magic(2)+version(1)+kind(1)+transferId(8)

    sealed interface Frame {
        val transferId: Long

        data class Data(
            override val transferId: Long,
            val agapPayload: ByteArray
        ) : Frame

        data class Ack(
            override val transferId: Long,
            val accepted: Boolean
        ) : Frame
    }

    fun encodeData(transferId: Long, payload: ByteArray): ByteArray {
        return ByteBuffer.allocate(FIXED_HEADER_BYTES + payload.size)
            .order(ByteOrder.BIG_ENDIAN)
            .put(MAGIC_0)
            .put(MAGIC_1)
            .put(VERSION)
            .put(KIND_DATA)
            .putLong(transferId)
            .put(payload)
            .array()
    }

    fun encodeAck(transferId: Long, accepted: Boolean): ByteArray {
        return ByteBuffer.allocate(FIXED_HEADER_BYTES + 1)
            .order(ByteOrder.BIG_ENDIAN)
            .put(MAGIC_0)
            .put(MAGIC_1)
            .put(VERSION)
            .put(KIND_ACK)
            .putLong(transferId)
            .put(if (accepted) 1 else 0)
            .array()
    }

    fun decode(bytes: ByteArray): Frame? {
        if (bytes.size < FIXED_HEADER_BYTES) return null
        val buffer = ByteBuffer.wrap(bytes).order(ByteOrder.BIG_ENDIAN)
        if (buffer.get() != MAGIC_0 || buffer.get() != MAGIC_1) return null
        if (buffer.get() != VERSION) return null
        val kind = buffer.get()
        val transferId = buffer.long
        return when (kind) {
            KIND_DATA -> {
                if (!buffer.hasRemaining()) return null
                val payload = ByteArray(buffer.remaining())
                buffer.get(payload)
                Frame.Data(transferId, payload)
            }

            KIND_ACK -> {
                if (buffer.remaining() != 1) return null
                Frame.Ack(transferId, buffer.get().toInt() != 0)
            }

            else -> null
        }
    }
}
