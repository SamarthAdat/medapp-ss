package com.ss.medrecord.ui.feature.home

import androidx.lifecycle.viewModelScope
import com.ss.medrecord.core.connectivity.ConnectivityObserver
import com.ss.medrecord.core.ui.BaseViewModel
import com.ss.medrecord.domain.model.AuthSession
import com.ss.medrecord.domain.repository.MedicineRepository
import com.ss.medrecord.domain.repository.ReminderRepository
import com.ss.medrecord.domain.repository.ReportRepository
import com.ss.medrecord.domain.repository.VisitRepository
import com.ss.medrecord.domain.session.ActivePatientManager
import com.ss.medrecord.domain.session.SessionManager
import com.ss.medrecord.domain.sync.SyncManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import javax.inject.Inject

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class HomeViewModel @Inject constructor(
    connectivityObserver: ConnectivityObserver,
    activePatientManager: ActivePatientManager,
    private val syncManager: SyncManager,
    visitRepository: VisitRepository,
    reportRepository: ReportRepository,
    medicineRepository: MedicineRepository,
    reminderRepository: ReminderRepository,
    sessionManager: SessionManager,
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

        activePatientManager.activePatient
            .flatMapLatest { patient ->
                if (patient == null) flowOf(0) else visitRepository.observeVisitCount(patient.patientId)
            }
            .onEach { count -> setState { copy(visitCount = count) } }
            .launchIn(viewModelScope)

        activePatientManager.activePatient
            .flatMapLatest { patient ->
                if (patient == null) {
                    flowOf(0)
                } else {
                    reportRepository.observeReportCountForPatient(patient.patientId)
                }
            }
            .onEach { count -> setState { copy(reportCount = count) } }
            .launchIn(viewModelScope)

        activePatientManager.activePatient
            .flatMapLatest { patient ->
                if (patient == null) {
                    flowOf(0)
                } else {
                    medicineRepository.observeActiveCountForPatient(patient.patientId)
                }
            }
            .onEach { count -> setState { copy(medicineCount = count) } }
            .launchIn(viewModelScope)

        // Account-wide rather than per-patient: the point of the number is "how
        // many doses does this household still owe today", and someone giving
        // a child their medicine should not have to switch profile to see it.
        sessionManager.session
            .flatMapLatest { session ->
                when (session) {
                    is AuthSession.Authenticated ->
                        reminderRepository.observeToday(session.userId)

                    else -> flowOf(emptyList())
                }
            }
            .onEach { reminders ->
                setState { copy(dosesDueToday = reminders.count { it.isPending }) }
            }
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
            HomeEvent.AddVisit -> sendEffect(HomeEffect.NavigateToAddVisit)

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
