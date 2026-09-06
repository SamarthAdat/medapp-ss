package com.ss.medrecord.core.file

import android.content.Context
import android.os.ParcelFileDescriptor
import android.util.Log
import com.ss.medrecord.core.security.KeystoreAesKeys
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.nio.ByteBuffer
import java.security.GeneralSecurityException
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec
import javax.inject.Inject
import javax.inject.Singleton

private const val TAG = "EncryptedFileStore"

/**
 * The encrypted local cache for report files (spec section 9.2).
 *
 * Envelope encryption, for the same reason the database passphrase uses it: a
 * Keystore key is a handle to hardware, not a cipher you can push megabytes
 * through. Every byte handed to a Keystore cipher crosses a Binder boundary in
 * chunks, which is slow at 2 MB and - as this app found on a real device -
 * crashes the process outright. So the Keystore key only ever sees 32 bytes.
 *
 * Each file gets its own random data key. The file is encrypted with that key
 * in-process, and only the data key is sealed with the hardware-backed Keystore
 * key. The security property is unchanged: without the Keystore key, which
 * cannot be extracted from the device, the data key cannot be unwrapped and the
 * file cannot be read.
 *
 * Both layers are AES-256/GCM, so a tampered or truncated file fails to open
 * rather than decrypting into something plausible.
 *
 * File layout:
 *   [1]  format version
 *   [2]  wrapped data key length, big-endian
 *   [12] Keystore GCM IV
 *   [n]  data key, sealed with the Keystore key
 *   [12] content GCM IV
 *   [..] content, encrypted with the data key
 */
