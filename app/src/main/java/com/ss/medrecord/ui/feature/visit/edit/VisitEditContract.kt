package com.ss.medrecord.ui.feature.visit.edit

import com.ss.medrecord.core.ui.UiEffect
import com.ss.medrecord.core.ui.UiEvent
import com.ss.medrecord.core.ui.UiState
import com.ss.medrecord.domain.model.Facility
import com.ss.medrecord.domain.model.FacilityType
import com.ss.medrecord.domain.model.Patient

/** Which date field the picker is currently open for. */
enum class VisitDateField { VISIT, NEXT_VISIT }

data class VisitEditUiState(
    val visitId: String? = null,
    val patients: List<Patient> = emptyList(),
    val selectedPatientId: String? = null,
    val facilities: List<Facility> = emptyList(),
    /**
     * The typed clinic name. Held as free text rather than only an id so the
     * form works before the facility exists - the user types a new clinic and
     * it is created on save (spec 5.5 "Select/Add Facility").
     */
    val facilityName: String = "",
    val selectedFacilityId: String? = null,
    val facilityType: FacilityType = FacilityType.CLINIC,
    val doctorName: String = "",
    val visitDateEpochDay: Long? = null,
    val notes: String = "",
    val nextVisitDateEpochDay: Long? = null,
    val facilityError: String? = null,
    val visitDateError: String? = null,
    val nextVisitDateError: String? = null,
    val doctorNameError: String? = null,
    val notesError: String? = null,
    val isLoading: Boolean = false,
    val isSubmitting: Boolean = false,
    val openDatePicker: VisitDateField? = null,
    val showFacilitySuggestions: Boolean = false,
) : UiState {
    val isEditing: Boolean get() = visitId != null

    val canSubmit: Boolean
        get() = !isSubmitting &&
            !isLoading &&
            facilityName.isNotBlank() &&
            visitDateEpochDay != null &&
            selectedPatientId != null

    val selectedPatient: Patient?
        get() = patients.firstOrNull { it.patientId == selectedPatientId }

    /** Saved facilities matching what has been typed so far. */
    val facilitySuggestions: List<Facility>
        get() = if (facilityName.isBlank()) {
            facilities
        } else {
            facilities.filter { it.name.contains(facilityName.trim(), ignoreCase = true) }
        }

    /** True once the typed name is not one of the saved facilities. */
    val willCreateFacility: Boolean
        get() = facilityName.isNotBlank() &&
            facilities.none { it.name.equals(facilityName.trim(), ignoreCase = true) }
}

sealed interface VisitEditEvent : UiEvent {
    data class PatientSelected(val patientId: String) : VisitEditEvent
    data class FacilityNameChanged(val value: String) : VisitEditEvent
    data class FacilitySelected(val facility: Facility) : VisitEditEvent
    data class FacilityTypeChanged(val value: FacilityType) : VisitEditEvent
    data class DoctorNameChanged(val value: String) : VisitEditEvent
    data class NotesChanged(val value: String) : VisitEditEvent
    data class DateSelected(val field: VisitDateField, val epochDay: Long?) : VisitEditEvent
    data class DatePickerRequested(val field: VisitDateField) : VisitEditEvent
    data object DatePickerDismissed : VisitEditEvent
    data object FacilitySuggestionsDismissed : VisitEditEvent
    data object Submit : VisitEditEvent
    data object BackClicked : VisitEditEvent
}

sealed interface VisitEditEffect : UiEffect {
    data object NavigateBack : VisitEditEffect
    data class ShowMessage(val message: String) : VisitEditEffect
}
