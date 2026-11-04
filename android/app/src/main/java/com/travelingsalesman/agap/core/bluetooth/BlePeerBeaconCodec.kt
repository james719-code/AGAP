package com.travelingsalesman.agap.core.bluetooth

/**
 * Tiny AGAP presence beacon carried in FEAF service-data continuously while
 * mesh discovery is active. Emergency payloads are never placed in BLE
 * advertisements in v3; this beacon exists only for peer discovery.
 *
 * Layout (9 bytes):
 *  0: 0x47 ('G')
 *  1: 0x50 ('P')
 *  2: beacon version (1)
 *  3..8: installation-scoped random peer id (6 bytes)
 */
object BlePeerBeaconCodec {
    const val LENGTH = 9
    private const val MAGIC_0: Byte = 0x47
    private const val MAGIC_1: Byte = 0x50
    private const val VERSION: Byte = 0x01

    fun encode(peerId: ByteArray): ByteArray {
        val id = ByteArray(6) { index -> peerId.getOrElse(index) { 0 } }
        return byteArrayOf(MAGIC_0, MAGIC_1, VERSION) + id
    }

    /** Returns the 12-hex peer id when this is an AGAP presence beacon. */
    fun decodePeerIdHex(data: ByteArray): String? {
        if (data.size != LENGTH) return null
        if (data[0] != MAGIC_0 || data[1] != MAGIC_1 || data[2] != VERSION) return null
        return data.toMeshHex(3, 9)
    }
}
