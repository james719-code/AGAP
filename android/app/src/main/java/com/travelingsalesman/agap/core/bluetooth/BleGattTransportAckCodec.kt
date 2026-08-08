package com.travelingsalesman.agap.core.bluetooth

/**
 * Tiny transport-level ACK used only by the warm GATT link.
 *
 * This is intentionally separate from the mesh ACK/STATUS packet types. A mesh
 * ACK is application data that may itself be relayed through the network. This
 * transport ACK only means: "this directly connected peer has stored or already
 * seen the packet you just wrote".
 *
 * Wire format (8 bytes):
 *   [0] 0xAC magic
 *   [1] status: 1 = accepted/already-seen, 0 = rejected
 *   [2..7] six-byte transport token
 */
internal object BleGattTransportAckCodec {
    private const val MAGIC: Byte = 0xAC.toByte()
    private const val LENGTH = 8
    const val STATUS_REJECTED: Int = 0
    const val STATUS_ACCEPTED: Int = 1

    data class Ack(
        val accepted: Boolean,
        val tokenHex: String
    )

    fun encode(tokenHex: String, accepted: Boolean): ByteArray? {
        val token = tokenHex.hexToSixBytesOrNull() ?: return null
        return ByteArray(LENGTH).also { out ->
            out[0] = MAGIC
            out[1] = if (accepted) STATUS_ACCEPTED.toByte() else STATUS_REJECTED.toByte()
            token.copyInto(out, destinationOffset = 2)
        }
    }

    fun decode(bytes: ByteArray): Ack? {
        if (bytes.size != LENGTH || bytes[0] != MAGIC) return null
        val status = bytes[1].toInt() and 0xFF
        if (status != STATUS_ACCEPTED && status != STATUS_REJECTED) return null
        val tokenHex = bytes.copyOfRange(2, 8).joinToString("") { "%02x".format(it) }
        return Ack(accepted = status == STATUS_ACCEPTED, tokenHex = tokenHex)
    }

    private fun String.hexToSixBytesOrNull(): ByteArray? {
        val clean = trim().lowercase()
        if (!clean.matches(Regex("^[0-9a-f]{12}$"))) return null
        return runCatching {
            clean.chunked(2).map { it.toInt(16).toByte() }.toByteArray()
        }.getOrNull()
    }
}
