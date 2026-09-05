package com.ss.medrecord.ui.feature.visit.list

import com.ss.medrecord.core.ui.UiEffect
import com.ss.medrecord.core.ui.UiEvent
import com.ss.medrecord.core.ui.UiState
import com.ss.medrecord.domain.model.Patient
import com.ss.medrecord.domain.model.VisitWithFacility

/**
 * The visit list for the active patient (spec section 5.4). Grouping by
 * facility is offered alongside the default date order, since "everything from
 * this clinic" and "what happened recently" are both common questions.
 */
data class VisitListUiState(
    val activePatient: Patient? = null,
    val visits: List<VisitWithFacility> = emptyList(),
    val groupByFacility: Boolean = false,
    val isLoading: Boolean = true,
    val pendingDeletion: VisitWithFacility? = null,
) : UiState {
    val isEmpty: Boolean get() = !isLoading && visits.isEmpty()

    /** Facility name to its visits, newest first within each group. */
    val groupedByFacility: List<Pair<String, List<VisitWithFacility>>>
        get() = visits
            .groupBy { it.facilityName }
            .toList()
            .sortedBy { (name, _) -> name.lowercase() }

    val hasNoPatient: Boolean get() = !isLoading && activePatient == null
}

sealed interface VisitListEvent : UiEvent {
    data class VisitClicked(val visit: VisitWithFacility) : VisitListEvent
    data class EditVisit(val visit: VisitWithFacility) : VisitListEvent
    data class DeleteRequested(val visit: VisitWithFacility) : VisitListEvent
    data object DeleteConfirmed : VisitListEvent
    data object DeleteDismissed : VisitListEvent
    data object ToggleGrouping : VisitListEvent
    data object AddVisit : VisitListEvent
    data object BackClicked : VisitListEvent
}

sealed interface VisitListEffect : UiEffect {
    data object NavigateBack : VisitListEffect
    data class NavigateToDetail(val visitId: String) : VisitListEffect
    data class NavigateToEdit(val visitId: String?) : VisitListEffect
    data class ShowMessage(val message: String) : VisitListEffect
}
