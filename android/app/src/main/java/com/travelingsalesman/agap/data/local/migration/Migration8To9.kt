package com.travelingsalesman.agap.data.local.migration

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

val MIGRATION_8_9 = object : Migration(8, 9) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("""
            CREATE TABLE IF NOT EXISTS `local_incident_reports` (
                `reportUuid` TEXT NOT NULL,
                `transportToken` TEXT NOT NULL,
                `incidentUuid` TEXT,
                `submissionType` TEXT NOT NULL,
                `subjectRelation` TEXT NOT NULL,
                `emergencyType` TEXT NOT NULL,
                `subtype` TEXT NOT NULL,
                `description` TEXT NOT NULL,
                `peopleAffected` INTEGER NOT NULL,
                `immediateDanger` INTEGER NOT NULL,
                `locationSource` TEXT NOT NULL,
                `latitude` REAL,
                `longitude` REAL,
                `gpsAccuracyMeters` REAL,
                `selectedBarangayCode` TEXT,
                `observedTimestampMs` INTEGER NOT NULL,
                `syncState` TEXT NOT NULL,
                PRIMARY KEY(`reportUuid`)
            )
        """.trimIndent())

        db.execSQL("""
            CREATE TABLE IF NOT EXISTS `seen_mesh_packets` (
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
                PRIMARY KEY(`transportToken`)
            )
        """.trimIndent())

        db.execSQL("""
            CREATE TABLE IF NOT EXISTS `relay_queue` (
                `queueId` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                `transportToken` TEXT NOT NULL,
                `packetType` TEXT NOT NULL,
                `payloadBytesHex` TEXT NOT NULL,
                `priority` INTEGER NOT NULL,
                `isOriginLocal` INTEGER NOT NULL,
                `hopCount` INTEGER NOT NULL,
                `createdAtMs` INTEGER NOT NULL,
                `expiryMs` INTEGER NOT NULL,
                `lastAdvertiseMs` INTEGER NOT NULL,
                `nextAdvertiseMs` INTEGER NOT NULL,
                `advertiseCount` INTEGER NOT NULL,
                `relayState` TEXT NOT NULL
            )
        """.trimIndent())

        db.execSQL("""
            CREATE TABLE IF NOT EXISTS `cached_catalog_versions` (
                `catalogName` TEXT NOT NULL,
                `version` TEXT NOT NULL,
                `updatedAtMs` INTEGER NOT NULL,
                PRIMARY KEY(`catalogName`)
            )
        """.trimIndent())

        db.execSQL("""
            CREATE TABLE IF NOT EXISTS `cached_emergency_subtypes` (
                `subtypeCode` TEXT NOT NULL,
                `emergencyTypeCode` TEXT NOT NULL,
                `displayName` TEXT NOT NULL,
                `protocolCode` INTEGER NOT NULL,
                PRIMARY KEY(`subtypeCode`)
            )
        """.trimIndent())

        db.execSQL("""
            CREATE TABLE IF NOT EXISTS `cached_agency_types` (
                `agencyTypeCode` TEXT NOT NULL,
                `displayName` TEXT NOT NULL,
                PRIMARY KEY(`agencyTypeCode`)
            )
        """.trimIndent())

        db.execSQL("""
            CREATE TABLE IF NOT EXISTS `cached_agency_service_areas` (
                `areaId` TEXT NOT NULL,
                `agencyId` TEXT NOT NULL,
                `barangayCode` TEXT NOT NULL,
                `cityMunicipality` TEXT NOT NULL,
                `isPrimary` INTEGER NOT NULL,
                PRIMARY KEY(`areaId`)
            )
        """.trimIndent())

        db.execSQL("""
            CREATE TABLE IF NOT EXISTS `cached_barangays` (
                `barangayCode` TEXT NOT NULL,
                `barangayName` TEXT NOT NULL,
                `cityMunicipality` TEXT NOT NULL,
                `province` TEXT NOT NULL,
                `representativeLatitudeE7` INTEGER NOT NULL,
                `representativeLongitudeE7` INTEGER NOT NULL,
                `boundaryGeoJson` TEXT,
                PRIMARY KEY(`barangayCode`)
            )
        """.trimIndent())

        db.execSQL("""
            CREATE TABLE IF NOT EXISTS `pending_sync` (
                `syncId` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                `syncType` TEXT NOT NULL,
                `payloadJson` TEXT NOT NULL,
                `reportUuid` TEXT,
                `createdAtMs` INTEGER NOT NULL,
                `status` TEXT NOT NULL,
                `retryCount` INTEGER NOT NULL
            )
        """.trimIndent())
    }
}

