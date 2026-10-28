package com.travelingsalesman.agap.core.sync

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.supervisorScope

/** Create observers only for an authenticated session, and dispose them on logout. */
internal suspend fun collectAuthenticatedWork(
    authenticated: Flow<Boolean>,
    work: suspend CoroutineScope.() -> Unit
) {
    authenticated.distinctUntilChanged().collectLatest { signedIn ->
        // Each observer remains supervised independently, as application tasks
        // were before session gating. Logout still cancels all session children.
        if (signedIn) supervisorScope(block = work)
    }
}
