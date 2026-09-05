package com.ss.medrecord.data.local.datastore

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.ss.medrecord.core.common.AppConstants
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

private val Context.settingsDataStore: DataStore<Preferences> by preferencesDataStore(
    name = AppConstants.DATASTORE_SETTINGS_NAME,
)

/**
 * Remembers which patient the user last selected, so the choice survives app
 * restarts (spec section 5.2).
 *
 * The owning account is stored alongside the id. Only a patient id is a
 * meaningless key on a shared device - if a different account signs in before
 * the store is cleared, an id from the previous holder would otherwise be
 * treated as this account's selection. Reads return null unless the stored
 * account matches the caller.
 *
 * Nothing identifying goes in here: an opaque id pair, in app-private storage.
 * Patient names and clinical fields live only in the encrypted database.
 */
@Singleton
class ActivePatientStore @Inject constructor(
    @param:ApplicationContext private val context: Context,
) {

    fun observeActivePatientId(userId: String): Flow<String?> =
        context.settingsDataStore.data.map { prefs ->
            if (prefs[KEY_OWNER_USER_ID] == userId) prefs[KEY_ACTIVE_PATIENT_ID] else null
        }

    suspend fun setActivePatient(userId: String, patientId: String?) {
        context.settingsDataStore.edit { prefs ->
            if (patientId == null) {
                prefs.remove(KEY_ACTIVE_PATIENT_ID)
                prefs.remove(KEY_OWNER_USER_ID)
            } else {
                prefs[KEY_ACTIVE_PATIENT_ID] = patientId
                prefs[KEY_OWNER_USER_ID] = userId
            }
        }
    }

    /** Called on sign-out so the next account starts with no selection. */
    suspend fun clear() {
        context.settingsDataStore.edit { prefs ->
            prefs.remove(KEY_ACTIVE_PATIENT_ID)
            prefs.remove(KEY_OWNER_USER_ID)
        }
    }

    private companion object {
        val KEY_ACTIVE_PATIENT_ID = stringPreferencesKey("active_patient_id")
        val KEY_OWNER_USER_ID = stringPreferencesKey("active_patient_owner")
    }
}
