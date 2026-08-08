package com.travelingsalesman.agap.data.local.migration

import androidx.sqlite.db.SupportSQLiteDatabase
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.lang.reflect.Proxy

class Migration9To10Test {

    @Test
    fun migrate9To10_createsCompositePrimaryKeyTable() {
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

        MIGRATION_9_10.migrate(dummyDb)

        val fullSqlText = executedSqls.joinToString("\n")

        assertTrue(
            "Migration 9->10 must create composite key table `seen_mesh_packets_new`",
            fullSqlText.contains("`seen_mesh_packets_new`")
        )
        assertTrue(
            "Migration 9->10 must define composite primary key (`transportToken`, `packetType`)",
            fullSqlText.contains("PRIMARY KEY(`transportToken`, `packetType`)")
        )

        assertEquals(9, MIGRATION_9_10.startVersion)
        assertEquals(10, MIGRATION_9_10.endVersion)
    }
}
