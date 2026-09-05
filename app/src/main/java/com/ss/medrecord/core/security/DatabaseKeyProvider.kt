package com.ss.medrecord.core.security

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import dagger.hilt.android.qualifiers.ApplicationContext
import java.security.GeneralSecurityException
import java.security.KeyStore
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Supplies the SQLCipher passphrase for the local database.
 *
 * The passphrase itself is 32 random bytes generated once on first launch. It is
 * never stored in the clear: it is sealed with an AES-256/GCM key that lives in
 * the Android Keystore, so the key material is held by hardware-backed storage
 * (StrongBox or TEE where available) and cannot be extracted from the device
 * even with root. Only the wrapped blob is written to app-private preferences.
 *
 * If the Keystore key is lost - factory reset, restore onto a different device,
 * or the user re-enrolling a lock screen on some OEMs - the wrapped passphrase
 * can no longer be opened. That is a deliberate property: the local ciphertext
 * becomes unreadable to everyone, including us. [resetKeyMaterial] re-provisions
 * from scratch, which callers must pair with deleting the now-undecryptable
 * database file. Cloud sync is what makes that recoverable rather than fatal.
 */
@Singleton
class DatabaseKeyProvider @Inject constructor(
    @param:ApplicationContext private val context: Context,
) {

    private val prefs by lazy {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }

    /**
     * The database passphrase, provisioning it on first call.
     *
     * Returns a fresh array each time because SQLCipher zeroes the array it is
     * handed once the database is open.
     */
    @Synchronized
    fun getOrCreatePassphrase(): ByteArray {
        val stored = prefs.getString(KEY_WRAPPED_PASSPHRASE, null)
        if (stored != null) {
            return unwrap(stored)
        }
        val passphrase = ByteArray(PASSPHRASE_LENGTH_BYTES).also { SecureRandom().nextBytes(it) }
        prefs.edit().putString(KEY_WRAPPED_PASSPHRASE, wrap(passphrase)).commit()
        return passphrase
    }

    /**
     * Discards the Keystore key and the wrapped passphrase. The existing database
     * file is unreadable afterwards and must be deleted by the caller.
     */
    @Synchronized
    fun resetKeyMaterial() {
        prefs.edit().remove(KEY_WRAPPED_PASSPHRASE).commit()
        runCatching { androidKeyStore().deleteEntry(KEY_ALIAS) }
    }

    private fun wrap(passphrase: ByteArray): String {
        val cipher = Cipher.getInstance(TRANSFORMATION).apply {
            init(Cipher.ENCRYPT_MODE, getOrCreateKeystoreKey())
        }
        val iv = cipher.iv
        val ciphertext = cipher.doFinal(passphrase)
        // IV is not secret; it is prefixed so unwrap can recover it.
        return Base64.encodeToString(iv + ciphertext, Base64.NO_WRAP)
    }

    private fun unwrap(encoded: String): ByteArray {
        val blob = Base64.decode(encoded, Base64.NO_WRAP)
        require(blob.size > GCM_IV_LENGTH_BYTES) { "Wrapped passphrase is truncated" }
        val iv = blob.copyOfRange(0, GCM_IV_LENGTH_BYTES)
        val ciphertext = blob.copyOfRange(GCM_IV_LENGTH_BYTES, blob.size)
        val key = androidKeyStore().getKey(KEY_ALIAS, null) as? SecretKey
            ?: throw GeneralSecurityException("Keystore entry $KEY_ALIAS is missing")
        val cipher = Cipher.getInstance(TRANSFORMATION).apply {
            init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(GCM_TAG_LENGTH_BITS, iv))
        }
        return cipher.doFinal(ciphertext)
    }

    private fun getOrCreateKeystoreKey(): SecretKey {
        val keyStore = androidKeyStore()
        (keyStore.getKey(KEY_ALIAS, null) as? SecretKey)?.let { return it }

        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE)
        generator.init(
            KeyGenParameterSpec.Builder(
                KEY_ALIAS,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
            )
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(AES_KEY_SIZE_BITS)
                // Background sync must be able to open the database while the
                // device is locked, so the key is not gated on user auth.
                .setUserAuthenticationRequired(false)
                .setRandomizedEncryptionRequired(true)
                .build(),
        )
        return generator.generateKey()
    }

    private fun androidKeyStore(): KeyStore =
        KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }

    private companion object {
        const val ANDROID_KEYSTORE = "AndroidKeyStore"
        const val KEY_ALIAS = "medrecord_db_master_key"
        const val TRANSFORMATION = "AES/GCM/NoPadding"
        const val PREFS_NAME = "medrecord_secure_prefs"
        const val KEY_WRAPPED_PASSPHRASE = "wrapped_db_passphrase"
        const val PASSPHRASE_LENGTH_BYTES = 32
        const val GCM_IV_LENGTH_BYTES = 12
        const val GCM_TAG_LENGTH_BITS = 128
        const val AES_KEY_SIZE_BITS = 256
    }
}
