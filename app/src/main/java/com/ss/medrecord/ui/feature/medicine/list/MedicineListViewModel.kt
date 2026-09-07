package com.ss.medrecord.ui.feature.medicine.list

import androidx.lifecycle.viewModelScope
import com.ss.medrecord.core.common.DataResult
import com.ss.medrecord.core.ui.BaseViewModel
import com.ss.medrecord.core.ui.toUserMessage
import com.ss.medrecord.data.reminder.AlarmScheduler
import com.ss.medrecord.data.reminder.ReminderNotifier
import com.ss.medrecord.domain.model.AuthSession
import com.ss.medrecord.domain.model.MedicineWithContext
import com.ss.medrecord.domain.repository.MedicineRepository
import com.ss.medrecord.domain.repository.ReminderRepository
import com.ss.medrecord.domain.session.ActivePatientManager
import com.ss.medrecord.domain.session.SessionManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import javax.inject.Inject

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class MedicineListViewModel @Inject constructor(
    private val medicineRepository: MedicineRepository,
    private val notifier: ReminderNotifier,
    private val alarmScheduler: AlarmScheduler,
    reminderRepository: ReminderRepository,
    activePatientManager: ActivePatientManager,
    sessionManager: SessionManager,
) : BaseViewModel<MedicineListUiState, MedicineListEvent, MedicineListEffect>(
    MedicineListUiState(),
) {

    init {
        sessionManager.session
            .flatMapLatest { session ->
                when (session) {
                    is AuthSession.Authenticated ->
                        medicineRepository.observeMedicines(session.userId)

                    else -> flowOf(emptyList())
                }
            }
            .onEach { medicines ->
                setState { copy(allMedicines = medicines, isLoading = false) }
            }
            .launchIn(viewModelScope)

        sessionManager.session
            .flatMapLatest { session ->
                when (session) {
                    is AuthSession.Authenticated ->
                        reminderRepository.observeToday(session.userId)

                    else -> flowOf(emptyList())
                }
            }
            .onEach { reminders -> setState { copy(todaysReminders = reminders) } }
            .launchIn(viewModelScope)

        activePatientManager.activePatient
            .onEach { patient -> setState { copy(activePatient = patient) } }
            .launchIn(viewModelScope)

        refreshPermissionState()
    }

    override fun onEvent(event: MedicineListEvent) {
        when (event) {
            is MedicineListEvent.MedicineClicked ->
                sendEffect(MedicineListEffect.NavigateToEdit(event.entry.medicine.medicineId))

            is MedicineListEvent.ActiveToggled -> setActive(event.entry, event.isActive)
            is MedicineListEvent.DeleteRequested -> setState { copy(pendingDeletion = event.entry) }
            MedicineListEvent.DeleteDismissed -> setState { copy(pendingDeletion = null) }
            MedicineListEvent.DeleteConfirmed -> confirmDelete()

            MedicineListEvent.ToggleAllPatients ->
                setState { copy(showAllPatients = !showAllPatients) }

            MedicineListEvent.ToggleShowInactive ->
                setState { copy(showInactive = !showInactive) }

            MedicineListEvent.AddClicked -> sendEffect(MedicineListEffect.NavigateToAdd)

            MedicineListEvent.FixNotificationsClicked ->
                sendEffect(MedicineListEffect.RequestNotificationPermission)

            MedicineListEvent.FixExactAlarmsClicked ->
                sendEffect(MedicineListEffect.OpenExactAlarmSettings)

            MedicineListEvent.PermissionsRechecked -> refreshPermissionState()
            MedicineListEvent.BackClicked -> sendEffect(MedicineListEffect.NavigateBack)
        }
    }

    /**
     * Both of these are changed outside the app - in a system dialog or a
     * settings screen - so they are read fresh rather than observed. There is
     * no callback for either.
     */
    private fun refreshPermissionState() {
        setState {
            copy(
                notificationsBlocked = !notifier.canPost(),
                exactAlarmsBlocked = !alarmScheduler.canScheduleExact(),
            )
        }
    }

    private fun setActive(entry: MedicineWithContext, isActive: Boolean) {
        viewModelScope.launch {
            val result = medicineRepository.setActive(entry.medicine.medicineId, isActive)
            if (result is DataResult.Error) {
                sendEffect(MedicineListEffect.ShowMessage(result.error.toUserMessage()))
            } else if (!isActive) {
                sendEffect(
                    MedicineListEffect.ShowMessage(
                        "Paused. Reminders for ${entry.medicine.name} have stopped.",
                    ),
                )
            }
        }
    }

    private fun confirmDelete() {
        val target = currentState.pendingDeletion ?: return
        setState { copy(pendingDeletion = null) }
        viewModelScope.launch {
            when (
                val result = medicineRepository.deleteMedicine(target.medicine.medicineId)
            ) {
                is DataResult.Success ->
                    sendEffect(MedicineListEffect.ShowMessage("Medicine deleted"))

                is DataResult.Error ->
                    sendEffect(MedicineListEffect.ShowMessage(result.error.toUserMessage()))
            }
        }
    }
}
