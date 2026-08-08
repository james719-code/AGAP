package com.travelingsalesman.agap.core.bluetooth

import timber.log.Timber

/**
 * Small structured logger for AGAP BLE mesh diagnostics.
 *
 * Logcat filter:
 *   tag:AGAP_BLE
 *
 * Tokens are shortened and exact coordinates/payload bytes are never printed.
 */
object BleMeshLog {
    const val TAG = "AGAP_BLE"

    fun d(event: String, vararg fields: Pair<String, Any?>) {
        Timber.tag(TAG).d(format(event, fields))
    }

    fun i(event: String, vararg fields: Pair<String, Any?>) {
        Timber.tag(TAG).i(format(event, fields))
    }

    fun w(event: String, vararg fields: Pair<String, Any?>, error: Throwable? = null) {
        if (error != null) {
            Timber.tag(TAG).w(error, format(event, fields))
        } else {
            Timber.tag(TAG).w(format(event, fields))
        }
    }

    fun e(event: String, vararg fields: Pair<String, Any?>, error: Throwable? = null) {
        if (error != null) {
            Timber.tag(TAG).e(error, format(event, fields))
        } else {
            Timber.tag(TAG).e(format(event, fields))
        }
    }

    fun token(tokenHex: String?): String {
        if (tokenHex.isNullOrBlank()) return "none"
        val clean = tokenHex.trim()
        return when {
            clean.length <= 6 -> clean
            else -> "${clean.take(4)}..${clean.takeLast(2)}"
        }
    }

    private fun format(event: String, fields: Array<out Pair<String, Any?>>): String {
        if (fields.isEmpty()) return "event=$event"
        return buildString {
            append("event=")
            append(event)
            fields.forEach { (key, value) ->
                append(' ')
                append(key)
                append('=')
                append(sanitize(value))
            }
        }
    }

    private fun sanitize(value: Any?): String {
        return when (value) {
            null -> "null"
            is Boolean, is Number -> value.toString()
            else -> value.toString()
                .replace('\n', '_')
                .replace('\r', '_')
                .replace(' ', '_')
                .take(160)
        }
    }
}
