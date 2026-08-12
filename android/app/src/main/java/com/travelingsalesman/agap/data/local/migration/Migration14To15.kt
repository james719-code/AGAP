package com.travelingsalesman.agap.data.local.migration

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * v15 adds a stable client id to incident messages. The same id is sent to the
 * backend for retry-safe idempotency, so a POST whose response was lost cannot
 * create a second operational message.
 */
val MIGRATION_14_15 = object : Migration(14, 15) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE incident_messages ADD COLUMN clientMessageId TEXT")
        db.execSQL("CREATE INDEX IF NOT EXISTS index_incident_messages_clientMessageId ON incident_messages(clientMessageId)")
    }
}
