package com.travelingsalesman.agap.data.local.migration

import androidx.sqlite.db.SupportSQLiteDatabase
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.lang.reflect.Proxy

class Migration8To9Test {

    @Test
    fun migrate8To9_executesAllRequiredTableCreations() {
        val executedSqls = mutableListOf<String>()

        val dummyDb = Proxy.newProxyInstance(
            SupportSQLiteDatabase::class.java.classLoader,
            arrayOf(SupportSQLiteDatabase::class.java)
        ) { _, method, args ->
            if (method.name == "execSQL" && args != null && args.isNotEmpty()) {
                executedSqls.add(args[0] as String)
            }
            null
        } as SupportSQLiteDatabase

        MIGRATION_8_9.migrate(dummyDb)

        val requiredTables = listOf(
            "local_incident_reports",
            "seen_mesh_packets",
            "relay_queue",
            "cached_catalog_versions",
            "cached_emergency_subtypes",
            "cached_agency_types",
            "cached_agency_service_areas",
            "cached_barangays",
            "pending_sync"
        )

        val fullSqlText = executedSqls.joinToString("\n")

        for (table in requiredTables) {
            assertTrue(
                "Migration 8->9 must create table `$table`",
                fullSqlText.contains("`$table`")
            )
        }

        assertEquals(8, MIGRATION_8_9.startVersion)
        assertEquals(9, MIGRATION_8_9.endVersion)
    }
}
