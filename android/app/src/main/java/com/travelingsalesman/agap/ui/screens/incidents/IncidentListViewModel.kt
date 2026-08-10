package com.travelingsalesman.agap.ui.screens.incidents

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.travelingsalesman.agap.core.util.Result
import com.travelingsalesman.agap.core.network.LiveSyncState
import com.travelingsalesman.agap.data.local.entity.IncidentEntity
import com.travelingsalesman.agap.data.repository.AuthRepository
import com.travelingsalesman.agap.data.repository.IncidentRepository
import com.travelingsalesman.agap.data.repository.NotificationRepository
import com.travelingsalesman.agap.data.repository.TelemetryRepository
import com.travelingsalesman.agap.domain.usecase.GetIncidentsUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import com.travelingsalesman.agap.data.local.ServerConfigManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

data class IncidentListUiState(
    /**
     * Only the current visible chunk is exposed to Compose.
     */
    val incidents:
    List<IncidentEntity> =
        emptyList(),

    val totalIncidentCount:
    Int = 0,

    val totalFilteredCount:
    Int = 0,

    val needsActionCount:
    Int = 0,

    val activeResponseCount:
    Int = 0,

    val resolvedCount:
    Int = 0,

    val selectedStatusFilter:
    String? = null,

    val selectedTypeFilter:
    String? = null,

    val searchQuery:
    String = "",

    val isLoading:
    Boolean = false,

    val isFiltering:
    Boolean = false,

    val canLoadMore:
    Boolean = false,

    val errorMessage:
    String? = null,

    val lastSuccessfulRefreshAt:
    Long? = null,

    val role:
    String? = null,

    val liveSyncState: LiveSyncState = LiveSyncState.CONNECTING,
    val unreadNotificationCount: Int = 0,
    val pendingPacketCount: Int = 0
)

