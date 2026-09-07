package com.ss.medrecord.ui.feature.facility.list

import com.ss.medrecord.core.ui.UiEffect
import com.ss.medrecord.core.ui.UiEvent
import com.ss.medrecord.core.ui.UiState
import com.ss.medrecord.domain.model.Facility
import com.ss.medrecord.domain.model.FacilityType

/**
 * Saved clinics and hospitals (spec section 5.7).
 *
 * Account-scoped rather than per-patient: one family uses the same clinic, and
 * duplicating it per profile would make "visit history at this facility"
 * unanswerable.
 */
data class FacilityListUiState(
    val facilities: List<Facility> = emptyList(),
    val typeFilter: FacilityType? = null,
    val query: String = "",
    val isMapView: Boolean = false,
    val isLoading: Boolean = true,
) : UiState {

    val visible: List<Facility>
        get() = facilities.filter { facility ->
            (typeFilter == null || facility.type == typeFilter) &&
                (query.isBlank() || facility.name.contains(query.trim(), ignoreCase = true))
        }

    /** Only facilities that were saved from the map can be put on one. */
    val mappable: List<Facility> get() = visible.filter { it.hasLocation }

    val isEmpty: Boolean get() = !isLoading && visible.isEmpty()

    val hasFiltersApplied: Boolean get() = typeFilter != null || query.isNotBlank()

    /**
     * The map is offered only once something can appear on it. A toggle that
     * leads to an empty map is a toggle that looks broken.
     */
    val canShowMap: Boolean get() = mappable.isNotEmpty()
}

sealed interface FacilityListEvent : UiEvent {
    data class FacilityClicked(val facility: Facility) : FacilityListEvent
    data class QueryChanged(val value: String) : FacilityListEvent
    data class TypeFilterChanged(val type: FacilityType?) : FacilityListEvent
    data object ToggleMapView : FacilityListEvent
    data object FindNearbyClicked : FacilityListEvent
    data object BackClicked : FacilityListEvent
}

sealed interface FacilityListEffect : UiEffect {
    data object NavigateBack : FacilityListEffect
    data class NavigateToDetail(val facilityId: String) : FacilityListEffect
    data object NavigateToNearby : FacilityListEffect
}
