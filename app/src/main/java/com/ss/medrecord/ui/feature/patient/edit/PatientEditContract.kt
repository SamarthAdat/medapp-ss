package com.ss.medrecord.ui.feature.patient.edit

import com.ss.medrecord.core.ui.UiEffect
import com.ss.medrecord.core.ui.UiEvent
import com.ss.medrecord.core.ui.UiState
import com.ss.medrecord.domain.model.BloodGroup
import com.ss.medrecord.domain.model.Gender
import com.ss.medrecord.domain.model.Relationship

data class PatientEditUiState(
    val patientId: String? = null,
    val name: String = "",
    val relationship: Relationship = Relationship.SELF,
    val dateOfBirthEpochDay: Long? = null,
    val gender: Gender? = null,
    val bloodGroup: BloodGroup? = null,
    val knownAllergies: String = "",
    val nameError: String? = null,
    val dateOfBirthError: String? = null,
    val allergiesError: String? = null,
    val isLoading: Boolean = false,
    val isSubmitting: Boolean = false,
    val showDatePicker: Boolean = false,
) : UiState {
    val isEditing: Boolean get() = patientId != null
    val canSubmit: Boolean get() = !isSubmitting && !isLoading && name.isNotBlank()
}

sealed interface PatientEditEvent : UiEvent {
    data class NameChanged(val value: String) : PatientEditEvent
    data class RelationshipChanged(val value: Relationship) : PatientEditEvent
    data class GenderChanged(val value: Gender?) : PatientEditEvent
    data class BloodGroupChanged(val value: BloodGroup?) : PatientEditEvent
    data class AllergiesChanged(val value: String) : PatientEditEvent
    data class DateOfBirthChanged(val epochDay: Long?) : PatientEditEvent
    data object DatePickerRequested : PatientEditEvent
    data object DatePickerDismissed : PatientEditEvent
    data object Submit : PatientEditEvent
    data object BackClicked : PatientEditEvent
}

sealed interface PatientEditEffect : UiEffect {
    data object NavigateBack : PatientEditEffect
    data class ShowMessage(val message: String) : PatientEditEffect
}
