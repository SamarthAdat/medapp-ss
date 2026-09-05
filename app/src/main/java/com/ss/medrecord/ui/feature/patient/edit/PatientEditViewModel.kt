package com.ss.medrecord.ui.feature.patient.edit

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import com.ss.medrecord.core.common.DataResult
import com.ss.medrecord.core.common.map
import com.ss.medrecord.core.ui.BaseViewModel
import com.ss.medrecord.core.ui.toUserMessage
import com.ss.medrecord.domain.model.AuthSession
import com.ss.medrecord.domain.model.Patient
import com.ss.medrecord.domain.repository.PatientRepository
import com.ss.medrecord.domain.session.ActivePatientManager
import com.ss.medrecord.domain.session.SessionManager
import com.ss.medrecord.domain.validation.PatientValidator
import com.ss.medrecord.ui.navigation.PatientEditDestination
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class PatientEditViewModel @Inject constructor(
    private val patientRepository: PatientRepository,
    private val sessionManager: SessionManager,
    private val activePatientManager: ActivePatientManager,
    savedStateHandle: SavedStateHandle,
) : BaseViewModel<PatientEditUiState, PatientEditEvent, PatientEditEffect>(
    PatientEditUiState(),
) {

    // Type-safe route arguments; a null patientId means "add", not "edit".
    private val route = savedStateHandle.toRoute<PatientEditDestination>()

    init {
        route.patientId?.let(::loadPatient)
    }

    private fun loadPatient(patientId: String) {
        viewModelScope.launch {
            setState { copy(isLoading = true) }
            val patient = patientRepository.getPatient(patientId)
            if (patient == null) {
                setState { copy(isLoading = false) }
                sendEffect(PatientEditEffect.ShowMessage("That profile no longer exists."))
                sendEffect(PatientEditEffect.NavigateBack)
                return@launch
            }
            setState {
                copy(
                    patientId = patient.patientId,
                    name = patient.name,
                    relationship = patient.relationship,
                    dateOfBirthEpochDay = patient.dateOfBirthEpochDay,
                    gender = patient.gender,
                    bloodGroup = patient.bloodGroup,
                    knownAllergies = patient.knownAllergies.orEmpty(),
                    isLoading = false,
                )
            }
        }
    }

    override fun onEvent(event: PatientEditEvent) {
        when (event) {
            is PatientEditEvent.NameChanged -> setState { copy(name = event.value, nameError = null) }
            is PatientEditEvent.RelationshipChanged -> setState { copy(relationship = event.value) }
            is PatientEditEvent.GenderChanged -> setState { copy(gender = event.value) }
            is PatientEditEvent.BloodGroupChanged -> setState { copy(bloodGroup = event.value) }
            is PatientEditEvent.AllergiesChanged ->
                setState { copy(knownAllergies = event.value, allergiesError = null) }

            is PatientEditEvent.DateOfBirthChanged -> setState {
                copy(
                    dateOfBirthEpochDay = event.epochDay,
                    dateOfBirthError = null,
                    showDatePicker = false,
                )
            }

            PatientEditEvent.DatePickerRequested -> setState { copy(showDatePicker = true) }
            PatientEditEvent.DatePickerDismissed -> setState { copy(showDatePicker = false) }
            PatientEditEvent.Submit -> submit()
            PatientEditEvent.BackClicked -> sendEffect(PatientEditEffect.NavigateBack)
        }
    }

    private fun submit() {
        if (currentState.isSubmitting) return
        val state = currentState

        val nameResult = PatientValidator.validateName(state.name)
        val dobResult = PatientValidator.validateDateOfBirth(state.dateOfBirthEpochDay)
        val allergiesResult = PatientValidator.validateAllergies(state.knownAllergies)

        if (!nameResult.isValid || !dobResult.isValid || !allergiesResult.isValid) {
            setState {
                copy(
                    nameError = nameResult.errorOrNull,
                    dateOfBirthError = dobResult.errorOrNull,
                    allergiesError = allergiesResult.errorOrNull,
                )
            }
            return
        }

        val userId = (sessionManager.session.value as? AuthSession.Authenticated)?.userId
        if (userId == null) {
            sendEffect(PatientEditEffect.ShowMessage("Your session expired. Please sign in again."))
            return
        }

        viewModelScope.launch {
            setState { copy(isSubmitting = true) }
            val patient = Patient(
                patientId = state.patientId.orEmpty(),
                userId = userId,
                name = state.name.trim(),
                relationship = state.relationship,
                dateOfBirthEpochDay = state.dateOfBirthEpochDay,
                gender = state.gender,
                bloodGroup = state.bloodGroup,
                knownAllergies = state.knownAllergies.trim().ifBlank { null },
                createdAt = 0L,
                updatedAt = 0L,
            )

            val result = if (state.isEditing) {
                // The repository sets updatedAt; createdAt must survive an edit,
                // so the stored value is read back rather than reconstructed.
                val existing = patientRepository.getPatient(state.patientId!!)
                patientRepository.updatePatient(
                    patient.copy(
                        createdAt = existing?.createdAt ?: System.currentTimeMillis(),
                        isArchived = existing?.isArchived ?: false,
                        photoUrl = existing?.photoUrl,
                    ),
                ).map { state.patientId }
            } else {
                patientRepository.createPatient(patient)
            }

            setState { copy(isSubmitting = false) }
            when (result) {
                is DataResult.Success -> {
                    // A newly added profile becomes the active one; that is
                    // almost always what the user wants next, and it means the
                    // first patient ever added needs no extra tap.
                    if (!state.isEditing) {
                        result.data?.let(activePatientManager::setActivePatient)
                    }
                    sendEffect(PatientEditEffect.NavigateBack)
                }

                is DataResult.Error ->
                    sendEffect(PatientEditEffect.ShowMessage(result.error.toUserMessage()))
            }
        }
    }
}
