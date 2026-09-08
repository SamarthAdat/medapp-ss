package com.ss.medrecord.domain.session

import com.ss.medrecord.data.local.datastore.AppearanceStore
import com.ss.medrecord.di.ApplicationScope
import com.ss.medrecord.domain.model.AppearanceMode
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

/**
 * The app's appearance and lock settings, held for the life of the process.
 *
 * A singleton rather than per-ViewModel state because two screens read it at
 * once: the activity, which decides which colour scheme to draw, and Settings,
 * which changes it. If each held its own copy, changing the setting would
 * repaint Settings and leave every other screen behind it in the old theme
 * until it was recreated.
 *
 * [appearanceMode] starts at [AppearanceMode.FOLLOW_SYSTEM] rather than
 * suspending for the stored value. Waiting would mean holding the first frame,
 * and the cost of getting it wrong is one repaint a few milliseconds in - much
 * cheaper than a blank window.
 */
@Singleton
class AppearanceManager @Inject constructor(
    private val appearanceStore: AppearanceStore,
    @param:ApplicationScope private val scope: CoroutineScope,
) {

    val appearanceMode: StateFlow<AppearanceMode> = appearanceStore.appearanceMode
        .stateIn(
            scope = scope,
            started = SharingStarted.Eagerly,
            initialValue = AppearanceMode.FOLLOW_SYSTEM,
        )

    val biometricLockEnabled: StateFlow<Boolean> = appearanceStore.biometricLockEnabled
        .stateIn(
            scope = scope,
            started = SharingStarted.Eagerly,
            initialValue = false,
        )

    fun setAppearanceMode(mode: AppearanceMode) {
        scope.launch { appearanceStore.setAppearanceMode(mode) }
    }

    fun setBiometricLockEnabled(enabled: Boolean) {
        scope.launch { appearanceStore.setBiometricLockEnabled(enabled) }
    }
}
