package com.ss.medrecord.ui.feature.home

import com.ss.medrecord.core.connectivity.NetworkStatus
import com.ss.medrecord.core.ui.UiEffect
import com.ss.medrecord.core.ui.UiEvent
import com.ss.medrecord.core.ui.UiState

/**
 * The contract every feature follows: one state class, one sealed event
 * hierarchy, one sealed effect hierarchy. Phase 7 fills this out with the real
 * dashboard aggregates; for now it carries just enough to exercise the wiring.
 */
data class HomeUiState(
    val isLoading: Boolean = false,
    val networkStatus: NetworkStatus = NetworkStatus.UNAVAILABLE,
) : UiState {
    val isOnline: Boolean get() = networkStatus == NetworkStatus.AVAILABLE
}

sealed interface HomeEvent : UiEvent {
    data object OpenPatients : HomeEvent
    data object OpenSettings : HomeEvent
}

sealed interface HomeEffect : UiEffect {
    data object NavigateToPatients : HomeEffect
    data object NavigateToSettings : HomeEffect
}
