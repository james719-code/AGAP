package com.travelingsalesman.agap.core.util

object UserIdFormatter {
    fun formatUserId(role: String, dbId: Long): String {
        val prefix = when (role.uppercase()) {
            "ADMIN" -> 1
            "RESPONDER" -> 2
            "CITIZEN" -> 3
            else -> 3
        }
        return "$prefix-$dbId"
    }
}
