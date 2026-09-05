package com.ss.medrecord.ui.feature.visit.list

import androidx.lifecycle.viewModelScope
import com.ss.medrecord.core.common.DataResult
import com.ss.medrecord.core.ui.BaseViewModel
import com.ss.medrecord.core.ui.toUserMessage
import com.ss.medrecord.domain.repository.VisitRepository
import com.ss.medrecord.domain.session.ActivePatientManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import javax.inject.Inject

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class VisitListViewModel @Inject constructor(
    private val visitRepository: VisitRepository,
    private val activePatientManager: ActivePatientManager,
) : BaseViewModel<VisitListUiState, VisitListEvent, VisitListEffect>(VisitListUiState()) {

    init {
        // Switching patient re-points the list. flatMapLatest cancels the old
        // query, so a slow emission from the previous patient cannot arrive
        // afterwards and show one patient's visits under another's name.
        activePatientManager.activePatient
            .onEach { patient -> setState { copy(activePatient = patient) } }
            .flatMapLatest { patient ->
                if (patient == null) {
                    flowOf(emptyList())
                } else {
                    visitRepository.observeVisitsForPatient(patient.patientId)
                }
            }
            .onEach { visits -> setState { copy(visits = visits, isLoading = false) } }
            .launchIn(viewModelScope)
    }

    override fun onEvent(event: VisitListEvent) {
        when (event) {
            is VisitListEvent.VisitClicked ->
                sendEffect(VisitListEffect.NavigateToDetail(event.visit.visit.visitId))

            is VisitListEvent.EditVisit ->
                sendEffect(VisitListEffect.NavigateToEdit(event.visit.visit.visitId))

            VisitListEvent.AddVisit -> {
                if (currentState.activePatient == null) {
                    sendEffect(VisitListEffect.ShowMessage("Add a patient before recording a visit."))
                } else {
                    sendEffect(VisitListEffect.NavigateToEdit(null))
                }
            }

            is VisitListEvent.DeleteRequested -> setState { copy(pendingDeletion = event.visit) }
            VisitListEvent.DeleteDismissed -> setState { copy(pendingDeletion = null) }
            VisitListEvent.DeleteConfirmed -> confirmDelete()
            VisitListEvent.ToggleGrouping -> setState { copy(groupByFacility = !groupByFacility) }
            VisitListEvent.BackClicked -> sendEffect(VisitListEffect.NavigateBack)
        }
    }

    private fun confirmDelete() {
        val target = currentState.pendingDeletion ?: return
        setState { copy(pendingDeletion = null) }
        viewModelScope.launch {
            when (val result = visitRepository.deleteVisit(target.visit.visitId)) {
                is DataResult.Success ->
                    sendEffect(VisitListEffect.ShowMessage("Visit deleted"))

                is DataResult.Error ->
                    sendEffect(VisitListEffect.ShowMessage(result.error.toUserMessage()))
            }
        }
    }
}
