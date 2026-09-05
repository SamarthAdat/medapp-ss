package com.ss.medrecord.ui.feature.home

import androidx.lifecycle.viewModelScope
import com.ss.medrecord.core.connectivity.ConnectivityObserver
import com.ss.medrecord.core.ui.BaseViewModel
import com.ss.medrecord.domain.session.ActivePatientManager
import com.ss.medrecord.domain.sync.SyncManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import javax.inject.Inject

@HiltViewModel
class HomeViewModel @Inject constructor(
    connectivityObserver: ConnectivityObserver,
    activePatientManager: ActivePatientManager,
    private val syncManager: SyncManager,
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

        syncManager.status
            .onEach { status -> setState { copy(syncStatus = status) } }
            .launchIn(viewModelScope)
    }

    override fun onEvent(event: HomeEvent) {
        when (event) {
            HomeEvent.OpenPatients, HomeEvent.SwitchPatient ->
                sendEffect(HomeEffect.NavigateToPatients)

            HomeEvent.OpenSettings -> sendEffect(HomeEffect.NavigateToSettings)
            HomeEvent.AddFirstPatient -> sendEffect(HomeEffect.NavigateToAddPatient)

            HomeEvent.SyncNowClicked -> {
                if (currentState.syncStatus.isOnline) {
                    // REPLACE, not KEEP: an explicit tap should start a pass now
                    // rather than quietly ride on one already queued.
                    syncManager.syncNow(expedited = true)
                    sendEffect(HomeEffect.ShowMessage("Syncing..."))
                } else {
                    sendEffect(
                        HomeEffect.ShowMessage(
                            "You are offline. Changes are saved here and will sync automatically.",
                        ),
                    )
                }
            }
        }
    }
}
