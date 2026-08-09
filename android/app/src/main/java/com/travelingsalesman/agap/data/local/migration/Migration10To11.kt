package com.travelingsalesman.agap.data.local.migration

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * Restores the schema transition that originally relied on destructive fallback.
 * Keeping it explicit preserves incident-message and notification history for
 * users upgrading from database v10.
 */
val MIGRATION_10_11 = object : Migration(10, 11) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS `incident_messages` (
                `id` INTEGER NOT NULL,
                `incidentId` INTEGER NOT NULL,
                `senderId` INTEGER NOT NULL,
                `senderName` TEXT NOT NULL,
                `senderRole` TEXT NOT NULL,
                `messageContent` TEXT NOT NULL,
                `messageType` TEXT NOT NULL,
                `createdAt` INTEGER NOT NULL,
                `isSystem` INTEGER NOT NULL,
                PRIMARY KEY(`id`)
            )
            """.trimIndent()
        )

        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS `notifications` (
                `id` INTEGER NOT NULL,
                `userId` INTEGER NOT NULL,
                `title` TEXT NOT NULL,
                `message` TEXT NOT NULL,
                `type` TEXT NOT NULL,
                `isRead` INTEGER NOT NULL,
                `referenceId` TEXT,
                `createdAt` INTEGER NOT NULL,
                PRIMARY KEY(`id`)
            )
            """.trimIndent()
        )
    }
}
