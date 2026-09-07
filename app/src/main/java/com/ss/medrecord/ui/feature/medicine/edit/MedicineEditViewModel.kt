package com.ss.medrecord.ui.feature.medicine.edit

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import com.ss.medrecord.core.common.DataResult
import com.ss.medrecord.core.ui.BaseViewModel
import com.ss.medrecord.core.ui.toUserMessage
import com.ss.medrecord.domain.model.AuthSession
import com.ss.medrecord.domain.model.Medicine
import com.ss.medrecord.domain.repository.MedicineRepository
import com.ss.medrecord.domain.repository.VisitRepository
import com.ss.medrecord.domain.session.ActivePatientManager
import com.ss.medrecord.domain.session.SessionManager
import com.ss.medrecord.domain.validation.MedicineValidator
import com.ss.medrecord.ui.navigation.MedicineEditDestination
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import java.time.LocalDate
import javax.inject.Inject

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class MedicineEditViewModel @Inject constructor(
    private val medicineRepository: MedicineRepository,
    private val visitRepository: VisitRepository,
    private val activePatientManager: ActivePatientManager,
    private val sessionManager: SessionManager,
    savedStateHandle: SavedStateHandle,
) : BaseViewModel<MedicineEditUiState, MedicineEditEvent, MedicineEditEffect>(
    MedicineEditUiState(),
) {

    private val route = savedStateHandle.toRoute<MedicineEditDestination>()

    init {
        activePatientManager.patients
            .onEach { patients ->
                setState {
                    copy(
                        patients = patients,
                        selectedPatientId = selectedPatientId
                            ?: activePatientManager.activePatient.value?.patientId
                            ?: patients.firstOrNull()?.patientId,
                    )
                }
            }
            .launchIn(viewModelScope)

        // The visit list follows the chosen patient: offering another patient's
        // appointments would let a prescription be filed under the wrong person
        // in one tap.
        uiState
            .map { it.selectedPatientId }
            // Without this the whole visit query would be resubscribed on every
            // keystroke in the name field.
            .distinctUntilChanged()
            .flatMapLatest { patientId ->
                if (patientId == null) {
                    flowOf(emptyList())
                } else {
                    visitRepository.observeVisitsForPatient(patientId)
                }
            }
            .onEach { visits ->
                setState {
                    copy(
                        visits = visits,
                        // A visit that no longer belongs to the chosen patient
                        // must not stay silently selected.
                        selectedVisitId = selectedVisitId?.takeIf { id ->
                            visits.any { it.visit.visitId == id }
                        },
                    )
                }
            }
            .launchIn(viewModelScope)

        val medicineId = route.medicineId
        if (medicineId != null) {
            loadMedicine(medicineId)
        } else {
            setState {
                copy(
                    startDateEpochDay = LocalDate.now().toEpochDay(),
                    // A sensible first dose time, so the common case is one tap
                    // rather than a form that refuses to save until you find
                    // the time picker.
                    reminderTimes = listOf(DEFAULT_TIME_MINUTES),
                    selectedVisitId = route.visitId,
                )
            }
        }
    }

    private fun loadMedicine(medicineId: String) {
        viewModelScope.launch {
            setState { copy(isLoading = true) }
            val medicine = medicineRepository.getMedicine(medicineId)
            if (medicine == null) {
                setState { copy(isLoading = false) }
                sendEffect(MedicineEditEffect.ShowMessage("That medicine no longer exists."))
                sendEffect(MedicineEditEffect.NavigateBack)
                return@launch
            }
            setState {
                copy(
                    medicineId = medicine.medicineId,
                    selectedPatientId = medicine.patientId,
                    selectedVisitId = medicine.visitId,
                    name = medicine.name,
                    dosage = medicine.dosage.orEmpty(),
                    frequency = medicine.frequency,
                    reminderTimes = medicine.reminderTimes,
                    startDateEpochDay = medicine.startDateEpochDay,
                    endDateEpochDay = medicine.endDateEpochDay,
                    instructions = medicine.instructions.orEmpty(),
                    isActive = medicine.isActive,
                    isLoading = false,
                )
            }
        }
    }

    override fun onEvent(event: MedicineEditEvent) {
        when (event) {
            is MedicineEditEvent.PatientSelected -> setState {
                // Changing patient invalidates the visit, which belonged to the
                // previous one.
                copy(selectedPatientId = event.patientId, selectedVisitId = null)
            }

            is MedicineEditEvent.VisitSelected -> setState { copy(selectedVisitId = event.visitId) }
            is MedicineEditEvent.NameChanged -> setState { copy(name = event.value, nameError = null) }
            is MedicineEditEvent.DosageChanged ->
                setState { copy(dosage = event.value, dosageError = null) }

            is MedicineEditEvent.InstructionsChanged ->
                setState { copy(instructions = event.value, instructionsError = null) }

            is MedicineEditEvent.FrequencyChanged -> setState {
                copy(frequency = event.value, timesError = null)
            }

            is MedicineEditEvent.TimeAdded -> setState {
                copy(
                    // De-duplicated here rather than rejected, so tapping a time
                    // that is already on the list is a no-op instead of an error.
                    reminderTimes = (reminderTimes + event.minutesOfDay).distinct().sorted(),
                    timesError = null,
                    isTimePickerOpen = false,
                )
            }

            is MedicineEditEvent.TimeRemoved -> setState {
                copy(reminderTimes = reminderTimes - event.minutesOfDay)
            }

            MedicineEditEvent.TimePickerRequested -> setState { copy(isTimePickerOpen = true) }
            MedicineEditEvent.TimePickerDismissed -> setState { copy(isTimePickerOpen = false) }

            is MedicineEditEvent.DateSelected -> setState {
                when (event.field) {
                    MedicineDateField.START -> copy(
                        startDateEpochDay = event.epochDay,
                        startDateError = null,
                        openDatePicker = null,
                    )

                    MedicineDateField.END -> copy(
                        endDateEpochDay = event.epochDay,
                        endDateError = null,
                        openDatePicker = null,
                    )
                }
            }

            is MedicineEditEvent.DatePickerRequested ->
                setState { copy(openDatePicker = event.field) }

            MedicineEditEvent.DatePickerDismissed -> setState { copy(openDatePicker = null) }
            is MedicineEditEvent.ActiveChanged -> setState { copy(isActive = event.value) }
            MedicineEditEvent.Submit -> submit()
            MedicineEditEvent.BackClicked -> sendEffect(MedicineEditEffect.NavigateBack)
        }
    }

    private fun submit() {
        if (currentState.isSubmitting) return
        val state = currentState

        val nameResult = MedicineValidator.validateName(state.name)
        val dosageResult = MedicineValidator.validateDosage(state.dosage)
        val instructionsResult = MedicineValidator.validateInstructions(state.instructions)
        val startResult = MedicineValidator.validateStartDate(state.startDateEpochDay)
        val endResult = MedicineValidator.validateEndDate(
            endEpochDay = state.endDateEpochDay,
            startEpochDay = state.startDateEpochDay,
        )
        val timesResult = MedicineValidator.validateTimes(state.times, state.frequency)

        val allValid = nameResult.isValid &&
            dosageResult.isValid &&
            instructionsResult.isValid &&
            startResult.isValid &&
            endResult.isValid &&
            timesResult.isValid

        if (!allValid) {
            setState {
                copy(
                    nameError = nameResult.errorOrNull,
                    dosageError = dosageResult.errorOrNull,
                    instructionsError = instructionsResult.errorOrNull,
                    startDateError = startResult.errorOrNull,
                    endDateError = endResult.errorOrNull,
                    timesError = timesResult.errorOrNull,
                )
            }
            return
        }

        val userId = (sessionManager.session.value as? AuthSession.Authenticated)?.userId
        val patientId = state.selectedPatientId
        if (userId == null || patientId == null) {
            sendEffect(MedicineEditEffect.ShowMessage("Choose a patient before saving."))
            return
        }

        viewModelScope.launch {
            setState { copy(isSubmitting = true) }

            val existing = state.medicineId?.let { medicineRepository.getMedicine(it) }
            val medicine = Medicine(
                medicineId = state.medicineId.orEmpty(),
                userId = userId,
                patientId = patientId,
                visitId = state.selectedVisitId,
                name = state.name.trim(),
                dosage = state.dosage.trim().ifBlank { null },
                frequency = state.frequency,
                // An as-needed medicine keeps no times: storing them would
                // resurrect a schedule the moment the frequency changed back.
                reminderTimes = if (state.frequency.schedulesDoses) state.times else emptyList(),
                startDateEpochDay = state.startDateEpochDay!!,
                endDateEpochDay = state.endDateEpochDay,
                instructions = state.instructions.trim().ifBlank { null },
                isActive = state.isActive,
                // The repository stamps updatedAt; createdAt must survive edits.
                createdAt = existing?.createdAt ?: 0L,
                updatedAt = 0L,
            )

            val result = if (state.isEditing) {
                medicineRepository.updateMedicine(medicine)
            } else {
                medicineRepository.createMedicine(medicine)
            }

            setState { copy(isSubmitting = false) }
            when (result) {
                is DataResult.Success -> sendEffect(MedicineEditEffect.NavigateBack)
                is DataResult.Error ->
                    sendEffect(MedicineEditEffect.ShowMessage(result.error.toUserMessage()))
            }
        }
    }

    private companion object {
        /** 08:00. */
        const val DEFAULT_TIME_MINUTES = 8 * 60
    }
}
