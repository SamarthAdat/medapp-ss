package com.ss.medrecord.ui.feature.facility.list

import androidx.lifecycle.viewModelScope
import com.ss.medrecord.core.ui.BaseViewModel
import com.ss.medrecord.domain.model.AuthSession
import com.ss.medrecord.domain.repository.FacilityRepository
import com.ss.medrecord.domain.session.SessionManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import javax.inject.Inject

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class FacilityListViewModel @Inject constructor(
    facilityRepository: FacilityRepository,
    sessionManager: SessionManager,
) : BaseViewModel<FacilityListUiState, FacilityListEvent, FacilityListEffect>(
    FacilityListUiState(),
) {

    init {
        sessionManager.session
            .flatMapLatest { session ->
                when (session) {
                    is AuthSession.Authenticated ->
                        facilityRepository.observeFacilities(session.userId)

                    else -> flowOf(emptyList())
                }
            }
            .onEach { facilities ->
                setState {
                    copy(
                        facilities = facilities,
                        isLoading = false,
                        // A map view left showing nothing after the last mapped
                        // facility is filtered away reads as a broken screen.
                        isMapView = isMapView && facilities.any { it.hasLocation },
                    )
                }
            }
            .launchIn(viewModelScope)
    }

    override fun onEvent(event: FacilityListEvent) {
        when (event) {
            is FacilityListEvent.FacilityClicked ->
                sendEffect(FacilityListEffect.NavigateToDetail(event.facility.facilityId))

            is FacilityListEvent.QueryChanged -> setState { copy(query = event.value) }
            is FacilityListEvent.TypeFilterChanged -> setState { copy(typeFilter = event.type) }
            FacilityListEvent.ToggleMapView -> setState { copy(isMapView = !isMapView) }
            FacilityListEvent.FindNearbyClicked ->
                sendEffect(FacilityListEffect.NavigateToNearby)

            FacilityListEvent.BackClicked -> sendEffect(FacilityListEffect.NavigateBack)
        }
    }
}
