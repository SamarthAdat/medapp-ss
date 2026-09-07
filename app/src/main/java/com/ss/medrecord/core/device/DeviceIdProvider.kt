package com.ss.medrecord.core.device

import android.content.Context
import androidx.core.content.edit
import dagger.hilt.android.qualifiers.ApplicationContext
import java.security.MessageDigest
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/**
 * A stable, non-identifying device label for audit entries.
 *
 * Deliberately not the Android ID or any hardware identifier: those are shared
 * across apps and effectively track the person, which is exactly what section
 * 9.1 rules out. This is a random value minted on first launch, hashed before
 * it ever leaves this class, and reset by uninstalling the app.
 */
@Singleton
class DeviceIdProvider @Inject constructor(
    @param:ApplicationContext private val context: Context,
) {

    val deviceIdHash: String by lazy { sha256(installId()) }

    @Synchronized
    private fun installId(): String {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.getString(KEY_INSTALL_ID, null)?.let { return it }
        val generated = UUID.randomUUID().toString()
        // commit, not apply: the value is returned and hashed into audit entries
        // on the next line, and an id that failed to persist would silently
        // become a different device in the trail after a restart.
        prefs.edit(commit = true) { putString(KEY_INSTALL_ID, generated) }
        return generated
    }

    private fun sha256(value: String): String =
        MessageDigest.getInstance("SHA-256")
            .digest(value.toByteArray())
            .joinToString(separator = "") { byte -> "%02x".format(byte) }

    private companion object {
        const val PREFS_NAME = "medrecord_device"
        const val KEY_INSTALL_ID = "install_id"
    }
}
