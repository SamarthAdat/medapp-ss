package com.ss.medrecord.ui.feature.home

import com.ss.medrecord.core.connectivity.NetworkStatus
import com.ss.medrecord.core.ui.UiEffect
import com.ss.medrecord.core.ui.UiEvent
import com.ss.medrecord.core.ui.UiState
import com.ss.medrecord.domain.model.Patient

/**
 * The contract every feature follows: one state class, one sealed event
 * hierarchy, one sealed effect hierarchy. Phase 7 fills this out with the real
 * dashboard aggregates; for now it carries the active-patient context and
 * connectivity.
 */
data class HomeUiState(
    val isLoading: Boolean = false,
    val networkStatus: NetworkStatus = NetworkStatus.UNAVAILABLE,
    val activePatient: Patient? = null,
    val patientCount: Int = 0,
) : UiState {
    val isOnline: Boolean get() = networkStatus == NetworkStatus.AVAILABLE

    /** No profiles yet, so the dashboard prompts for the first one instead. */
    val needsFirstPatient: Boolean get() = patientCount == 0
}

sealed interface HomeEvent : UiEvent {
    data object OpenPatients : HomeEvent
    data object OpenSettings : HomeEvent
    data object SwitchPatient : HomeEvent
    data object AddFirstPatient : HomeEvent
}

sealed interface HomeEffect : UiEffect {
    data object NavigateToPatients : HomeEffect
    data object NavigateToSettings : HomeEffect
    data object NavigateToAddPatient : HomeEffect
}
