package com.ss.medrecord.ui.feature.report.viewer

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import com.ss.medrecord.core.common.DataResult
import com.ss.medrecord.core.ui.BaseViewModel
import com.ss.medrecord.core.ui.toUserMessage
import com.ss.medrecord.domain.audit.AuditLogger
import com.ss.medrecord.domain.model.AuditAction
import com.ss.medrecord.domain.model.AuditEntityType
import com.ss.medrecord.domain.model.Report
import com.ss.medrecord.domain.model.ReportFileType
import com.ss.medrecord.domain.repository.ReportRepository
import com.ss.medrecord.ui.feature.report.ReportImageLoader
import com.ss.medrecord.ui.navigation.ReportViewerDestination
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class ReportViewerViewModel @Inject constructor(
    private val reportRepository: ReportRepository,
    private val imageLoader: ReportImageLoader,
    private val auditLogger: AuditLogger,
    savedStateHandle: SavedStateHandle,
) : BaseViewModel<ReportViewerUiState, ReportViewerEvent, ReportViewerEffect>(
    ReportViewerUiState(),
) {

    private val route = savedStateHandle.toRoute<ReportViewerDestination>()

    private var viewportWidthPx: Int = 0
    private var renderJob: Job? = null

    init {
        viewModelScope.launch {
            val report = reportRepository.getReport(route.reportId)
            setState { copy(report = report) }
            if (report == null) {
                setState { copy(isLoading = false, errorMessage = "That report no longer exists.") }
                return@launch
            }

            // Opening a report is an access to patient data, logged once per
            // screen entry (spec 4.9) - not per page render.
            auditLogger.log(
                action = AuditAction.VIEW,
                entityType = AuditEntityType.REPORT,
                entityId = report.reportId,
                patientId = report.patientId,
            )
            render(report)
        }
    }

    override fun onEvent(event: ReportViewerEvent) {
        when (event) {
            ReportViewerEvent.BackClicked -> sendEffect(ReportViewerEffect.NavigateBack)

            ReportViewerEvent.RetryClicked -> {
                val report = currentState.report ?: return
                setState { copy(isLoading = true, errorMessage = null) }
                viewModelScope.launch { render(report) }
            }

            is ReportViewerEvent.ViewportMeasured -> {
                if (event.widthPx == viewportWidthPx) return
                viewportWidthPx = event.widthPx
                // A PDF is rasterised at the width it will be shown, so a page
                // rendered before the layout was measured is redone once.
                val report = currentState.report
                if (report?.fileType == ReportFileType.PDF && !currentState.hasContent) {
                    renderJob?.cancel()
                    renderJob = viewModelScope.launch { render(report) }
                }
            }
        }
    }

    private suspend fun render(report: Report) {
        // Fetches from Cloud Storage only when this device has no copy, which
        // is the common case for a report added on another phone.
        if (!report.isAvailableOffline) setState { copy(isDownloading = true) }

        when (val local = reportRepository.ensureLocalCopy(report.reportId)) {
            is DataResult.Error -> setState {
                copy(
                    isLoading = false,
                    isDownloading = false,
                    errorMessage = local.error.toUserMessage(),
                )
            }

            is DataResult.Success -> {
                setState { copy(isDownloading = false) }
                val pages = when (report.fileType) {
                    ReportFileType.IMAGE ->
                        listOfNotNull(imageLoader.fullImage(local.data))

                    ReportFileType.PDF ->
                        imageLoader.pdfPages(local.data, viewportWidthPx.coerceAtLeast(MIN_WIDTH_PX))
                }
                setState {
                    copy(
                        pages = pages,
                        isLoading = false,
                        errorMessage = if (pages.isEmpty()) {
                            "This file could not be opened. It may be damaged."
                        } else {
                            null
                        },
                    )
                }
            }
        }
    }

    private companion object {
        /** Used only if a render starts before the layout has been measured. */
        const val MIN_WIDTH_PX = 1080
    }
}
