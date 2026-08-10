package com.travelingsalesman.agap.data.local.migration

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * v14 separates transport identity from server identity and persists the
 * complete authenticated-user snapshot needed by offline relay/report flows.
 * The migration deliberately keeps incident_id as the navigation/API key for
 * compatibility; logical_id is the stable packet identity used for merging.
 */
val MIGRATION_13_14 = object : Migration(13, 14) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE pending_packets ADD COLUMN origin_device_id TEXT")

        db.execSQL("ALTER TABLE incidents ADD COLUMN logical_id TEXT NOT NULL DEFAULT ''")
        db.execSQL("ALTER TABLE incidents ADD COLUMN server_incident_id INTEGER")
        db.execSQL("ALTER TABLE incidents ADD COLUMN delivery_state TEXT NOT NULL DEFAULT 'RECEIVED_BY_COMMAND'")
        db.execSQL("UPDATE incidents SET logical_id = CASE WHEN packet_hash <> '' THEN packet_hash ELSE incident_id END")
        // Older server rows only carried their numeric primary key. Recover it
        // before choosing survivors so a server-backed copy wins over a mesh
        // placeholder when both already exist on the device.
        db.execSQL("UPDATE incidents SET server_incident_id = CAST(incident_id AS INTEGER) WHERE server_incident_id IS NULL AND incident_id NOT GLOB '*[^0-9]*'")

        // Collapse pre-v14 mesh/server duplicates by logical packet identity.
        // Messages follow the selected row, preserving one conversation thread.
        db.execSQL("CREATE TEMP TABLE incident_survivors (logical_id TEXT PRIMARY KEY, incident_id TEXT NOT NULL)")
        db.execSQL("""
            INSERT INTO incident_survivors(logical_id, incident_id)
            SELECT logical_id,
                   COALESCE(
                       MAX(CASE WHEN server_incident_id IS NOT NULL THEN incident_id END),
                       MAX(incident_id)
                   )
            FROM incidents
            GROUP BY logical_id
        """.trimIndent())
        db.execSQL("""
            UPDATE incident_messages
            SET incidentId = (
                SELECT survivor.incident_id
                FROM incident_survivors survivor
                JOIN incidents source ON source.logical_id = survivor.logical_id
                WHERE source.incident_id = incident_messages.incidentId
            )
            WHERE incidentId IN (SELECT incident_id FROM incidents)
        """.trimIndent())
        db.execSQL("DELETE FROM incidents WHERE incident_id NOT IN (SELECT incident_id FROM incident_survivors)")
        db.execSQL("DROP TABLE incident_survivors")
        db.execSQL("CREATE INDEX IF NOT EXISTS index_incidents_logical_id ON incidents(logical_id)")

        db.execSQL("ALTER TABLE local_incident_reports ADD COLUMN serverIncidentId INTEGER")
        db.execSQL("ALTER TABLE local_incident_reports ADD COLUMN logicalId TEXT NOT NULL DEFAULT ''")
        db.execSQL("UPDATE local_incident_reports SET logicalId = transportToken WHERE logicalId = ''")
        db.execSQL("CREATE INDEX IF NOT EXISTS index_local_incident_reports_logicalId ON local_incident_reports(logicalId)")

        db.execSQL("ALTER TABLE user_profiles ADD COLUMN username TEXT NOT NULL DEFAULT ''")
        db.execSQL("ALTER TABLE user_profiles ADD COLUMN role TEXT NOT NULL DEFAULT 'CITIZEN'")
        db.execSQL("ALTER TABLE user_profiles ADD COLUMN device_id TEXT")
        db.execSQL("ALTER TABLE user_profiles ADD COLUMN agency_id INTEGER")
        db.execSQL("ALTER TABLE user_profiles ADD COLUMN medical_info TEXT")
        db.execSQL("ALTER TABLE user_profiles ADD COLUMN national_id TEXT")
        db.execSQL("ALTER TABLE user_profiles ADD COLUMN account_status TEXT NOT NULL DEFAULT 'PENDING'")
        db.execSQL("ALTER TABLE user_profiles ADD COLUMN is_verified INTEGER NOT NULL DEFAULT 0")
        db.execSQL("ALTER TABLE user_profiles ADD COLUMN image_url TEXT")
        db.execSQL("ALTER TABLE user_profiles ADD COLUMN last_synced_at INTEGER NOT NULL DEFAULT 0")
        db.execSQL("UPDATE user_profiles SET last_synced_at = strftime('%s','now') * 1000 WHERE last_synced_at = 0")

        db.execSQL("ALTER TABLE emergency_contacts ADD COLUMN relationship TEXT NOT NULL DEFAULT ''")
    }
}
