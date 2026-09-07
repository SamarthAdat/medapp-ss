package com.ss.medrecord.ui.feature.facility.nearby

import androidx.lifecycle.viewModelScope
import com.ss.medrecord.core.common.DataResult
import com.ss.medrecord.core.location.LocationProvider
import com.ss.medrecord.core.ui.BaseViewModel
import com.ss.medrecord.core.ui.toUserMessage
import com.ss.medrecord.domain.model.AuthSession
import com.ss.medrecord.domain.model.NearbyPlace
import com.ss.medrecord.domain.repository.FacilityRepository
import com.ss.medrecord.domain.repository.PlacesRepository
import com.ss.medrecord.domain.session.SessionManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class NearbyViewModel @Inject constructor(
    private val placesRepository: PlacesRepository,
    private val facilityRepository: FacilityRepository,
    private val locationProvider: LocationProvider,
    private val sessionManager: SessionManager,
) : BaseViewModel<NearbyUiState, NearbyEvent, NearbyEffect>(NearbyUiState()) {

    init {
        setState { copy(isMapsConfigured = placesRepository.isConfigured) }
        refreshPermissionState()
    }

    override fun onEvent(event: NearbyEvent) {
        when (event) {
            is NearbyEvent.SearchTypeChanged -> {
                setState { copy(searchType = event.type) }
                // Changing what you are looking for is itself the request; making
                // the user tap Search again to see it is a wasted step.
                if (currentState.canSearch) search()
            }

            NearbyEvent.SearchClicked -> search()
            is NearbyEvent.SaveClicked -> save(event.place)

            is NearbyEvent.PlaceClicked -> sendEffect(NearbyEffect.OpenDirections(event.place))

            NearbyEvent.GrantLocationClicked ->
                sendEffect(NearbyEffect.RequestLocationPermission)

            NearbyEvent.EnableLocationClicked -> sendEffect(NearbyEffect.OpenLocationSettings)
            NearbyEvent.ToggleMapView -> setState { copy(isMapView = !isMapView) }

            NearbyEvent.PermissionsRechecked -> {
                val wasBlocked = !currentState.hasLocationPermission
                refreshPermissionState()
                // Just granted, and nothing has been searched yet: run the search
                // the user was trying to run when they were interrupted.
                if (wasBlocked && currentState.canSearch && !currentState.hasSearched) search()
            }

            NearbyEvent.BackClicked -> sendEffect(NearbyEffect.NavigateBack)
        }
    }

    private fun refreshPermissionState() {
        setState {
            copy(
                hasLocationPermission = locationProvider.hasPermission(),
                isLocationEnabled = locationProvider.isLocationEnabled(),
            )
        }
    }

    private fun search() {
        if (currentState.isSearching) return

        viewModelScope.launch {
            setState { copy(isSearching = true, errorMessage = null) }

            val origin = locationProvider.currentLocation()
            if (origin == null) {
                // Re-read rather than guess: the fix could have failed because
                // permission was revoked mid-session or location was switched
                // off, and the screen should now say which.
                refreshPermissionState()
                setState {
                    copy(
                        isSearching = false,
                        errorMessage = "Could not get your location. Try again in a moment.",
                    )
                }
                return@launch
            }

            when (
                val result = placesRepository.searchNearby(
                    origin = origin,
                    type = currentState.searchType,
                    radiusMetres = SEARCH_RADIUS_METRES,
                )
            ) {
                is DataResult.Success -> setState {
                    copy(
                        origin = origin,
                        results = result.data,
                        isSearching = false,
                        hasSearched = true,
                    )
                }

                is DataResult.Error -> setState {
                    copy(
                        origin = origin,
                        isSearching = false,
                        hasSearched = true,
                        errorMessage = result.error.toUserMessage(),
                    )
                }
            }
        }
    }

    /**
     * Saving is the moment a search result becomes a record on this account, so
     * it is the only point at which the phone number is worth fetching - and
     * the only point at which anything from Places is written down.
     */
    private fun save(place: NearbyPlace) {
        val userId = (sessionManager.session.value as? AuthSession.Authenticated)?.userId
        if (userId == null) {
            sendEffect(NearbyEffect.ShowMessage("Sign in before saving a clinic."))
            return
        }
        if (currentState.savingPlaceId != null) return

        viewModelScope.launch {
            setState { copy(savingPlaceId = place.placeId) }

            // Best effort: a missing phone number is not a reason to refuse to
            // save the clinic the user just chose.
            val detailed = when (val details = placesRepository.details(place)) {
                is DataResult.Success -> details.data
                is DataResult.Error -> place
            }

            when (val created = facilityRepository.createFacility(detailed.toFacility(userId))) {
                is DataResult.Success -> {
                    setState { copy(savingPlaceId = null) }
                    sendEffect(NearbyEffect.ShowMessage("${detailed.name} saved"))
                    sendEffect(NearbyEffect.NavigateToFacility(created.data))
                }

                is DataResult.Error -> {
                    setState { copy(savingPlaceId = null) }
                    sendEffect(NearbyEffect.ShowMessage(created.error.toUserMessage()))
                }
            }
        }
    }

    private companion object {
        /**
         * Wide enough to find something in a rural area, narrow enough that an
         * urban search is not twenty results from three suburbs away.
         */
        const val SEARCH_RADIUS_METRES = 5_000.0
    }
}
