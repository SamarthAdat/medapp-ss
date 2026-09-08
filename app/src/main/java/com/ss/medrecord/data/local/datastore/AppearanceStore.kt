package com.ss.medrecord.data.local.datastore

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.ss.medrecord.core.common.AppConstants
import com.ss.medrecord.domain.model.AppearanceMode
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

private val Context.appearanceDataStore: DataStore<Preferences> by preferencesDataStore(
    name = AppConstants.DATASTORE_APPEARANCE_NAME,
)

/**
 * The two device-local preferences: which colour scheme to draw in, and
 * whether the app asks for a fingerprint before it opens.
 *
 * Deliberately not synced and deliberately not cleared on sign-out. Both are
 * properties of *this phone*, not of an account - syncing the app lock would
 * mean a second device silently inheriting a lock the user never set on it,
 * and syncing the theme would mean a tablet in a bright room following a
 * phone's night-time setting.
 *
 * Nothing clinical is stored here, which is why it sits in a plain
 * preferences file rather than the encrypted database.
 */
@Singleton
class AppearanceStore @Inject constructor(
    @param:ApplicationContext private val context: Context,
) {

    val appearanceMode: Flow<AppearanceMode> = context.appearanceDataStore.data.map { prefs ->
        AppearanceMode.fromStorage(prefs[KEY_APPEARANCE_MODE])
    }

    /**
     * Whether the user has asked for the app to be locked behind their
     * fingerprint or device credential.
     *
     * This is the user's *intent*. Whether the device can actually satisfy it
     * is a separate question answered at unlock time - hardware can be removed
     * from the equation by a factory reset or a wiped enrolment, and a stored
     * `true` must never be able to lock somebody out of their own records.
     */
    val biometricLockEnabled: Flow<Boolean> = context.appearanceDataStore.data.map { prefs ->
        prefs[KEY_BIOMETRIC_LOCK] == true
    }

    suspend fun setAppearanceMode(mode: AppearanceMode) {
        context.appearanceDataStore.edit { prefs ->
            prefs[KEY_APPEARANCE_MODE] = mode.name
        }
    }

    suspend fun setBiometricLockEnabled(enabled: Boolean) {
        context.appearanceDataStore.edit { prefs ->
            prefs[KEY_BIOMETRIC_LOCK] = enabled
        }
    }

    private companion object {
        val KEY_APPEARANCE_MODE = stringPreferencesKey("appearance_mode")
        val KEY_BIOMETRIC_LOCK = booleanPreferencesKey("biometric_lock_enabled")
    }
}
