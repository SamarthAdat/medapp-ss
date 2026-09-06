package com.ss.medrecord.core.security

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import java.security.KeyStore
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey

/**
 * Access to the AES-256/GCM keys the app keeps in the Android Keystore.
 *
 * Key material never leaves hardware-backed storage (StrongBox or TEE where the
 * device has it): the app gets a handle, not the bytes, so the keys cannot be
 * extracted from the device even with root.
 *
 * Keys are not gated on user authentication. Background sync and background
 * uploads both have to run while the device is locked, and a key that could
 * only be used with the screen unlocked would stall the outbox instead of
 * protecting anything the lock screen does not already cover.
 */
internal object KeystoreAesKeys {

    const val ANDROID_KEYSTORE = "AndroidKeyStore"
    const val TRANSFORMATION = "AES/GCM/NoPadding"
    const val GCM_IV_LENGTH_BYTES = 12
    const val GCM_TAG_LENGTH_BITS = 128

    private const val AES_KEY_SIZE_BITS = 256

    fun keyStore(): KeyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }

    fun getKey(alias: String): SecretKey? = keyStore().getKey(alias, null) as? SecretKey

    fun getOrCreateKey(alias: String): SecretKey {
        getKey(alias)?.let { return it }

        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE)
        generator.init(
            KeyGenParameterSpec.Builder(
                alias,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
            )
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(AES_KEY_SIZE_BITS)
                .setUserAuthenticationRequired(false)
                .setRandomizedEncryptionRequired(true)
                .build(),
        )
        return generator.generateKey()
    }

    fun deleteKey(alias: String) {
        runCatching { keyStore().deleteEntry(alias) }
    }
}
