package com.travelingsalesman.agap.core.network

import com.travelingsalesman.agap.core.bluetooth.BleMeshLog
import com.travelingsalesman.agap.data.local.ServerConfigManager
import com.travelingsalesman.agap.data.local.TransmissionMode
import com.travelingsalesman.agap.data.remote.dto.WebSocketEnvelopeDto
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AgapWebSocketClient @Inject constructor(
    private val okHttpClient: OkHttpClient,
    private val json: Json,
    private val serverConfigManager: ServerConfigManager
) {
    private val _state = MutableStateFlow(LiveSyncState.OFFLINE)
    val state = _state.asStateFlow()

    fun connect(): Flow<WebSocketEnvelopeDto> = callbackFlow {
        if (serverConfigManager.getTransmissionMode() == TransmissionMode.FORCE_BLE_MESH) {
            _state.value = LiveSyncState.BLE_ONLY
            BleMeshLog.i("WEBSOCKET_SKIPPED_BLE_ONLY")
            close(BleOnlyModeNetworkException())
            return@callbackFlow
        }

        _state.value = LiveSyncState.CONNECTING

        val wsUrl = serverConfigManager.getWsUrl()
        val request = Request.Builder()
            .url(wsUrl)
            .build()
        BleMeshLog.d("WEBSOCKET_CONNECT_REQUEST", "host" to request.url.host)

        val listener = object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                _state.value = LiveSyncState.LIVE
                BleMeshLog.i("WEBSOCKET_CONNECTED", "code" to response.code)
            }

            override fun onMessage(webSocket: WebSocket, text: String) {
                try {
                    val event = json.decodeFromString<WebSocketEnvelopeDto>(text)
                    val result = trySend(event)
                    if (result.isFailure) {
                        // Never silently lose an emergency update under a UI/Room
                        // backpressure spike. Closing makes the repository reconnect
                        // and lets its REST fallback resynchronize authoritative data.
                        BleMeshLog.w(
                            "WEBSOCKET_BACKPRESSURE_RECONNECT",
                            "length" to text.length
                        )
                        webSocket.close(1013, "Client backpressure")
                        channel.close(IOException("WebSocket consumer backpressure"))
                    }
                } catch (e: Exception) {
                    BleMeshLog.w(
                        "WEBSOCKET_MESSAGE_PARSE_FAILED",
                        "length" to text.length,
                        "errorType" to e::class.java.simpleName,
                        error = e
                    )
                }
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                _state.value = LiveSyncState.STALE
                BleMeshLog.w("WEBSOCKET_FAILURE", "errorType" to t::class.java.simpleName, error = t)
                channel.close(t)
            }

            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                _state.value = LiveSyncState.STALE
                BleMeshLog.i("WEBSOCKET_CLOSED", "code" to code, "reason" to reason)
                channel.close()
            }
        }

        val webSocket = okHttpClient.newWebSocket(request, listener)

        awaitClose {
            webSocket.close(1000, "Client closing")
        }
    }
}
