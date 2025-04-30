package com.sdk.cmscuresdk



import android.util.Base64
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.Mac
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

object CryptoHelper {

    private const val AES_KEY_SIZE = 256
    private const val GCM_TAG_LENGTH = 128
    private const val GCM_IV_LENGTH = 12

    fun deriveKey(secret: String): SecretKey {
        val keyBytes = secret.toByteArray(Charsets.UTF_8)
        val hash = java.security.MessageDigest.getInstance("SHA-256").digest(keyBytes)
        return SecretKeySpec(hash, "AES")
    }

    fun encryptAES(body: String, secretKey: SecretKey): Triple<String, String, String> {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        val iv = ByteArray(GCM_IV_LENGTH)
        SecureRandom().nextBytes(iv)
        val spec = GCMParameterSpec(GCM_TAG_LENGTH, iv)
        cipher.init(Cipher.ENCRYPT_MODE, secretKey, spec)
        val encrypted = cipher.doFinal(body.toByteArray(Charsets.UTF_8))

        val cipherText = encrypted.copyOfRange(0, encrypted.size - 16)
        val tag = encrypted.copyOfRange(encrypted.size - 16, encrypted.size)

        return Triple(
            Base64.encodeToString(iv, Base64.NO_WRAP),
            Base64.encodeToString(cipherText, Base64.NO_WRAP),
            Base64.encodeToString(tag, Base64.NO_WRAP)
        )
    }

    fun generateHmac(data: ByteArray, secretKey: SecretKey): String {
        val mac = Mac.getInstance("HmacSHA256")
        mac.init(secretKey)
        val hmacBytes = mac.doFinal(data)
        return Base64.encodeToString(hmacBytes, Base64.NO_WRAP)
    }
}
