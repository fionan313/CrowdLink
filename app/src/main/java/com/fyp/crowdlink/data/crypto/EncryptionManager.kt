package com.fyp.crowdlink.data.crypto

import android.util.Base64
import android.util.Log
import com.google.crypto.tink.Aead
import com.google.crypto.tink.CleartextKeysetHandle
import com.google.crypto.tink.KeysetHandle
import com.google.crypto.tink.aead.AeadConfig
import com.google.crypto.tink.aead.AeadKeyTemplates
import com.google.crypto.tink.proto.AesGcmKey
import com.google.crypto.tink.proto.KeyData
import com.google.crypto.tink.proto.KeyStatusType
import com.google.crypto.tink.proto.Keyset
import com.google.crypto.tink.proto.OutputPrefixType
import com.google.crypto.tink.shaded.protobuf.ByteString
import com.google.crypto.tink.BinaryKeysetReader
import java.security.SecureRandom
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class EncryptionManager @Inject constructor() {

    init {
        AeadConfig.register()
    }

    /**
     * Generates a raw 32-byte AES key and returns it Base64 encoded.
     * Kept as raw bytes rather than a full Tink keyset so the QR code
     * payload stays small — 44 characters rather than ~400.
     */
    fun generateSharedKey(): String {
        val keyBytes = ByteArray(32)
        SecureRandom().nextBytes(keyBytes)
        return Base64.encodeToString(keyBytes, Base64.NO_WRAP)
    }

    /**
     * Encrypts plaintext using AES-256-GCM via Tink.
     * Tink handles nonce generation internally — nonce reuse is
     * structurally impossible.
     */
    fun encrypt(plaintext: ByteArray, sharedKeyBase64: String): ByteArray {
        return buildAead(sharedKeyBase64).encrypt(plaintext, null)
    }

    /**
     * Decrypts ciphertext using AES-256-GCM via Tink.
     * Throws GeneralSecurityException if decryption fails — caller
     * must handle this and drop the payload rather than crashing.
     */
    fun decrypt(ciphertext: ByteArray, sharedKeyBase64: String): ByteArray {
        return buildAead(sharedKeyBase64).decrypt(ciphertext, null)
    }

    /**
     * Imports a raw 32-byte Base64-encoded key into a Tink KeysetHandle
     * and returns an Aead primitive ready for use.
     */
    private fun buildAead(sharedKeyBase64: String): Aead {
        val keyBytes = Base64.decode(sharedKeyBase64, Base64.NO_WRAP)

        val aesGcmKey = AesGcmKey.newBuilder()
            .setKeyValue(ByteString.copyFrom(keyBytes))
            .setVersion(0)
            .build()

        val keyData = KeyData.newBuilder()
            .setTypeUrl("type.googleapis.com/google.crypto.tink.AesGcmKey")
            .setValue(aesGcmKey.toByteString())
            .setKeyMaterialType(KeyData.KeyMaterialType.SYMMETRIC)
            .build()

        val keyset = Keyset.newBuilder()
            .addKey(
                Keyset.Key.newBuilder()
                    .setKeyData(keyData)
                    .setStatus(KeyStatusType.ENABLED)
                    .setKeyId(1)
                    .setOutputPrefixType(OutputPrefixType.RAW)
                    .build()
            )
            .setPrimaryKeyId(1)
            .build()

        val keysetHandle = CleartextKeysetHandle.read(
            BinaryKeysetReader.withBytes(keyset.toByteArray())
        )

        return keysetHandle.getPrimitive(Aead::class.java)
    }
}
