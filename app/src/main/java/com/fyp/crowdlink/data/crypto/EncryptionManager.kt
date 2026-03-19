package com.fyp.crowdlink.data.crypto

import android.util.Base64
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class EncryptionManager @Inject constructor() {

    private val secureRandom = SecureRandom()
    private val ALGORITHM = "AES/GCM/NoPadding"
    private val TAG_LENGTH = 128
    private val IV_LENGTH = 12

    /**
     * Generates a raw 32-byte AES key and returns it Base64 encoded.
     * This keeps the QR code payload small compared to full Tink keysets.
     */
    fun generateSharedKey(): String {
        val keyBytes = ByteArray(32)
        secureRandom.nextBytes(keyBytes)
        return Base64.encodeToString(keyBytes, Base64.NO_WRAP)
    }

    /**
     * Encrypts a payload using AES-256-GCM.
     * Prepends a 12-byte IV to the ciphertext.
     */
    fun encrypt(plaintext: ByteArray, sharedKeyBase64: String): ByteArray {
        val keyBytes = Base64.decode(sharedKeyBase64, Base64.NO_WRAP)
        val secretKey = SecretKeySpec(keyBytes, "AES")
        
        val iv = ByteArray(IV_LENGTH).also { secureRandom.nextBytes(it) }
        val cipher = Cipher.getInstance(ALGORITHM)
        cipher.init(Cipher.ENCRYPT_MODE, secretKey, GCMParameterSpec(TAG_LENGTH, iv))
        
        val ciphertext = cipher.doFinal(plaintext)
        return iv + ciphertext
    }

    /**
     * Decrypts a payload using AES-256-GCM.
     * Expects the first 12 bytes to be the IV.
     */
    fun decrypt(ciphertext: ByteArray, sharedKeyBase64: String): ByteArray {
        val keyBytes = Base64.decode(sharedKeyBase64, Base64.NO_WRAP)
        val secretKey = SecretKeySpec(keyBytes, "AES")
        
        val iv = ciphertext.copyOfRange(0, IV_LENGTH)
        val data = ciphertext.copyOfRange(IV_LENGTH, ciphertext.size)
        
        val cipher = Cipher.getInstance(ALGORITHM)
        cipher.init(Cipher.DECRYPT_MODE, secretKey, GCMParameterSpec(TAG_LENGTH, iv))
        
        return cipher.doFinal(data)
    }
}
