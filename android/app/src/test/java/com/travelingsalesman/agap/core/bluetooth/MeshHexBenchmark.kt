package com.travelingsalesman.agap.core.bluetooth

/** Optional JVM microbenchmark; not a radio-throughput or battery measurement. */
object MeshHexBenchmark {
    @Volatile private var sink = 0

    @JvmStatic
    fun main(args: Array<String>) {
        for (size in listOf(6, 21, 96)) {
            val payload = ByteArray(size) { (it * 37).toByte() }
            val oldEncode = { payload.joinToString("") { "%02x".format(it) } }
            val newEncode = { payload.toMeshHex() }
            check(oldEncode() == newEncode())
            val hex = newEncode()
            val oldDecode = {
                if (hex.length % 2 != 0 || !hex.matches(Regex("^[0-9a-fA-F]+$"))) null
                else runCatching { hex.chunked(2).map { it.toInt(16).toByte() }.toByteArray() }.getOrNull()
            }
            val newDecode = { hex.meshHexToBytesOrNull() }
            check(oldDecode()!!.contentEquals(newDecode()!!))
            val beforeEncode = measure { sink = oldEncode().hashCode() }
            val afterEncode = measure { sink = newEncode().hashCode() }
            val beforeDecode = measure { sink = oldDecode()!!.contentHashCode() }
            val afterDecode = measure { sink = newDecode()!!.contentHashCode() }
            println("bytes=$size encodeBeforeNs=$beforeEncode encodeAfterNs=$afterEncode decodeBeforeNs=$beforeDecode decodeAfterNs=$afterDecode")
        }
    }

    private fun measure(action: () -> Unit): Long {
        repeat(5_000) { action() }
        return List(5) {
            val start = System.nanoTime()
            repeat(10_000) { action() }
            (System.nanoTime() - start) / 10_000
        }.sorted()[2]
    }
}
