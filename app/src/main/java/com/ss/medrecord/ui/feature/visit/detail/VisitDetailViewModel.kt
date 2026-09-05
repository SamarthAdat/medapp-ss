package com.ss.medrecord.ui.feature.visit.detail

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import com.ss.medrecord.core.ui.BaseViewModel
import com.ss.medrecord.domain.audit.AuditLogger
import com.ss.medrecord.domain.model.AuditAction
import com.ss.medrecord.domain.model.AuditEntityType
import com.ss.medrecord.domain.repository.PatientRepository
import com.ss.medrecord.domain.repository.VisitRepository
import com.ss.medrecord.ui.navigation.VisitDetailDestination
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class VisitDetailViewModel @Inject constructor(
    visitRepository: VisitRepository,
    private val patientRepository: PatientRepository,
    private val auditLogger: AuditLogger,
    savedStateHandle: SavedStateHandle,
) : BaseViewModel<VisitDetailUiState, VisitDetailEvent, VisitDetailEffect>(VisitDetailUiState()) {

    private val route = savedStateHandle.toRoute<VisitDetailDestination>()

    init {
        visitRepository.observeVisit(route.visitId)
            .onEach { visit ->
                setState { copy(visit = visit, isLoading = false) }
                visit?.let { loadPatientName(it.visit.patientId) }
            }
            .launchIn(viewModelScope)

        // Opening a record is a VIEW under spec 4.9. Logged once per screen
        // entry rather than on every flow emission, so a background sync
        // refreshing the row does not manufacture phantom accesses.
        viewModelScope.launch {
            auditLogger.log(
                action = AuditAction.VIEW,
                entityType = AuditEntityType.VISIT,
                entityId = route.visitId,
            )
        }
    }

    private fun loadPatientName(patientId: String) {
        if (currentState.patientName != null) return
        viewModelScope.launch {
            val patient = patientRepository.getPatient(patientId)
            setState { copy(patientName = patient?.name) }
        }
    }

    override fun onEvent(event: VisitDetailEvent) {
        when (event) {
            VisitDetailEvent.BackClicked -> sendEffect(VisitDetailEffect.NavigateBack)
            VisitDetailEvent.EditClicked ->
                sendEffect(VisitDetailEffect.NavigateToEdit(route.visitId))
        }
    }
}
