package com.ss.medrecord.ui.feature.home

import androidx.lifecycle.viewModelScope
import com.ss.medrecord.core.connectivity.ConnectivityObserver
import com.ss.medrecord.core.ui.BaseViewModel
import com.ss.medrecord.domain.sync.SyncManager
import com.ss.medrecord.domain.usecase.ObserveDashboard
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import javax.inject.Inject

/**
 * Thin by design. The dashboard's arithmetic lives in [ObserveDashboard], which
 * is testable without Android; what is left here is connectivity, sync status
 * and turning taps into navigation.
 */
@HiltViewModel
class HomeViewModel @Inject constructor(
    connectivityObserver: ConnectivityObserver,
    observeDashboard: ObserveDashboard,
    private val syncManager: SyncManager,
) : BaseViewModel<HomeUiState, HomeEvent, HomeEffect>(
    initialState = HomeUiState(networkStatus = connectivityObserver.currentStatus()),
) {

    init {
        connectivityObserver.status
            .onEach { status -> setState { copy(networkStatus = status) } }
            .launchIn(viewModelScope)

        observeDashboard()
            .onEach { snapshot -> setState { copy(dashboard = snapshot, isLoading = false) } }
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
            HomeEvent.OpenVisits -> sendEffect(HomeEffect.NavigateToVisits)
            HomeEvent.OpenReports -> sendEffect(HomeEffect.NavigateToReports)
            HomeEvent.OpenMedicines -> sendEffect(HomeEffect.NavigateToMedicines)
            HomeEvent.OpenTimeline -> sendEffect(HomeEffect.NavigateToTimeline)
            HomeEvent.OpenFacilities -> sendEffect(HomeEffect.NavigateToFacilities)
            HomeEvent.AddVisit -> sendEffect(HomeEffect.NavigateToAddVisit)

            is HomeEvent.ActivityClicked -> sendEffect(event.entry.destinationEffect())

            is HomeEvent.AppointmentClicked ->
                sendEffect(HomeEffect.NavigateToVisit(event.visitId))

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
