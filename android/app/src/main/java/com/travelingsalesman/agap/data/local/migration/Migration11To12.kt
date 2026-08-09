package com.travelingsalesman.agap.data.local.migration

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * Adds indexes for the app's hot paths. These are deliberately targeted at
 * relay selection, duplicate/ACK lookup, active incident lists, responder
 * filtering, pending report upload, and bounded notification/message history.
 */
val MIGRATION_11_12 = object : Migration(11, 12) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_pending_packets_priority_created_at` ON `pending_packets` (`priority`, `created_at`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_pending_packets_created_at` ON `pending_packets` (`created_at`)")

        db.execSQL("CREATE INDEX IF NOT EXISTS `index_mesh_delivery_events_packet_hash_event_type_recorded_at` ON `mesh_delivery_events` (`packet_hash`, `event_type`, `recorded_at`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_mesh_delivery_events_event_type_recorded_at` ON `mesh_delivery_events` (`event_type`, `recorded_at`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_mesh_delivery_events_synced_at_recorded_at` ON `mesh_delivery_events` (`synced_at`, `recorded_at`)")

        db.execSQL("CREATE INDEX IF NOT EXISTS `index_incidents_timestamp` ON `incidents` (`timestamp`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_incidents_status_timestamp` ON `incidents` (`status`, `timestamp`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_incidents_packet_hash` ON `incidents` (`packet_hash`)")

        db.execSQL("CREATE INDEX IF NOT EXISTS `index_rescue_units_agency_id_unit_callsign` ON `rescue_units` (`agency_id`, `unit_callsign`)")

        db.execSQL("CREATE INDEX IF NOT EXISTS `index_seen_mesh_packets_expiryMs` ON `seen_mesh_packets` (`expiryMs`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_seen_mesh_packets_isTerminal_tombstoneExpiryMs` ON `seen_mesh_packets` (`isTerminal`, `tombstoneExpiryMs`)")

        db.execSQL("CREATE INDEX IF NOT EXISTS `index_relay_queue_relayState_expiryMs_priority_lastAdvertiseMs_createdAtMs` ON `relay_queue` (`relayState`, `expiryMs`, `priority`, `lastAdvertiseMs`, `createdAtMs`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_relay_queue_transportToken_packetType_relayState` ON `relay_queue` (`transportToken`, `packetType`, `relayState`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_relay_queue_expiryMs` ON `relay_queue` (`expiryMs`)")

        db.execSQL("CREATE INDEX IF NOT EXISTS `index_local_incident_reports_transportToken` ON `local_incident_reports` (`transportToken`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_local_incident_reports_syncState_observedTimestampMs` ON `local_incident_reports` (`syncState`, `observedTimestampMs`)")

        db.execSQL("CREATE INDEX IF NOT EXISTS `index_notifications_createdAt` ON `notifications` (`createdAt`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_notifications_isRead_createdAt` ON `notifications` (`isRead`, `createdAt`)")

        db.execSQL("CREATE INDEX IF NOT EXISTS `index_incident_messages_incidentId_createdAt` ON `incident_messages` (`incidentId`, `createdAt`)")

        db.execSQL("CREATE INDEX IF NOT EXISTS `index_pending_sync_status_createdAtMs` ON `pending_sync` (`status`, `createdAtMs`)")

        db.execSQL("CREATE INDEX IF NOT EXISTS `index_cached_emergency_subtypes_emergencyTypeCode` ON `cached_emergency_subtypes` (`emergencyTypeCode`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_cached_agency_service_areas_barangayCode_isPrimary` ON `cached_agency_service_areas` (`barangayCode`, `isPrimary`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_cached_agency_service_areas_agencyId` ON `cached_agency_service_areas` (`agencyId`)")
    }
}
