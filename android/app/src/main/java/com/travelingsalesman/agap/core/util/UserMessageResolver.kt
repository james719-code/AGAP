package com.travelingsalesman.agap.core.util

import org.json.JSONObject
import java.net.ConnectException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import javax.net.ssl.SSLException

/** Converts technical failures into short, actionable messages safe for UI. */
object UserMessageResolver {
    fun network(action: String, error: Throwable? = null): String = when (error) {
        is SocketTimeoutException -> "$action is taking too long. Check your connection and try again."
        is UnknownHostException, is ConnectException -> "AGAP cannot reach the server right now. Check the server address or try again later."
        is SSLException -> "A secure connection to AGAP could not be established. Ensure your server address uses http:// (or has a valid SSL certificate)."
        else -> "$action could not be completed. Your saved data is still available."
    }

    fun http(action: String, code: Int, rawBody: String? = null): String {
        val backend = runCatching { JSONObject(rawBody.orEmpty()).optString("error") }
            .getOrNull()
            ?.trim()
            ?.takeIf { it.isNotEmpty() }
        return when (code) {
            401 -> "Your session has expired. Please log in again."
            403 -> "You do not have permission to $action."
            409 -> "This request was already received. No duplicate was created."
            in 400..499 -> backend ?: "AGAP could not $action. Check the information and try again."
            else -> "AGAP is temporarily unavailable. Your saved data is safe; try again shortly."
        }
    }

    fun safe(message: String?, fallback: String): String {
        val value = message?.trim().orEmpty()
        if (value.isBlank()) return fallback
        if (value.startsWith("{") || value.contains("HTTP ")) return fallback
        return value
    }
}
