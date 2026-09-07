package com.ss.medrecord.ui.feature.facility.nearby

import com.ss.medrecord.core.location.Coordinates
import com.ss.medrecord.core.ui.UiEffect
import com.ss.medrecord.core.ui.UiEvent
import com.ss.medrecord.core.ui.UiState
import com.ss.medrecord.domain.model.NearbyPlace
import com.ss.medrecord.domain.model.PlaceSearchType

/**
 * Nearby facility search (spec section 5.10).
 *
 * The only screen in the app with no offline story, and the state says so out
 * loud rather than showing an empty list: everything else keeps working with no
 * network, and a user who has learnt that would otherwise read "no results" as
 * "there are no clinics near me".
 *
 * [origin] is held only for the lifetime of this screen and is never persisted.
 */
data class NearbyUiState(
    val origin: Coordinates? = null,
    val results: List<NearbyPlace> = emptyList(),
    val searchType: PlaceSearchType = PlaceSearchType.HOSPITAL,
    val isSearching: Boolean = false,
    val isMapView: Boolean = false,
    val hasSearched: Boolean = false,
    val isMapsConfigured: Boolean = true,
    val hasLocationPermission: Boolean = false,
    val isLocationEnabled: Boolean = true,
    val savingPlaceId: String? = null,
    val errorMessage: String? = null,
) : UiState {

    val canSearch: Boolean
        get() = isMapsConfigured && hasLocationPermission && isLocationEnabled && !isSearching

    val isEmpty: Boolean get() = hasSearched && !isSearching && results.isEmpty()

    /** Only results Google gave coordinates for can be pinned. */
    val mappable: List<NearbyPlace> get() = results.filter { it.coordinates != null }

    /**
     * Which of the three possible blockers to explain. Ordered by what the user
     * has to fix first: an unconfigured build is not something they can fix at
     * all, so it is said plainly and the other prompts are suppressed.
     */
    val blocker: NearbyBlocker?
        get() = when {
            !isMapsConfigured -> NearbyBlocker.NOT_CONFIGURED
            !hasLocationPermission -> NearbyBlocker.NO_PERMISSION
            !isLocationEnabled -> NearbyBlocker.LOCATION_OFF
            else -> null
        }
}

enum class NearbyBlocker { NOT_CONFIGURED, NO_PERMISSION, LOCATION_OFF }

sealed interface NearbyEvent : UiEvent {
    data class SearchTypeChanged(val type: PlaceSearchType) : NearbyEvent
    data object SearchClicked : NearbyEvent
    data class SaveClicked(val place: NearbyPlace) : NearbyEvent
    data class PlaceClicked(val place: NearbyPlace) : NearbyEvent
    data object GrantLocationClicked : NearbyEvent
    data object EnableLocationClicked : NearbyEvent
    data object ToggleMapView : NearbyEvent
    /** Re-read after a permission dialog or a trip to settings. */
    data object PermissionsRechecked : NearbyEvent
    data object BackClicked : NearbyEvent
}

sealed interface NearbyEffect : UiEffect {
    data object NavigateBack : NearbyEffect
    data class NavigateToFacility(val facilityId: String) : NearbyEffect
    data object RequestLocationPermission : NearbyEffect
    data object OpenLocationSettings : NearbyEffect
    data class OpenDirections(val place: NearbyPlace) : NearbyEffect
    data class ShowMessage(val message: String) : NearbyEffect
}
