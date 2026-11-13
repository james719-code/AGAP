package com.travelingsalesman.agap.data.local

import com.travelingsalesman.agap.BuildConfig
import java.security.MessageDigest

private val backendSuffix = if (BuildConfig.HOSTED_BACKEND) {
    val digest = MessageDigest.getInstance("SHA-256").digest(BuildConfig.BASE_URL.toByteArray())
    "-" + digest.take(8).joinToString("") { "%02x".format(it) }
} else {
    ""
}

internal fun backendStorageName(name: String): String = name + backendSuffix
