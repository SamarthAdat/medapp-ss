package com.ss.medrecord.ui.feature.home

import androidx.lifecycle.viewModelScope
import com.ss.medrecord.core.connectivity.ConnectivityObserver
import com.ss.medrecord.core.ui.BaseViewModel
import com.ss.medrecord.domain.session.ActivePatientManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import javax.inject.Inject

@HiltViewModel
class HomeViewModel @Inject constructor(
    connectivityObserver: ConnectivityObserver,
    activePatientManager: ActivePatientManager,
) : BaseViewModel<HomeUiState, HomeEvent, HomeEffect>(
    initialState = HomeUiState(networkStatus = connectivityObserver.currentStatus()),
) {

    init {
        connectivityObserver.status
            .onEach { status -> setState { copy(networkStatus = status) } }
            .launchIn(viewModelScope)

        combine(
            activePatientManager.activePatient,
            activePatientManager.patients,
        ) { active, all -> active to all.size }
            .onEach { (active, count) ->
                setState { copy(activePatient = active, patientCount = count) }
            }
            .launchIn(viewModelScope)
    }

    override fun onEvent(event: HomeEvent) {
        when (event) {
            HomeEvent.OpenPatients, HomeEvent.SwitchPatient ->
                sendEffect(HomeEffect.NavigateToPatients)

            HomeEvent.OpenSettings -> sendEffect(HomeEffect.NavigateToSettings)
            HomeEvent.AddFirstPatient -> sendEffect(HomeEffect.NavigateToAddPatient)
        }
    }
}
