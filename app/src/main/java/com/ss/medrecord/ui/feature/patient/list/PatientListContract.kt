package com.ss.medrecord.ui.feature.patient.list

import com.ss.medrecord.core.ui.UiEffect
import com.ss.medrecord.core.ui.UiEvent
import com.ss.medrecord.core.ui.UiState
import com.ss.medrecord.domain.model.Patient

data class PatientListUiState(
    val patients: List<Patient> = emptyList(),
    val archivedPatients: List<Patient> = emptyList(),
    val activePatientId: String? = null,
    val isLoading: Boolean = true,
    val showArchived: Boolean = false,
    /**
     * The profile awaiting delete confirmation. Held in state rather than in the
     * composable so the dialog survives configuration changes mid-decision.
     */
    val pendingDeletion: Patient? = null,
) : UiState {
    val isEmpty: Boolean get() = !isLoading && patients.isEmpty()
    val hasArchived: Boolean get() = archivedPatients.isNotEmpty()
}

sealed interface PatientListEvent : UiEvent {
    data class PatientSelected(val patient: Patient) : PatientListEvent
    data class EditPatient(val patient: Patient) : PatientListEvent
    data class ArchiveToggled(val patient: Patient) : PatientListEvent
    data class DeleteRequested(val patient: Patient) : PatientListEvent
    data object DeleteConfirmed : PatientListEvent
    data object DeleteDismissed : PatientListEvent
    data object ToggleShowArchived : PatientListEvent
    data object AddPatient : PatientListEvent
    data object BackClicked : PatientListEvent
}

sealed interface PatientListEffect : UiEffect {
    data object NavigateBack : PatientListEffect
    data class NavigateToEdit(val patientId: String?) : PatientListEffect

    /**
     * Selecting a patient is what the switcher exists for, so it closes and
     * returns to whatever the user was looking at (spec section 5.2).
     */
    data object PatientActivated : PatientListEffect
    data class ShowMessage(val message: String) : PatientListEffect
}
