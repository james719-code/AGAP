package com.travelingsalesman.agap.core.network

import com.travelingsalesman.agap.data.remote.dto.WebSocketEnvelopeDto
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Process-wide owner for the realtime channel. Screens observe the state and
 * Room; they never create their own WebSocket connection.
 */
@Singleton
class RealtimeSyncCoordinator @Inject constructor(
    private val webSocketClient: AgapWebSocketClient
) {
    val state: StateFlow<LiveSyncState> = webSocketClient.state

    fun connect(): Flow<WebSocketEnvelopeDto> = webSocketClient.connect()
}
