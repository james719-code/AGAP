package com.travelingsalesman.agap.data.local.migration

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * v13 makes incident messaging truly offline-capable:
 * - incident IDs become TEXT so local UUID/token incidents can own conversations;
 * - message IDs become TEXT so optimistic/mesh entries do not collide with server IDs;
 * - predefined catalog metadata and transport delivery state are persisted.
 */
val MIGRATION_12_13 = object : Migration(12, 13) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS `incident_messages_new` (
                `id` TEXT NOT NULL,
                `incidentId` TEXT NOT NULL,
                `senderId` INTEGER NOT NULL,
                `senderName` TEXT NOT NULL,
                `senderRole` TEXT NOT NULL,
                `messageContent` TEXT NOT NULL,
                `messageType` TEXT NOT NULL,
                `createdAt` INTEGER NOT NULL,
                `isSystem` INTEGER NOT NULL,
                `predefinedMessageId` INTEGER,
                `predefinedCode` TEXT,
                `protocolCode` INTEGER NOT NULL DEFAULT 0,
                `channel` TEXT NOT NULL DEFAULT 'ONLINE',
                `deliveryState` TEXT NOT NULL DEFAULT 'RECEIVED',
                PRIMARY KEY(`id`)
            )
            """.trimIndent()
        )
        db.execSQL(
            """
            INSERT INTO `incident_messages_new`
                (`id`, `incidentId`, `senderId`, `senderName`, `senderRole`, `messageContent`,
                 `messageType`, `createdAt`, `isSystem`, `predefinedMessageId`, `predefinedCode`,
                 `protocolCode`, `channel`, `deliveryState`)
            SELECT
                CAST(`id` AS TEXT), CAST(`incidentId` AS TEXT), `senderId`, `senderName`, `senderRole`,
                `messageContent`, `messageType`, `createdAt`, `isSystem`, NULL, NULL, 0, 'ONLINE', 'RECEIVED'
            FROM `incident_messages`
            """.trimIndent()
        )
        db.execSQL("DROP TABLE `incident_messages`")
        db.execSQL("ALTER TABLE `incident_messages_new` RENAME TO `incident_messages`")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_incident_messages_incidentId_createdAt` ON `incident_messages` (`incidentId`, `createdAt`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_incident_messages_predefinedMessageId` ON `incident_messages` (`predefinedMessageId`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_incident_messages_deliveryState_createdAt` ON `incident_messages` (`deliveryState`, `createdAt`)")

        // Older builds could seed predefined reply rows from bundled assets or a
        // local fallback. Those rows have no server_updated_at and must not remain
        // selectable now that the server catalog is the authoritative reply source.
        // Previously downloaded server rows are preserved for offline use.
        db.execSQL("DELETE FROM `predefined_messages` WHERE `server_updated_at` IS NULL")
    }
}
