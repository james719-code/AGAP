package com.travelingsalesman.agap.core.security

import android.util.Base64
import java.security.MessageDigest
import javax.crypto.Cipher
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec

object CryptoManager {
    private const val ALGORITHM = "AES/CBC/PKCS5Padding"
    private const val DEFAULT_SECRET = "0123456789abcdef0123456789abcdef"

    private fun getSecretKeySpec(secret: String = DEFAULT_SECRET): SecretKeySpec {
        val digest = MessageDigest.getInstance("SHA-256")
        val keyBytes = digest.digest(secret.toByteArray(Charsets.UTF_8))
        return SecretKeySpec(keyBytes, "AES")
    }

    fun encrypt(plainText: String, secret: String = DEFAULT_SECRET): String {
        val cipher = Cipher.getInstance(ALGORITHM)
        val keySpec = getSecretKeySpec(secret)
        val ivBytes = ByteArray(16) // Default zero-IV for simplicity or generate random IV
        val ivSpec = IvParameterSpec(ivBytes)

        cipher.init(Cipher.ENCRYPT_MODE, keySpec, ivSpec)
        val encryptedBytes = cipher.doFinal(plainText.toByteArray(Charsets.UTF_8))
        return Base64.encodeToString(encryptedBytes, Base64.NO_WRAP)
    }

    fun decrypt(cipherText: String, secret: String = DEFAULT_SECRET): String {
        val cipher = Cipher.getInstance(ALGORITHM)
        val keySpec = getSecretKeySpec(secret)
        val ivBytes = ByteArray(16)
        val ivSpec = IvParameterSpec(ivBytes)

        cipher.init(Cipher.DECRYPT_MODE, keySpec, ivSpec)
        val decodedBytes = Base64.decode(cipherText, Base64.NO_WRAP)
        val plainBytes = cipher.doFinal(decodedBytes)
        return String(plainBytes, Charsets.UTF_8)
    }

    fun computeHash(input: String): String {
        val digest = MessageDigest.getInstance("SHA-256")
        val hashBytes = digest.digest(input.toByteArray(Charsets.UTF_8))
        return hashBytes.joinToString("") { "%02x".format(it) }
    }
}
