package com.travelingsalesman.agap.core.network

import com.travelingsalesman.agap.BuildConfig
import com.travelingsalesman.agap.data.remote.dto.WebSocketEnvelopeDto
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.emptyFlow
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
    private val hostedState = MutableStateFlow(LiveSyncState.CONNECTING)
    val state: StateFlow<LiveSyncState> = if (BuildConfig.HOSTED_BACKEND) hostedState else webSocketClient.state

    fun connect(): Flow<WebSocketEnvelopeDto> =
        if (BuildConfig.HOSTED_BACKEND) emptyFlow() else webSocketClient.connect()

    fun onHostedSync(success: Boolean) {
        if (BuildConfig.HOSTED_BACKEND) hostedState.value = if (success) LiveSyncState.LIVE else LiveSyncState.STALE
    }
}
