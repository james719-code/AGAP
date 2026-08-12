package com.travelingsalesman.agap.data.local.migration

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * v16 persists account-scoped dispatch snapshots. They restore operational
 * admin/responder conversation routing when the app starts without Internet.
 */
val MIGRATION_15_16 = object : Migration(15, 16) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS `cached_dispatches` (
                `owner_user_id` INTEGER NOT NULL,
                `owner_scope` TEXT NOT NULL,
                `dispatch_id` INTEGER NOT NULL,
                `incident_id` INTEGER NOT NULL,
                `unit_id` INTEGER NOT NULL,
                `status` TEXT NOT NULL,
                `dispatched_at` TEXT,
                `accepted_at` TEXT,
                `departed_at` TEXT,
                `arrived_at` TEXT,
                `completed_at` TEXT,
                `unit_callsign` TEXT,
                `agency_name` TEXT,
                `responder_name` TEXT,
                `cached_at` INTEGER NOT NULL,
                PRIMARY KEY(`owner_user_id`, `owner_scope`, `dispatch_id`)
            )
            """.trimIndent()
        )
        db.execSQL(
            """
            CREATE INDEX IF NOT EXISTS
                `index_cached_dispatches_owner_user_id_owner_scope_incident_id`
            ON `cached_dispatches` (`owner_user_id`, `owner_scope`, `incident_id`)
            """.trimIndent()
        )
    }
}
