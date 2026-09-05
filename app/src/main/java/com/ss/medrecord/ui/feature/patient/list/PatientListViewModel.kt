package com.ss.medrecord.ui.feature.patient.list

import androidx.lifecycle.viewModelScope
import com.ss.medrecord.core.common.DataResult
import com.ss.medrecord.core.ui.BaseViewModel
import com.ss.medrecord.core.ui.toUserMessage
import com.ss.medrecord.domain.model.AuthSession
import com.ss.medrecord.domain.model.Patient
import com.ss.medrecord.domain.repository.PatientRepository
import com.ss.medrecord.domain.session.ActivePatientManager
import com.ss.medrecord.domain.session.SessionManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import javax.inject.Inject

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class PatientListViewModel @Inject constructor(
    private val patientRepository: PatientRepository,
    private val activePatientManager: ActivePatientManager,
    private val sessionManager: SessionManager,
) : BaseViewModel<PatientListUiState, PatientListEvent, PatientListEffect>(
    PatientListUiState(),
) {

    init {
        sessionManager.session
            .flatMapLatest { session ->
                when (session) {
                    is AuthSession.Authenticated -> combine(
                        patientRepository.observeActivePatients(session.userId),
                        patientRepository.observeArchivedPatients(session.userId),
                        activePatientManager.activePatient,
                    ) { active, archived, selected -> Triple(active, archived, selected) }

                    else -> flowOf(Triple(emptyList(), emptyList(), null))
                }
            }
            .onEach { (active, archived, selected) ->
                setState {
                    copy(
                        patients = active,
                        archivedPatients = archived,
                        activePatientId = selected?.patientId,
                        isLoading = false,
                    )
                }
            }
            .launchIn(viewModelScope)
    }

    override fun onEvent(event: PatientListEvent) {
        when (event) {
            is PatientListEvent.PatientSelected -> {
                activePatientManager.setActivePatient(event.patient.patientId)
                sendEffect(PatientListEffect.PatientActivated)
            }

            is PatientListEvent.EditPatient ->
                sendEffect(PatientListEffect.NavigateToEdit(event.patient.patientId))

            PatientListEvent.AddPatient -> sendEffect(PatientListEffect.NavigateToEdit(null))

            is PatientListEvent.ArchiveToggled -> archive(event.patient)

            is PatientListEvent.DeleteRequested ->
                setState { copy(pendingDeletion = event.patient) }

            PatientListEvent.DeleteDismissed -> setState { copy(pendingDeletion = null) }

            PatientListEvent.DeleteConfirmed -> confirmDelete()

            PatientListEvent.ToggleShowArchived -> setState { copy(showArchived = !showArchived) }

            PatientListEvent.BackClicked -> sendEffect(PatientListEffect.NavigateBack)
        }
    }

    private fun archive(patient: Patient) {
        viewModelScope.launch {
            val archiving = !patient.isArchived
            when (val result = patientRepository.setArchived(patient.patientId, archiving)) {
                is DataResult.Success -> sendEffect(
                    PatientListEffect.ShowMessage(
                        if (archiving) {
                            "${patient.name} archived"
                        } else {
                            "${patient.name} restored"
                        },
                    ),
                )

                is DataResult.Error ->
                    sendEffect(PatientListEffect.ShowMessage(result.error.toUserMessage()))
            }
        }
    }

    private fun confirmDelete() {
        val patient = currentState.pendingDeletion ?: return
        setState { copy(pendingDeletion = null) }
        viewModelScope.launch {
            when (val result = patientRepository.deletePatient(patient.patientId)) {
                is DataResult.Success -> {
                    // The manager re-resolves the selection against the live list
                    // on its own, but clearing here avoids a frame where the
                    // deleted profile is still shown as active.
                    if (currentState.activePatientId == patient.patientId) {
                        activePatientManager.setActivePatient(null)
                    }
                    sendEffect(PatientListEffect.ShowMessage("${patient.name} deleted"))
                }

                is DataResult.Error ->
                    sendEffect(PatientListEffect.ShowMessage(result.error.toUserMessage()))
            }
        }
    }
}
