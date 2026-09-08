package com.ss.medrecord.ui.navigation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ss.medrecord.core.biometric.BiometricGate
import com.ss.medrecord.domain.model.AppearanceMode
import com.ss.medrecord.domain.session.AppearanceManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

/**
 * State that belongs to the window rather than to any screen: which colour
 * scheme to draw in, and whether the app lock is currently standing in front
 * of everything else.
 *
 * It is separate from [SessionViewModel] because the two answer different
 * questions. The session decides *which* screens the user may see; this
 * decides how they are painted and whether they are shown at all yet.
 */
@HiltViewModel
class AppShellViewModel @Inject constructor(
    private val appearanceManager: AppearanceManager,
    private val biometricGate: BiometricGate,
) : ViewModel() {

    val appearanceMode: StateFlow<AppearanceMode> = appearanceManager.appearanceMode

    private val unlocked = MutableStateFlow(false)

    /**
     * True while the lock screen should cover the app.
     *
     * The device's own capability is part of the condition, not just the
     * stored preference. If the sensor is gone or the enrolment was wiped, a
     * stored `true` would otherwise put an unpassable door in front of the
     * user's records.
     */
    val locked: StateFlow<Boolean> =
        combine(appearanceManager.biometricLockEnabled, unlocked) { enabled, isUnlocked ->
            enabled && !isUnlocked && biometricGate.isAvailable
        }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = false,
        )

    private val _lockError = MutableStateFlow<String?>(null)
    val lockError: StateFlow<String?> = _lockError.asStateFlow()

    fun onUnlocked() {
        _lockError.value = null
        unlocked.value = true
    }

    fun onUnlockFailed(message: String?) {
        _lockError.value = message
    }

    /**
     * Called when the app leaves the foreground.
     *
     * Re-locking on background rather than only at launch is the whole point:
     * a lock that is satisfied once per process would be open all day on a
     * phone that is never restarted, which is exactly the phone this protects.
     */
    fun onBackgrounded() {
        unlocked.value = false
    }
}
