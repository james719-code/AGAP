package com.travelingsalesman.agap.data.repository

import com.travelingsalesman.agap.data.local.dao.IncidentDao
import com.travelingsalesman.agap.data.local.entity.IncidentEntity

/** Cache a server snapshot without waking Room observers for unchanged incidents. */
internal suspend fun IncidentDao.cacheIncidentSnapshot(incoming: List<IncidentEntity>) {
    if (incoming.isEmpty()) return

    // Keep both lookups under SQLite's bind-parameter limit, including after an outage.
    val existingById = incoming.map { it.incidentId }.distinct()
        .chunked(INCIDENT_ID_QUERY_CHUNK)
        .flatMap { getIncidentsByIds(it) }
        .associateBy { it.incidentId }

    // Only unresolved primary keys need a logical lookup. Batch these too: a mesh
    // placeholder keeps its UUID after reconciliation with the server's numeric ID.
    val existingByLogicalId = incoming.asSequence()
        .filter { it.incidentId !in existingById }
        .map { it.logicalId }
        .filter { it.isNotBlank() }
        .distinct()
        .toList()
        .chunked(INCIDENT_ID_QUERY_CHUNK)
        .flatMap { getIncidentsByLogicalIds(it) }
        .distinctBy { it.logicalId }
        .associateBy { it.logicalId }

    val changed = incoming.mapNotNull { remote ->
        val local = existingById[remote.incidentId] ?: existingByLogicalId[remote.logicalId]
        val reconciled = if (local == null) remote else remote.copy(
            incidentId = local.incidentId,
            serverIncidentId = remote.serverIncidentId ?: remote.incidentId.toLongOrNull()
        )

        // Compare after identity reconciliation. Comparing a retained local UUID
        // against a numeric server ID would rewrite the same mesh row on every poll.
        reconciled.takeIf { local == null || !local.hasSameRemoteContent(reconciled) }
    }

    // Small transactions leave Room responsive to relay writes during catch-up.
    changed.chunked(INCIDENT_WRITE_CHUNK).forEach { insertIncidents(it) }
}

private const val INCIDENT_ID_QUERY_CHUNK = 400
private const val INCIDENT_WRITE_CHUNK = 200

private fun IncidentEntity.hasSameRemoteContent(
    other: IncidentEntity
): Boolean {
    return incidentId ==
            other.incidentId &&
            targetId ==
            other.targetId &&
            emergencyType ==
            other.emergencyType &&
            latitude ==
            other.latitude &&
            longitude ==
            other.longitude &&
            altitude ==
            other.altitude &&
            gpsAccuracy ==
            other.gpsAccuracy &&
            timestamp ==
            other.timestamp &&
            status ==
            other.status &&
            description ==
            other.description &&
            dispatcherNotes ==
            other.dispatcherNotes &&
            dispatchedUnitId ==
            other.dispatchedUnitId &&
            dispatchedAt ==
            other.dispatchedAt &&
            resolvedAt ==
            other.resolvedAt &&
            packetHash ==
            other.packetHash &&
            channel ==
            other.channel &&
            deliveryPath ==
            other.deliveryPath &&
            senderDeviceId ==
            other.senderDeviceId &&
            logicalId ==
            other.logicalId &&
            serverIncidentId ==
            other.serverIncidentId &&
            deliveryState ==
            other.deliveryState &&
            isDirty ==
            other.isDirty
}

