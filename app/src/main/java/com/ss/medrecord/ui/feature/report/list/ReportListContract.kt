package com.ss.medrecord.ui.feature.report.list

import com.ss.medrecord.core.ui.UiEffect
import com.ss.medrecord.core.ui.UiEvent
import com.ss.medrecord.core.ui.UiState
import com.ss.medrecord.domain.model.Patient
import com.ss.medrecord.domain.model.ReportFileType
import com.ss.medrecord.domain.model.ReportWithContext

/**
 * The reports screen (spec section 5.8): every stored file for the active
 * patient, with an "All patients" toggle and filters by facility and file type.
 *
 * Filtering happens over an already-observed list rather than by re-querying.
 * One query feeds every combination of filters, the results stay live as sync
 * brings changes in, and switching a filter costs nothing.
 */
data class ReportListUiState(
    val activePatient: Patient? = null,
    val allReports: List<ReportWithContext> = emptyList(),
    val showAllPatients: Boolean = false,
    val fileTypeFilter: ReportFileType? = null,
    val facilityFilter: String? = null,
    val isLoading: Boolean = true,
    val pendingDeletion: ReportWithContext? = null,
) : UiState {

    /** Reports in scope before the type and facility filters narrow them. */
    private val inScope: List<ReportWithContext>
        get() = if (showAllPatients) {
            allReports
        } else {
            allReports.filter { it.report.patientId == activePatient?.patientId }
        }

    val reports: List<ReportWithContext>
        get() = inScope.filter { entry ->
            (fileTypeFilter == null || entry.report.fileType == fileTypeFilter) &&
                (facilityFilter == null || entry.facilityName == facilityFilter)
        }

    /** Only facilities that actually have reports in scope are offered. */
    val availableFacilities: List<String>
        get() = inScope.mapNotNull { it.facilityName }.distinct().sorted()

    val isEmpty: Boolean get() = !isLoading && reports.isEmpty()

    val hasFiltersApplied: Boolean get() = fileTypeFilter != null || facilityFilter != null

    val hasNoPatient: Boolean get() = !isLoading && activePatient == null && !showAllPatients
}

sealed interface ReportListEvent : UiEvent {
    data class ReportClicked(val entry: ReportWithContext) : ReportListEvent
    data class RetryUpload(val entry: ReportWithContext) : ReportListEvent
    data class DeleteRequested(val entry: ReportWithContext) : ReportListEvent
    data object DeleteConfirmed : ReportListEvent
    data object DeleteDismissed : ReportListEvent
    data object ToggleAllPatients : ReportListEvent
    data class FileTypeFilterChanged(val fileType: ReportFileType?) : ReportListEvent
    data class FacilityFilterChanged(val facility: String?) : ReportListEvent
    data object BackClicked : ReportListEvent
}

sealed interface ReportListEffect : UiEffect {
    data object NavigateBack : ReportListEffect
    data class NavigateToViewer(val reportId: String) : ReportListEffect
    data class ShowMessage(val message: String) : ReportListEffect
}
