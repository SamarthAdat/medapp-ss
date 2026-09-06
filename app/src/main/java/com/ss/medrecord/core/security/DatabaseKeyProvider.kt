package com.ss.medrecord.core.security

import android.content.Context
import android.util.Base64
import dagger.hilt.android.qualifiers.ApplicationContext
import java.security.GeneralSecurityException
import java.security.SecureRandom
import javax.crypto.Cipher
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
        KeystoreAesKeys.deleteKey(KEY_ALIAS)
    }

    private fun wrap(passphrase: ByteArray): String {
        val cipher = Cipher.getInstance(TRANSFORMATION).apply {
            init(Cipher.ENCRYPT_MODE, KeystoreAesKeys.getOrCreateKey(KEY_ALIAS))
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
        val key = KeystoreAesKeys.getKey(KEY_ALIAS)
            ?: throw GeneralSecurityException("Keystore entry $KEY_ALIAS is missing")
        val cipher = Cipher.getInstance(TRANSFORMATION).apply {
            init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(GCM_TAG_LENGTH_BITS, iv))
        }
        return cipher.doFinal(ciphertext)
    }

    private companion object {
        const val KEY_ALIAS = "medrecord_db_master_key"
        const val TRANSFORMATION = KeystoreAesKeys.TRANSFORMATION
        const val PREFS_NAME = "medrecord_secure_prefs"
        const val KEY_WRAPPED_PASSPHRASE = "wrapped_db_passphrase"
        const val PASSPHRASE_LENGTH_BYTES = 32
        const val GCM_IV_LENGTH_BYTES = KeystoreAesKeys.GCM_IV_LENGTH_BYTES
        const val GCM_TAG_LENGTH_BITS = KeystoreAesKeys.GCM_TAG_LENGTH_BITS
    }
}
