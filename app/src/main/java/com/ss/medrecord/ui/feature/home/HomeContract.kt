package com.ss.medrecord.ui.feature.home

import com.ss.medrecord.core.connectivity.NetworkStatus
import com.ss.medrecord.core.ui.UiEffect
import com.ss.medrecord.core.ui.UiEvent
import com.ss.medrecord.core.ui.UiState
import com.ss.medrecord.domain.model.DashboardSnapshot
import com.ss.medrecord.domain.model.Patient
import com.ss.medrecord.domain.model.TimelineEntry
import com.ss.medrecord.domain.model.TimelineKind
import com.ss.medrecord.domain.sync.SyncStatusUi

/**
 * The dashboard (spec section 5.3).
 *
 * The aggregate arrives as one [DashboardSnapshot] rather than as a dozen
 * separate fields, because it is computed as a unit: a count and the list it
 * counts come from the same read, and splitting them here would reintroduce
 * exactly the drift the use case exists to prevent. Connectivity and sync
 * status stay separate - they describe the app, not the records.
 */
data class HomeUiState(
    val isLoading: Boolean = true,
    val networkStatus: NetworkStatus = NetworkStatus.UNAVAILABLE,
    val dashboard: DashboardSnapshot = DashboardSnapshot(),
    val syncStatus: SyncStatusUi = SyncStatusUi(),
) : UiState {
    val isOnline: Boolean get() = networkStatus == NetworkStatus.AVAILABLE

    val activePatient: Patient? get() = dashboard.activePatient

    /** No profiles yet, so the dashboard prompts for the first one instead. */
    val needsFirstPatient: Boolean get() = !isLoading && dashboard.needsFirstPatient

    /**
     * Allergies lead the health summary when there are any. It is the one field
     * here that changes what someone else should do in an emergency, and a
     * dashboard that buries it below a visit count has its priorities wrong.
     */
    val allergies: String?
        get() = activePatient?.knownAllergies?.takeIf { it.isNotBlank() }
}

sealed interface HomeEvent : UiEvent {
    data object OpenPatients : HomeEvent
    /** Tapping a profile chip on the dashboard, rather than leaving to choose. */
    data class PatientSelected(val patientId: String) : HomeEvent
    data object OpenSettings : HomeEvent
    data object SwitchPatient : HomeEvent
    data object AddFirstPatient : HomeEvent
    data object SyncNowClicked : HomeEvent
    data object OpenVisits : HomeEvent
    data object AddVisit : HomeEvent
    data object OpenReports : HomeEvent
    data object OpenMedicines : HomeEvent
    data object OpenTimeline : HomeEvent
    data object OpenFacilities : HomeEvent
    data class ActivityClicked(val entry: TimelineEntry) : HomeEvent
    data class AppointmentClicked(val visitId: String) : HomeEvent
}

sealed interface HomeEffect : UiEffect {
    data object NavigateToPatients : HomeEffect
    data object NavigateToSettings : HomeEffect
    data object NavigateToAddPatient : HomeEffect
    data class ShowMessage(val message: String) : HomeEffect
    data object NavigateToVisits : HomeEffect
    data object NavigateToAddVisit : HomeEffect
    data object NavigateToReports : HomeEffect
    data object NavigateToMedicines : HomeEffect
    data object NavigateToTimeline : HomeEffect
    data object NavigateToFacilities : HomeEffect
    data class NavigateToVisit(val visitId: String) : HomeEffect
    data class NavigateToReport(val reportId: String) : HomeEffect
    data class NavigateToMedicine(val medicineId: String) : HomeEffect
}

/** Where tapping a timeline row goes, shared by the dashboard and the timeline. */
fun TimelineEntry.destinationEffect(): HomeEffect = when (kind) {
    TimelineKind.VISIT -> HomeEffect.NavigateToVisit(targetId)
    TimelineKind.REPORT -> HomeEffect.NavigateToReport(targetId)
    TimelineKind.MEDICINE -> HomeEffect.NavigateToMedicine(targetId)
}
