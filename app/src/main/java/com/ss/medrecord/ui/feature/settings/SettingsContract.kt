package com.ss.medrecord.ui.feature.settings

import com.ss.medrecord.core.ui.UiEffect
import com.ss.medrecord.core.ui.UiEvent
import com.ss.medrecord.core.ui.UiState

/**
 * Minimal for now: enough to show who is signed in and to sign out. Phase 9
 * fills this out with notification and sync preferences, data export, consent
 * history and the right-to-erasure flow.
 */
data class SettingsUiState(
    val name: String = "",
    val email: String = "",
    val consentVersion: Int? = null,
    val isSigningOut: Boolean = false,
) : UiState

sealed interface SettingsEvent : UiEvent {
    data object SignOutClicked : SettingsEvent
    data object BackClicked : SettingsEvent
}

sealed interface SettingsEffect : UiEffect {
    data object NavigateBack : SettingsEffect
}