@Singleton
class EncryptedFileStore @Inject constructor(
    @param:ApplicationContext private val context: Context,
) {

    private val reportsDir: File
        get() = File(context.filesDir, REPORTS_DIR).apply { mkdirs() }

    /** Encrypts [plaintext] under [reportId] and returns the stored path. */
    fun write(reportId: String, plaintext: ByteArray): String {
        val dataKey = ByteArray(DATA_KEY_LENGTH_BYTES).also { SecureRandom().nextBytes(it) }

        val wrapCipher = Cipher.getInstance(KeystoreAesKeys.TRANSFORMATION).apply {
            init(Cipher.ENCRYPT_MODE, KeystoreAesKeys.getOrCreateKey(KEY_ALIAS))
        }
        val wrappedKey = wrapCipher.doFinal(dataKey)

        val contentCipher = Cipher.getInstance(KeystoreAesKeys.TRANSFORMATION).apply {
            init(Cipher.ENCRYPT_MODE, SecretKeySpec(dataKey, AES))
        }
        val ciphertext = contentCipher.doFinal(plaintext)
        // The plaintext key does not outlive the write.
        dataKey.fill(0)

        val blob = ByteBuffer
            .allocate(HEADER_LENGTH_BYTES + wrappedKey.size + IV_LENGTH + ciphertext.size)
            .put(FORMAT_VERSION)
            .putShort(wrappedKey.size.toShort())
            .put(wrapCipher.iv)
            .put(wrappedKey)
            .put(contentCipher.iv)
            .put(ciphertext)
            .array()

        val target = File(reportsDir, "$reportId$EXTENSION")
        target.writeBytes(blob)
        return target.absolutePath
    }

    /** Null when the file is gone or can no longer be decrypted. */
    fun read(path: String): ByteArray? {
        val file = File(path)
        if (!file.exists()) return null
        return try {
            decrypt(file.readBytes())
        } catch (e: GeneralSecurityException) {
            // Keystore key replaced (device restore, keystore reset) or the file
            // was tampered with. Either way the bytes are unrecoverable; the
            // caller re-downloads from Cloud Storage.
            Log.w(TAG, "Could not decrypt $path", e)
            null
        } catch (e: IllegalArgumentException) {
            Log.w(TAG, "Malformed encrypted report at $path", e)
            null
        }
    }

    fun exists(path: String?): Boolean = path != null && File(path).exists()

    fun delete(path: String?) {
        if (path == null) return
        runCatching { File(path).delete() }
    }

    /** Drops every cached report file. Called on sign-out. */
    fun deleteAll() {
        runCatching { reportsDir.deleteRecursively() }
    }

    /**
     * A read-only descriptor onto the decrypted bytes, for APIs that insist on
     * a real file - [android.graphics.pdf.PdfRenderer] is the only one here,
     * because it seeks and so cannot be fed a pipe or a byte array.
     *
     * The plaintext is written to the cache directory and then immediately
     * unlinked while this descriptor holds it open. On Linux that keeps the
     * inode alive for the reader and removes it from the directory at the same
     * instant: nothing else can open it by name, and it is reclaimed even if
     * the process is killed mid-render. The window in which a decrypted medical
     * report exists as a nameable file is the few milliseconds between those
     * two calls.
     */
    fun openTransientPlaintext(path: String): ParcelFileDescriptor? {
        val plaintext = read(path) ?: return null
        val temp = File.createTempFile("view", ".tmp", context.cacheDir)
        return try {
            temp.writeBytes(plaintext)
            ParcelFileDescriptor.open(temp, ParcelFileDescriptor.MODE_READ_ONLY)
        } catch (e: Exception) {
            Log.w(TAG, "Could not open transient plaintext", e)
            null
        } finally {
            // Unlinked whether or not the open succeeded, so a failure cannot
            // leave a decrypted report sitting in the cache directory.
            temp.delete()
        }
    }

    private fun decrypt(blob: ByteArray): ByteArray {
        val buffer = ByteBuffer.wrap(blob)
        require(blob.size > HEADER_LENGTH_BYTES + IV_LENGTH) { "Encrypted report is truncated" }

        val version = buffer.get()
        require(version == FORMAT_VERSION) { "Unsupported report format version $version" }

        val wrappedKeyLength = buffer.short.toInt()
        require(wrappedKeyLength in 1..MAX_WRAPPED_KEY_LENGTH) {
            "Implausible wrapped key length $wrappedKeyLength"
        }
        require(buffer.remaining() >= wrappedKeyLength + IV_LENGTH) {
            "Encrypted report is truncated"
        }

        val keystoreIv = ByteArray(IV_LENGTH).also { buffer.get(it) }
        val wrappedKey = ByteArray(wrappedKeyLength).also { buffer.get(it) }
        val contentIv = ByteArray(IV_LENGTH).also { buffer.get(it) }
        val ciphertext = ByteArray(buffer.remaining()).also { buffer.get(it) }

        val keystoreKey = KeystoreAesKeys.getKey(KEY_ALIAS)
            ?: throw GeneralSecurityException("Keystore entry $KEY_ALIAS is missing")
        val unwrapCipher = Cipher.getInstance(KeystoreAesKeys.TRANSFORMATION).apply {
            init(Cipher.DECRYPT_MODE, keystoreKey, gcmSpec(keystoreIv))
        }
        val dataKey = unwrapCipher.doFinal(wrappedKey)

        return try {
            Cipher.getInstance(KeystoreAesKeys.TRANSFORMATION)
                .apply { init(Cipher.DECRYPT_MODE, SecretKeySpec(dataKey, AES), gcmSpec(contentIv)) }
                .doFinal(ciphertext)
        } finally {
            dataKey.fill(0)
        }
    }

    private fun gcmSpec(iv: ByteArray) =
        GCMParameterSpec(KeystoreAesKeys.GCM_TAG_LENGTH_BITS, iv)

    private companion object {
        const val KEY_ALIAS = "medrecord_file_master_key"
        const val REPORTS_DIR = "reports"
        const val EXTENSION = ".enc"
        const val AES = "AES"

        const val FORMAT_VERSION: Byte = 1
        const val DATA_KEY_LENGTH_BYTES = 32
        const val IV_LENGTH = KeystoreAesKeys.GCM_IV_LENGTH_BYTES

        /** version byte + length short + Keystore IV. */
        const val HEADER_LENGTH_BYTES = 1 + 2 + IV_LENGTH

        /** A 32-byte key plus a GCM tag; anything larger is a corrupt header. */
        const val MAX_WRAPPED_KEY_LENGTH = 128
    }
}