@HiltViewModel
class IncidentListViewModel @Inject constructor(
    private val getIncidentsUseCase:
    GetIncidentsUseCase,

    private val incidentRepository:
    IncidentRepository,

    private val authRepository:
    AuthRepository,

    private val notificationRepository:
    NotificationRepository,

    private val telemetryRepository:
    TelemetryRepository
) : ViewModel() {

    private val _uiState =
        MutableStateFlow(
            IncidentListUiState()
        )

    val uiState:
            StateFlow<IncidentListUiState> =
        _uiState.asStateFlow()

    /*
     * Private bounded data set.
     *
     * Full incident history stays on the backend. The mobile device keeps a
     * large enough recent window for responsive local search/history.
     */
    private var cachedIncidents:
            List<IncidentEntity> =
        emptyList()

    private var visibleLimit =
        INITIAL_VISIBLE_COUNT

    private var filterJob:
            Job? = null

    init {
        observeRole()
        observeCachedIncidents()
        viewModelScope.launch {
            incidentRepository.liveSyncState.collect { state ->
                _uiState.update { it.copy(liveSyncState = state) }
            }
        }
        refreshRemoteIncidents()
    }

    private fun observeRole() {
        viewModelScope.launch {
            authRepository.userRole.collect { role ->
                _uiState.update { it.copy(role = role) }
            }
        }
    }

    private fun observeCachedIncidents() {
        viewModelScope.launch {
            incidentRepository
                .getRecentIncidents(
                    MOBILE_HISTORY_WORKING_SET
                )
                .distinctUntilChanged()
                .collect { localList ->
                    cachedIncidents =
                        localList

                    schedulePublish(
                        debounceMillis = 0L
                    )
                }
        }
    }

    fun setStatusFilter(
        status: String?
    ) {
        _uiState.update { state ->
            val normalized =
                status?.uppercase()

            val next =
                if (
                    normalized != null &&
                    state.selectedStatusFilter
                        .equals(
                            normalized,
                            ignoreCase = true
                        )
                ) {
                    null
                } else {
                    normalized
                }

            state.copy(
                selectedStatusFilter =
                    next
            )
        }

        resetVisibleWindow()
        schedulePublish(0L)
    }

    fun setTypeFilter(
        type: String?
    ) {
        _uiState.update { state ->
            val normalized =
                type?.uppercase()

            val next =
                if (
                    normalized != null &&
                    state.selectedTypeFilter
                        .equals(
                            normalized,
                            ignoreCase = true
                        )
                ) {
                    null
                } else {
                    normalized
                }

            state.copy(
                selectedTypeFilter =
                    next
            )
        }

        resetVisibleWindow()
        schedulePublish(0L)
    }

    /**
     * Debounced and calculated away from the UI thread.
     */
    fun setSearchQuery(
        query: String
    ) {
        _uiState.update {
            it.copy(
                searchQuery = query,
                isFiltering = true
            )
        }

        resetVisibleWindow()

        schedulePublish(
            SEARCH_DEBOUNCE_MS
        )
    }

    fun clearFilters() {
        _uiState.update {
            it.copy(
                selectedStatusFilter = null,
                selectedTypeFilter = null,
                searchQuery = ""
            )
        }

        resetVisibleWindow()
        schedulePublish(0L)
    }

    fun loadMore() {
        if (!_uiState.value.canLoadMore) {
            return
        }

        visibleLimit +=
            VISIBLE_PAGE_SIZE

        schedulePublish(0L)
    }

    fun refreshRemoteIncidents() {
        if (_uiState.value.isLoading) {
            return
        }

        _uiState.update {
            it.copy(
                isLoading = true,
                errorMessage = null
            )
        }

        if (ServerConfigManager.isBleOnlyActive) {
            _uiState.update {
                it.copy(
                    isLoading = false,
                    lastSuccessfulRefreshAt = System.currentTimeMillis()
                )
            }
            return
        }

        viewModelScope.launch {
            when (
                val result =
                    getIncidentsUseCase()
            ) {
                is Result.Success -> {
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            lastSuccessfulRefreshAt =
                                System.currentTimeMillis()
                        )
                    }
                }

                is Result.Error -> {
                    val msg = result.message ?: ""
                    val isBleOrOfflineBypass = msg.contains("BLE mesh", ignoreCase = true) ||
                            msg.contains("Internet transport is disabled", ignoreCase = true) ||
                            msg.contains("offline", ignoreCase = true)

                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            errorMessage = if (isBleOrOfflineBypass) null else (result.message ?: "Unable to refresh incidents."),
                            lastSuccessfulRefreshAt = if (isBleOrOfflineBypass) System.currentTimeMillis() else it.lastSuccessfulRefreshAt
                        )
                    }
                }

                Result.Loading -> Unit
            }
        }
    }

    fun clearErrorMessage() {
        _uiState.update {
            it.copy(
                errorMessage = null
            )
        }
    }

    private fun resetVisibleWindow() {
        visibleLimit =
            INITIAL_VISIBLE_COUNT
    }

    private fun schedulePublish(
        debounceMillis: Long
    ) {
        filterJob?.cancel()

        filterJob =
            viewModelScope.launch {
                if (debounceMillis > 0L) {
                    delay(
                        debounceMillis
                    )
                }

                val stateSnapshot =
                    _uiState.value

                val sourceSnapshot =
                    cachedIncidents

                val requestedVisibleLimit =
                    visibleLimit

                val computed =
                    withContext(
                        Dispatchers.Default
                    ) {
                        buildDisplayResult(
                            source =
                                sourceSnapshot,
                            statusFilter =
                                stateSnapshot
                                    .selectedStatusFilter,
                            typeFilter =
                                stateSnapshot
                                    .selectedTypeFilter,
                            query =
                                stateSnapshot
                                    .searchQuery,
                            visibleLimit =
                                requestedVisibleLimit
                        )
                    }

                val latest =
                    _uiState.value

                if (
                    latest.searchQuery !=
                    stateSnapshot.searchQuery ||
                    latest.selectedStatusFilter !=
                    stateSnapshot
                        .selectedStatusFilter ||
                    latest.selectedTypeFilter !=
                    stateSnapshot
                        .selectedTypeFilter
                ) {
                    return@launch
                }

                _uiState.update {
                    it.copy(
                        incidents =
                            computed.visibleItems,
                        totalIncidentCount =
                            computed.totalCount,
                        totalFilteredCount =
                            computed.filteredCount,
                        needsActionCount =
                            computed.needsActionCount,
                        activeResponseCount =
                            computed.activeResponseCount,
                        resolvedCount =
                            computed.resolvedCount,
                        canLoadMore =
                            computed.hasMore,
                        isFiltering = false
                    )
                }
            }
    }

    private fun buildDisplayResult(
        source: List<IncidentEntity>,
        statusFilter: String?,
        typeFilter: String?,
        query: String,
        visibleLimit: Int
    ): DisplayResult {
        val normalizedQuery =
            query.trim()

        var needsAction = 0
        var activeResponse = 0
        var resolved = 0

        source.forEach { incident ->
            when (
                incident.status.uppercase()
            ) {
                "RECEIVED",
                "ACKNOWLEDGED" ->
                    needsAction++

                "DISPATCHED",
                "EN_ROUTE",
                "ON_SCENE" ->
                    activeResponse++

                "RESOLVED" ->
                    resolved++
            }
        }

        val filtered =
            source
                .asSequence()
                .filter { incident ->
                    val matchesStatus =
                        statusFilter == null ||
                                incident.status
                                    .equals(
                                        statusFilter,
                                        ignoreCase = true
                                    )

                    val matchesType =
                        typeFilter == null ||
                                incident.emergencyType
                                    .equals(
                                        typeFilter,
                                        ignoreCase = true
                                    )

                    val matchesSearch =
                        normalizedQuery.isBlank() ||
                                incident.matchesQuery(
                                    normalizedQuery
                                )

                    matchesStatus &&
                            matchesType &&
                            matchesSearch
                }
                .sortedWith(
                    compareBy<IncidentEntity> {
                        incidentStatusPriority(
                            it.status
                        )
                    }.thenByDescending {
                        it.timestamp
                    }
                )
                .toList()

        val visible =
            filtered.take(
                visibleLimit.coerceAtLeast(
                    INITIAL_VISIBLE_COUNT
                )
            )

        return DisplayResult(
            visibleItems = visible,
            totalCount = source.size,
            filteredCount = filtered.size,
            needsActionCount = needsAction,
            activeResponseCount =
                activeResponse,
            resolvedCount = resolved,
            hasMore =
                visible.size <
                        filtered.size
        )
    }

    private data class DisplayResult(
        val visibleItems:
        List<IncidentEntity>,
        val totalCount: Int,
        val filteredCount: Int,
        val needsActionCount: Int,
        val activeResponseCount: Int,
        val resolvedCount: Int,
        val hasMore: Boolean
    )

    companion object {
        private const val INITIAL_VISIBLE_COUNT = 10
        private const val VISIBLE_PAGE_SIZE = 10
        private const val MOBILE_HISTORY_WORKING_SET = 1_000
        private const val SEARCH_DEBOUNCE_MS = 180L
    }
}

private fun IncidentEntity.matchesQuery(
    query: String
): Boolean {
    return incidentId.contains(
        query,
        ignoreCase = true
    ) ||
            emergencyType.contains(
                query,
                ignoreCase = true
            ) ||
            status.contains(
                query,
                ignoreCase = true
            ) ||
            targetId.contains(
                query,
                ignoreCase = true
            ) ||
            channel.contains(
                query,
                ignoreCase = true
            ) ||
            deliveryPath.contains(
                query,
                ignoreCase = true
            ) ||
            description.orEmpty()
                .contains(
                    query,
                    ignoreCase = true
                ) ||
            dispatcherNotes.orEmpty()
                .contains(
                    query,
                    ignoreCase = true
                )
}

private fun incidentStatusPriority(
    status: String
): Int {
    return when (
        status.uppercase()
    ) {
        "RECEIVED" -> 0
        "ACKNOWLEDGED" -> 1
        "DISPATCHED" -> 2
        "EN_ROUTE" -> 3
        "ON_SCENE" -> 4
        "RESOLVED" -> 5
        "CANCELLED" -> 6
        else -> 7
    }
}
