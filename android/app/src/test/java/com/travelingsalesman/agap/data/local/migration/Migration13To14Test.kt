package com.travelingsalesman.agap.data.local.migration

import androidx.sqlite.db.SupportSQLiteDatabase
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.lang.reflect.Proxy

class Migration13To14Test {
    @Test
    fun migrate13To14_addsLogicalIdentityAndReparentsMessages() {
        val sql = mutableListOf<String>()
        val db = Proxy.newProxyInstance(
            SupportSQLiteDatabase::class.java.classLoader,
            arrayOf(SupportSQLiteDatabase::class.java)
        ) { _, method, args ->
            if (method.name == "execSQL" && !args.isNullOrEmpty()) sql += args[0] as String
            null
        } as SupportSQLiteDatabase

        MIGRATION_13_14.migrate(db)
        val text = sql.joinToString("\n")
        assertTrue(text.contains("origin_device_id"))
        assertTrue(text.contains("server_incident_id"))
        assertTrue(text.contains("logical_id"))
        assertTrue(text.contains("incident_survivors"))
        assertTrue(text.contains("UPDATE incident_messages"))
        assertEquals(13, MIGRATION_13_14.startVersion)
        assertEquals(14, MIGRATION_13_14.endVersion)
    }
}
