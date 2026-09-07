package com.ss.medrecord.ui.feature.medicine.edit

import com.ss.medrecord.core.ui.UiEffect
import com.ss.medrecord.core.ui.UiEvent
import com.ss.medrecord.core.ui.UiState
import com.ss.medrecord.domain.model.Medicine
import com.ss.medrecord.domain.model.MedicineFrequency
import com.ss.medrecord.domain.model.Patient
import com.ss.medrecord.domain.model.VisitWithFacility

/** Which date field the picker is currently open for. */
enum class MedicineDateField { START, END }

data class MedicineEditUiState(
    val medicineId: String? = null,
    val patients: List<Patient> = emptyList(),
    val selectedPatientId: String? = null,
    /** Offered so a prescription can be tied back to the appointment it came from. */
    val visits: List<VisitWithFacility> = emptyList(),
    val selectedVisitId: String? = null,
    val name: String = "",
    val dosage: String = "",
    val frequency: MedicineFrequency = MedicineFrequency.DAILY,
    /** Minutes from midnight. Kept sorted so the form matches what is stored. */
    val reminderTimes: List<Int> = emptyList(),
    val startDateEpochDay: Long? = null,
    val endDateEpochDay: Long? = null,
    val instructions: String = "",
    val isActive: Boolean = true,
    val nameError: String? = null,
    val dosageError: String? = null,
    val instructionsError: String? = null,
    val startDateError: String? = null,
    val endDateError: String? = null,
    val timesError: String? = null,
    val isLoading: Boolean = false,
    val isSubmitting: Boolean = false,
    val openDatePicker: MedicineDateField? = null,
    val isTimePickerOpen: Boolean = false,
) : UiState {
    val isEditing: Boolean get() = medicineId != null

    val canSubmit: Boolean
        get() = !isSubmitting &&
            !isLoading &&
            name.isNotBlank() &&
            startDateEpochDay != null &&
            selectedPatientId != null

    val selectedPatient: Patient?
        get() = patients.firstOrNull { it.patientId == selectedPatientId }

    val selectedVisit: VisitWithFacility?
        get() = visits.firstOrNull { it.visit.visitId == selectedVisitId }

    /** As-needed has no schedule, so the times section is hidden rather than empty. */
    val showTimes: Boolean get() = frequency.schedulesDoses

    val times: List<Int> get() = reminderTimes.sorted()

    /** Mirrors [Medicine.scheduleSummary] for the live preview under the form. */
    val scheduleSummary: String
        get() = buildString {
            append(frequency.label)
            if (showTimes && reminderTimes.isNotEmpty()) {
                append(" · ")
                append(
                    times.joinToString(", ") {
                        Medicine.formatTime(Medicine.minutesToTime(it))
                    },
                )
            }
        }
}

sealed interface MedicineEditEvent : UiEvent {
    data class PatientSelected(val patientId: String) : MedicineEditEvent
    data class VisitSelected(val visitId: String?) : MedicineEditEvent
    data class NameChanged(val value: String) : MedicineEditEvent
    data class DosageChanged(val value: String) : MedicineEditEvent
    data class InstructionsChanged(val value: String) : MedicineEditEvent
    data class FrequencyChanged(val value: MedicineFrequency) : MedicineEditEvent
    data class TimeAdded(val minutesOfDay: Int) : MedicineEditEvent
    data class TimeRemoved(val minutesOfDay: Int) : MedicineEditEvent
    data object TimePickerRequested : MedicineEditEvent
    data object TimePickerDismissed : MedicineEditEvent
    data class DateSelected(val field: MedicineDateField, val epochDay: Long?) : MedicineEditEvent
    data class DatePickerRequested(val field: MedicineDateField) : MedicineEditEvent
    data object DatePickerDismissed : MedicineEditEvent
    data class ActiveChanged(val value: Boolean) : MedicineEditEvent
    data object Submit : MedicineEditEvent
    data object BackClicked : MedicineEditEvent
}

sealed interface MedicineEditEffect : UiEffect {
    data object NavigateBack : MedicineEditEffect
    data class ShowMessage(val message: String) : MedicineEditEffect
}
