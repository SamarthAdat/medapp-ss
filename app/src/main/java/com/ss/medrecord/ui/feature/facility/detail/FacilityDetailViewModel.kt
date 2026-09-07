package com.ss.medrecord.ui.feature.facility.detail

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import com.ss.medrecord.core.ui.BaseViewModel
import com.ss.medrecord.domain.repository.FacilityRepository
import com.ss.medrecord.domain.repository.VisitRepository
import com.ss.medrecord.ui.navigation.FacilityDetailDestination
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import javax.inject.Inject

@HiltViewModel
class FacilityDetailViewModel @Inject constructor(
    facilityRepository: FacilityRepository,
    visitRepository: VisitRepository,
    savedStateHandle: SavedStateHandle,
) : BaseViewModel<FacilityDetailUiState, FacilityDetailEvent, FacilityDetailEffect>(
    FacilityDetailUiState(),
) {

    private val route = savedStateHandle.toRoute<FacilityDetailDestination>()

    init {
        facilityRepository.observeFacility(route.facilityId)
            .onEach { facility -> setState { copy(facility = facility, isLoading = false) } }
            .launchIn(viewModelScope)

        visitRepository.observeVisitsAtFacility(route.facilityId)
            .onEach { visits -> setState { copy(visits = visits) } }
            .launchIn(viewModelScope)
    }

    override fun onEvent(event: FacilityDetailEvent) {
        when (event) {
            FacilityDetailEvent.BackClicked -> sendEffect(FacilityDetailEffect.NavigateBack)

            FacilityDetailEvent.DirectionsClicked -> {
                if (currentState.hasLocation) {
                    sendEffect(FacilityDetailEffect.OpenDirections)
                } else {
                    // A facility typed into the add-visit form has a name and
                    // nothing else. Saying so beats a button that does nothing.
                    sendEffect(
                        FacilityDetailEffect.ShowMessage(
                            "No location saved for this place. Add it from Find nearby.",
                        ),
                    )
                }
            }

            FacilityDetailEvent.CallClicked -> {
                val phone = currentState.phone
                if (phone == null) {
                    sendEffect(FacilityDetailEffect.ShowMessage("No phone number saved."))
                } else {
                    sendEffect(FacilityDetailEffect.Dial(phone))
                }
            }

            is FacilityDetailEvent.VisitClicked ->
                sendEffect(FacilityDetailEffect.NavigateToVisit(event.visit.visitId))
        }
    }
}
