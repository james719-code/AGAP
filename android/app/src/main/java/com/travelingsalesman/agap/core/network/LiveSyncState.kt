package com.travelingsalesman.agap.core.network

enum class LiveSyncState {
    CONNECTING,
    LIVE,
    STALE,
    OFFLINE,
    BLE_ONLY
}
