package com.travelingsalesman.agap.core.network

import com.travelingsalesman.agap.core.bluetooth.BleMeshLog
import com.travelingsalesman.agap.data.local.ServerConfigManager
import com.travelingsalesman.agap.data.local.TransmissionMode
import com.travelingsalesman.agap.data.remote.dto.WebSocketEnvelopeDto
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.first
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AgapWebSocketClient @Inject constructor(
    private val okHttpClient: OkHttpClient,
    private val json: Json,
    private val serverConfigManager: ServerConfigManager
) {
    fun connect(): Flow<WebSocketEnvelopeDto> = callbackFlow {
        if (serverConfigManager.getTransmissionMode() == TransmissionMode.FORCE_BLE_MESH) {
            BleMeshLog.i("WEBSOCKET_SKIPPED_BLE_ONLY")
            close(BleOnlyModeNetworkException())
            return@callbackFlow
        }

        val wsUrl = serverConfigManager.wsUrl.first()
        val request = Request.Builder()
            .url(wsUrl)
            .build()
        BleMeshLog.d("WEBSOCKET_CONNECT_REQUEST", "host" to request.url.host)

        val listener = object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                BleMeshLog.i("WEBSOCKET_CONNECTED", "code" to response.code)
            }

            override fun onMessage(webSocket: WebSocket, text: String) {
                try {
                    val event = json.decodeFromString<WebSocketEnvelopeDto>(text)
                    trySend(event)
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
                BleMeshLog.w("WEBSOCKET_FAILURE", "errorType" to t::class.java.simpleName, error = t)
            }

            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
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
