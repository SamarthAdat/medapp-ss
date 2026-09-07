package com.ss.medrecord.ui.feature.facility.detail

import com.ss.medrecord.core.ui.UiEffect
import com.ss.medrecord.core.ui.UiEvent
import com.ss.medrecord.core.ui.UiState
import com.ss.medrecord.domain.model.Facility
import com.ss.medrecord.domain.model.Visit

/**
 * One clinic, and every visit to it (spec section 5.7).
 *
 * The visit list is across all patients on the account, which is the point:
 * "when did we last take anyone to this hospital" is a question a household
 * asks, and answering it per-profile would need the user to check three
 * profiles to find out.
 */
data class FacilityDetailUiState(
    val facility: Facility? = null,
    val visits: List<Visit> = emptyList(),
    val isLoading: Boolean = true,
) : UiState {
    val hasLocation: Boolean get() = facility?.hasLocation == true

    val phone: String? get() = facility?.phone?.takeIf { it.isNotBlank() }

    val address: String? get() = facility?.address?.takeIf { it.isNotBlank() }
}

sealed interface FacilityDetailEvent : UiEvent {
    data object DirectionsClicked : FacilityDetailEvent
    data object CallClicked : FacilityDetailEvent
    data class VisitClicked(val visit: Visit) : FacilityDetailEvent
    data object BackClicked : FacilityDetailEvent
}

sealed interface FacilityDetailEffect : UiEffect {
    data object NavigateBack : FacilityDetailEffect
    data class NavigateToVisit(val visitId: String) : FacilityDetailEffect
    data object OpenDirections : FacilityDetailEffect
    data class Dial(val phone: String) : FacilityDetailEffect
    data class ShowMessage(val message: String) : FacilityDetailEffect
}
