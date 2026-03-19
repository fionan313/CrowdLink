package com.fyp.crowdlink.data.crypto

import android.util.Base64
import com.google.crypto.tink.Aead
import com.google.crypto.tink.CleartextKeysetHandle
import com.google.crypto.tink.JsonKeysetReader
import com.google.crypto.tink.JsonKeysetWriter
import com.google.crypto.tink.KeysetHandle
import com.google.crypto.tink.aead.AeadConfig
import com.google.crypto.tink.aead.AeadKeyTemplates
import java.io.ByteArrayOutputStream
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class EncryptionManager @Inject constructor() {

    init {
        AeadConfig.register()
    }

    /**
     * Generates a new AES-256-GCM keyset and returns it serialised
     * as a Base64 string for storage in the Friend entity.
     */
    fun generateSharedKey(): String {
        val keysetHandle = KeysetHandle.generateNew(AeadKeyTemplates.AES256_GCM)
        val outputStream = ByteArrayOutputStream()
        CleartextKeysetHandle.write(keysetHandle, JsonKeysetWriter.withOutputStream(outputStream))
        return Base64.encodeToString(outputStream.toByteArray(), Base64.NO_WRAP)
    }

    /**
     * Encrypts a payload using the shared key for a specific friend.
     * Returns the ciphertext as a ByteArray.
     * Throws if the key is null or malformed.
     */
    fun encrypt(plaintext: ByteArray, sharedKeyBase64: String): ByteArray {
        return loadAead(sharedKeyBase64).encrypt(plaintext, null)
    }

    /**
     * Decrypts an incoming payload using the shared key.
     * Returns the plaintext ByteArray.
     * Throws GeneralSecurityException if decryption fails — caller should handle this.
     */
    fun decrypt(ciphertext: ByteArray, sharedKeyBase64: String): ByteArray {
        return loadAead(sharedKeyBase64).decrypt(ciphertext, null)
    }

    private fun loadAead(sharedKeyBase64: String): Aead {
        val keysetBytes = Base64.decode(sharedKeyBase64, Base64.NO_WRAP)
        val keysetHandle = CleartextKeysetHandle.read(JsonKeysetReader.withBytes(keysetBytes))
        return keysetHandle.getPrimitive(Aead::class.java)
    }
}
