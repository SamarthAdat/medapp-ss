package com.ss.medrecord.ui.feature.visit.edit

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import com.ss.medrecord.core.common.DataResult
import com.ss.medrecord.core.ui.BaseViewModel
import com.ss.medrecord.core.ui.toUserMessage
import com.ss.medrecord.domain.model.AuthSession
import com.ss.medrecord.domain.model.Visit
import com.ss.medrecord.domain.repository.FacilityRepository
import com.ss.medrecord.domain.repository.VisitRepository
import com.ss.medrecord.domain.session.ActivePatientManager
import com.ss.medrecord.domain.session.SessionManager
import com.ss.medrecord.domain.validation.VisitValidator
import com.ss.medrecord.ui.navigation.VisitEditDestination
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import java.time.LocalDate
import javax.inject.Inject

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class VisitEditViewModel @Inject constructor(
    private val visitRepository: VisitRepository,
    private val facilityRepository: FacilityRepository,
    private val activePatientManager: ActivePatientManager,
    private val sessionManager: SessionManager,
    savedStateHandle: SavedStateHandle,
) : BaseViewModel<VisitEditUiState, VisitEditEvent, VisitEditEffect>(VisitEditUiState()) {

    private val route = savedStateHandle.toRoute<VisitEditDestination>()

    init {
        activePatientManager.patients
            .onEach { patients ->
                setState {
                    copy(
                        patients = patients,
                        // Defaults to the active patient but stays changeable
                        // (spec 5.5), which matters when logging a visit for a
                        // child while viewing your own records.
                        selectedPatientId = selectedPatientId
                            ?: activePatientManager.activePatient.value?.patientId
                            ?: patients.firstOrNull()?.patientId,
                    )
                }
            }
            .launchIn(viewModelScope)

        sessionManager.session
            .flatMapLatest { session ->
                when (session) {
                    is AuthSession.Authenticated ->
                        facilityRepository.observeFacilities(session.userId)

                    else -> flowOf(emptyList())
                }
            }
            .onEach { facilities -> setState { copy(facilities = facilities) } }
            .launchIn(viewModelScope)

        route.visitId?.let(::loadVisit)

        if (route.visitId == null) {
            // A visit is nearly always logged the day it happened.
            setState { copy(visitDateEpochDay = LocalDate.now().toEpochDay()) }
        }
    }

    private fun loadVisit(visitId: String) {
        viewModelScope.launch {
            setState { copy(isLoading = true) }
            val visit = visitRepository.getVisit(visitId)
            if (visit == null) {
                setState { copy(isLoading = false) }
                sendEffect(VisitEditEffect.ShowMessage("That visit no longer exists."))
                sendEffect(VisitEditEffect.NavigateBack)
                return@launch
            }
            val facility = facilityRepository.getFacility(visit.facilityId)
            setState {
                copy(
                    visitId = visit.visitId,
                    selectedPatientId = visit.patientId,
                    selectedFacilityId = visit.facilityId,
                    facilityName = facility?.name.orEmpty(),
                    facilityType = facility?.type ?: facilityType,
                    doctorName = visit.doctorName.orEmpty(),
                    visitDateEpochDay = visit.visitDateEpochDay,
                    notes = visit.notes.orEmpty(),
                    nextVisitDateEpochDay = visit.nextVisitDateEpochDay,
                    isLoading = false,
                )
            }
        }
    }

    override fun onEvent(event: VisitEditEvent) {
        when (event) {
            is VisitEditEvent.PatientSelected -> setState { copy(selectedPatientId = event.patientId) }

            is VisitEditEvent.FacilityNameChanged -> setState {
                copy(
                    facilityName = event.value,
                    // Typing after picking means the choice no longer holds.
                    selectedFacilityId = null,
                    facilityError = null,
                    showFacilitySuggestions = event.value.isNotBlank(),
                )
            }

            is VisitEditEvent.FacilitySelected -> setState {
                copy(
                    facilityName = event.facility.name,
                    selectedFacilityId = event.facility.facilityId,
                    facilityType = event.facility.type,
                    facilityError = null,
                    showFacilitySuggestions = false,
                )
            }

            is VisitEditEvent.FacilityTypeChanged -> setState { copy(facilityType = event.value) }
            is VisitEditEvent.DoctorNameChanged ->
                setState { copy(doctorName = event.value, doctorNameError = null) }

            is VisitEditEvent.NotesChanged -> setState { copy(notes = event.value, notesError = null) }

            is VisitEditEvent.DateSelected -> setState {
                when (event.field) {
                    VisitDateField.VISIT -> copy(
                        visitDateEpochDay = event.epochDay,
                        visitDateError = null,
                        openDatePicker = null,
                    )

                    VisitDateField.NEXT_VISIT -> copy(
                        nextVisitDateEpochDay = event.epochDay,
                        nextVisitDateError = null,
                        openDatePicker = null,
                    )
                }
            }

            is VisitEditEvent.DatePickerRequested -> setState { copy(openDatePicker = event.field) }
            VisitEditEvent.DatePickerDismissed -> setState { copy(openDatePicker = null) }
            VisitEditEvent.FacilitySuggestionsDismissed ->
                setState { copy(showFacilitySuggestions = false) }

            VisitEditEvent.Submit -> submit()
            VisitEditEvent.BackClicked -> sendEffect(VisitEditEffect.NavigateBack)
        }
    }

    private fun submit() {
        if (currentState.isSubmitting) return
        val state = currentState

        val facilityResult = VisitValidator.validateFacility(state.facilityName)
        val visitDateResult = VisitValidator.validateVisitDate(state.visitDateEpochDay)
        val nextDateResult = VisitValidator.validateNextVisitDate(
            nextEpochDay = state.nextVisitDateEpochDay,
            visitEpochDay = state.visitDateEpochDay,
        )
        val doctorResult = VisitValidator.validateDoctorName(state.doctorName)
        val notesResult = VisitValidator.validateNotes(state.notes)

        val allValid = facilityResult.isValid &&
            visitDateResult.isValid &&
            nextDateResult.isValid &&
            doctorResult.isValid &&
            notesResult.isValid

        if (!allValid) {
            setState {
                copy(
                    facilityError = facilityResult.errorOrNull,
                    visitDateError = visitDateResult.errorOrNull,
                    nextVisitDateError = nextDateResult.errorOrNull,
                    doctorNameError = doctorResult.errorOrNull,
                    notesError = notesResult.errorOrNull,
                )
            }
            return
        }

        val userId = (sessionManager.session.value as? AuthSession.Authenticated)?.userId
        val patientId = state.selectedPatientId
        if (userId == null || patientId == null) {
            sendEffect(VisitEditEffect.ShowMessage("Choose a patient before saving."))
            return
        }

        viewModelScope.launch {
            setState { copy(isSubmitting = true) }

            // Resolve the facility first: a visit cannot be stored without one,
            // and the typed name may refer to a clinic that does not exist yet.
            val facilityId = state.selectedFacilityId
                ?: when (
                    val created = facilityRepository.findOrCreateByName(
                        userId = userId,
                        name = state.facilityName,
                        type = state.facilityType,
                    )
                ) {
                    is DataResult.Success -> created.data.facilityId
                    is DataResult.Error -> {
                        setState { copy(isSubmitting = false) }
                        sendEffect(VisitEditEffect.ShowMessage(created.error.toUserMessage()))
                        return@launch
                    }
                }

            val existing = state.visitId?.let { visitRepository.getVisit(it) }
            val visit = Visit(
                visitId = state.visitId.orEmpty(),
                userId = userId,
                patientId = patientId,
                facilityId = facilityId,
                doctorName = state.doctorName.trim().ifBlank { null },
                visitDateEpochDay = state.visitDateEpochDay!!,
                notes = state.notes.trim().ifBlank { null },
                nextVisitDateEpochDay = state.nextVisitDateEpochDay,
                // The repository stamps updatedAt; createdAt must survive edits.
                createdAt = existing?.createdAt ?: 0L,
                updatedAt = 0L,
            )

            val result = if (state.isEditing) {
                visitRepository.updateVisit(visit)
            } else {
                visitRepository.createVisit(visit)
            }

            setState { copy(isSubmitting = false) }
            when (result) {
                is DataResult.Success -> sendEffect(VisitEditEffect.NavigateBack)
                is DataResult.Error ->
                    sendEffect(VisitEditEffect.ShowMessage(result.error.toUserMessage()))
            }
        }
    }
}
