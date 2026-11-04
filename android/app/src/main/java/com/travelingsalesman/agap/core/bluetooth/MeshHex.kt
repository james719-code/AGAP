package com.travelingsalesman.agap.core.bluetooth

private const val MESH_HEX_DIGITS = "0123456789abcdef"

/** Fixed-width lowercase hex for persisted relay payloads and peer identities. */
internal fun ByteArray.toMeshHex(startIndex: Int = 0, endIndex: Int = size): String {
    require(startIndex >= 0 && endIndex >= startIndex && endIndex <= size)
    val chars = CharArray((endIndex - startIndex) * 2)
    var output = 0
    for (index in startIndex until endIndex) {
        val value = this[index].toInt() and 0xff
        chars[output++] = MESH_HEX_DIGITS[value ushr 4]
        chars[output++] = MESH_HEX_DIGITS[value and 0x0f]
    }
    return chars.concatToString()
}

/** Matches the relay queue's existing strict, nonempty ASCII hex format. */
internal fun String.meshHexToBytesOrNull(): ByteArray? {
    if (isEmpty() || length % 2 != 0) return null
    val bytes = ByteArray(length / 2)
    for (index in bytes.indices) {
        val high = this[index * 2].meshHexDigit()
        val low = this[index * 2 + 1].meshHexDigit()
        if (high < 0 || low < 0) return null
        bytes[index] = ((high shl 4) or low).toByte()
    }
    return bytes
}

private fun Char.meshHexDigit(): Int = when (this) {
    in '0'..'9' -> this - '0'
    in 'a'..'f' -> this - 'a' + 10
    in 'A'..'F' -> this - 'A' + 10
    else -> -1
}
