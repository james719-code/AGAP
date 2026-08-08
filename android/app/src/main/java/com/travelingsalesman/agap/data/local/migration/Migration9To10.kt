package com.travelingsalesman.agap.data.local.migration

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

val MIGRATION_9_10 = object : Migration(9, 10) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("""
            CREATE TABLE IF NOT EXISTS `seen_mesh_packets_new` (
                `transportToken` TEXT NOT NULL,
                `packetType` TEXT NOT NULL,
                `highestHop` INTEGER NOT NULL,
                `firstSeenMs` INTEGER NOT NULL,
                `lastSeenMs` INTEGER NOT NULL,
                `expiryMs` INTEGER NOT NULL,
                `deliveryState` TEXT NOT NULL,
                `isTerminal` INTEGER NOT NULL,
                `terminalStatus` TEXT,
                `terminalTimeMs` INTEGER,
                `tombstoneExpiryMs` INTEGER,
                PRIMARY KEY(`transportToken`, `packetType`)
            )
        """.trimIndent())

        db.execSQL("""
            INSERT OR REPLACE INTO `seen_mesh_packets_new` 
            SELECT `transportToken`, `packetType`, `highestHop`, `firstSeenMs`, `lastSeenMs`, `expiryMs`, `deliveryState`, `isTerminal`, `terminalStatus`, `terminalTimeMs`, `tombstoneExpiryMs` 
            FROM `seen_mesh_packets`
        """.trimIndent())

        db.execSQL("DROP TABLE `seen_mesh_packets`")
        db.execSQL("ALTER TABLE `seen_mesh_packets_new` RENAME TO `seen_mesh_packets`")
    }
}
