package com.ss.medrecord.ui.feature.report.list

import androidx.compose.ui.graphics.ImageBitmap
import androidx.lifecycle.viewModelScope
import com.ss.medrecord.core.common.DataResult
import com.ss.medrecord.core.ui.BaseViewModel
import com.ss.medrecord.core.ui.toUserMessage
import com.ss.medrecord.domain.model.AuthSession
import com.ss.medrecord.domain.model.Report
import com.ss.medrecord.domain.model.ReportWithContext
import com.ss.medrecord.domain.repository.ReportRepository
import com.ss.medrecord.domain.session.ActivePatientManager
import com.ss.medrecord.domain.session.SessionManager
import com.ss.medrecord.ui.feature.report.ReportImageLoader
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
class ReportListViewModel @Inject constructor(
    private val reportRepository: ReportRepository,
    private val imageLoader: ReportImageLoader,
    activePatientManager: ActivePatientManager,
    sessionManager: SessionManager,
) : BaseViewModel<ReportListUiState, ReportListEvent, ReportListEffect>(ReportListUiState()) {

    init {
        // The whole account's reports are observed once and scoped in state, so
        // the "All patients" toggle is a filter rather than a new query - which
        // keeps the toggle instant and the two views consistent with each other.
        sessionManager.session
            .flatMapLatest { session ->
                when (session) {
                    is AuthSession.Authenticated ->
                        reportRepository.observeReportsWithContext(session.userId)

                    else -> flowOf(emptyList())
                }
            }
            .onEach { reports -> setState { copy(allReports = reports, isLoading = false) } }
            .launchIn(viewModelScope)

        activePatientManager.activePatient
            .onEach { patient -> setState { copy(activePatient = patient) } }
            .launchIn(viewModelScope)
    }

    /** Passed to the tiles so decoding happens per visible item, not per row. */
    suspend fun loadThumbnail(report: Report): ImageBitmap? = imageLoader.thumbnail(report)

    override fun onEvent(event: ReportListEvent) {
        when (event) {
            is ReportListEvent.ReportClicked -> openReport(event.entry)
            is ReportListEvent.RetryUpload -> retryUpload(event.entry)
            is ReportListEvent.DeleteRequested -> setState { copy(pendingDeletion = event.entry) }
            ReportListEvent.DeleteDismissed -> setState { copy(pendingDeletion = null) }
            ReportListEvent.DeleteConfirmed -> confirmDelete()

            ReportListEvent.ToggleAllPatients -> setState {
                // Facility choices are derived from what is in scope, so a
                // filter naming a clinic the new scope has no reports at is
                // cleared rather than left silently hiding everything.
                copy(showAllPatients = !showAllPatients, facilityFilter = null)
            }

            is ReportListEvent.FileTypeFilterChanged -> setState {
                copy(fileTypeFilter = event.fileType)
            }

            is ReportListEvent.FacilityFilterChanged -> setState {
                copy(facilityFilter = event.facility)
            }

            ReportListEvent.BackClicked -> sendEffect(ReportListEffect.NavigateBack)
        }
    }

    private fun openReport(entry: ReportWithContext) {
        val report = entry.report
        when {
            report.isRejected -> sendEffect(
                ReportListEffect.ShowMessage(
                    "This file was never saved - it was over the size limit.",
                ),
            )

            report.isViewable ->
                sendEffect(ReportListEffect.NavigateToViewer(report.reportId))

            else -> sendEffect(
                ReportListEffect.ShowMessage(
                    "This report has not finished uploading from the device that added it.",
                ),
            )
        }
    }

    private fun retryUpload(entry: ReportWithContext) {
        viewModelScope.launch {
            when (val result = reportRepository.retryUpload(entry.report.reportId)) {
                is DataResult.Success ->
                    sendEffect(ReportListEffect.ShowMessage("Upload queued"))

                is DataResult.Error ->
                    sendEffect(ReportListEffect.ShowMessage(result.error.toUserMessage()))
            }
        }
    }

    private fun confirmDelete() {
        val target = currentState.pendingDeletion ?: return
        setState { copy(pendingDeletion = null) }
        viewModelScope.launch {
            when (val result = reportRepository.deleteReport(target.report.reportId)) {
                is DataResult.Success ->
                    sendEffect(ReportListEffect.ShowMessage("Report deleted"))

                is DataResult.Error ->
                    sendEffect(ReportListEffect.ShowMessage(result.error.toUserMessage()))
            }
        }
    }
}
