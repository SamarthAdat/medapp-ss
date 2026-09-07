package com.ss.medrecord.ui.feature.medicine.list

import com.ss.medrecord.core.ui.UiEffect
import com.ss.medrecord.core.ui.UiEvent
import com.ss.medrecord.core.ui.UiState
import com.ss.medrecord.domain.model.MedicineWithContext
import com.ss.medrecord.domain.model.Patient
import com.ss.medrecord.domain.model.Reminder

/**
 * The medicines screen (spec section 5.8).
 *
 * Two things share this screen deliberately: what someone is taking, and what
 * is due today. They are the same question asked at two timescales, and putting
 * "today" behind a second navigation step is how a reminder app becomes one
 * nobody opens.
 *
 * As on the reports screen, one query feeds every filter combination and the
 * scoping happens here in state, so toggling costs nothing and the two views
 * cannot drift apart.
 */
data class MedicineListUiState(
    val activePatient: Patient? = null,
    val allMedicines: List<MedicineWithContext> = emptyList(),
    val todaysReminders: List<Reminder> = emptyList(),
    val showAllPatients: Boolean = false,
    val showInactive: Boolean = true,
    val isLoading: Boolean = true,
    val pendingDeletion: MedicineWithContext? = null,
    /**
     * Set when the schedule is stored but cannot actually ring: notifications
     * refused, or exact alarms not granted. Held in state rather than shown as
     * a one-off snackbar because it stays true until the user changes it.
     */
    val notificationsBlocked: Boolean = false,
    val exactAlarmsBlocked: Boolean = false,
) : UiState {

    private val inScope: List<MedicineWithContext>
        get() = if (showAllPatients) {
            allMedicines
        } else {
            allMedicines.filter { it.medicine.patientId == activePatient?.patientId }
        }

    val medicines: List<MedicineWithContext>
        get() = inScope.filter { showInactive || it.medicine.isActive }

    val activeCount: Int get() = inScope.count { it.medicine.isActive }

    val isEmpty: Boolean get() = !isLoading && medicines.isEmpty()

    val hasNoPatient: Boolean get() = !isLoading && activePatient == null && !showAllPatients

    /** Doses still ahead of them today, which is the only part worth listing. */
    val upcomingToday: List<Reminder>
        get() = todaysReminders
            .filter { it.isPending }
            .sortedBy { it.triggerAtMillis }

    /** Only warn about delivery once there is something that would be delivered. */
    val shouldWarnAboutDelivery: Boolean
        get() = (notificationsBlocked || exactAlarmsBlocked) && activeCount > 0
}

sealed interface MedicineListEvent : UiEvent {
    data class MedicineClicked(val entry: MedicineWithContext) : MedicineListEvent
    data class ActiveToggled(val entry: MedicineWithContext, val isActive: Boolean) :
        MedicineListEvent

    data class DeleteRequested(val entry: MedicineWithContext) : MedicineListEvent
    data object DeleteConfirmed : MedicineListEvent
    data object DeleteDismissed : MedicineListEvent
    data object ToggleAllPatients : MedicineListEvent
    data object ToggleShowInactive : MedicineListEvent
    data object AddClicked : MedicineListEvent
    data object FixNotificationsClicked : MedicineListEvent
    data object FixExactAlarmsClicked : MedicineListEvent
    /** Re-checked when the screen resumes, because both are changed elsewhere. */
    data object PermissionsRechecked : MedicineListEvent
    data object BackClicked : MedicineListEvent
}

sealed interface MedicineListEffect : UiEffect {
    data object NavigateBack : MedicineListEffect
    data object NavigateToAdd : MedicineListEffect
    data class NavigateToEdit(val medicineId: String) : MedicineListEffect
    data object RequestNotificationPermission : MedicineListEffect
    data object OpenExactAlarmSettings : MedicineListEffect
    data class ShowMessage(val message: String) : MedicineListEffect
}
