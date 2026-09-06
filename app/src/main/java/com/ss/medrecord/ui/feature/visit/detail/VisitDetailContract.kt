package com.ss.medrecord.ui.feature.visit.detail

import android.net.Uri
import com.ss.medrecord.core.ui.UiEffect
import com.ss.medrecord.core.ui.UiEvent
import com.ss.medrecord.core.ui.UiState
import com.ss.medrecord.domain.model.Report
import com.ss.medrecord.domain.model.VisitWithFacility

data class VisitDetailUiState(
    val visit: VisitWithFacility? = null,
    val patientName: String? = null,
    val reports: List<Report> = emptyList(),
    val isLoading: Boolean = true,
    /** An attach is in flight: reading, compressing and encrypting the file. */
    val isImporting: Boolean = false,
    val isAttachSheetVisible: Boolean = false,
) : UiState

sealed interface VisitDetailEvent : UiEvent {
    data object EditClicked : VisitDetailEvent
    data object BackClicked : VisitDetailEvent

    // Reports (Phase 5)
    data object AttachClicked : VisitDetailEvent
    data object AttachDismissed : VisitDetailEvent
    data class FileSelected(val uri: Uri) : VisitDetailEvent
    data class ReportClicked(val report: Report) : VisitDetailEvent
    data class RetryUpload(val report: Report) : VisitDetailEvent

    /** A system picker could not be launched; see AttachReportSheet. */
    data class PickerUnavailable(val message: String) : VisitDetailEvent
}

sealed interface VisitDetailEffect : UiEffect {
    data object NavigateBack : VisitDetailEffect
    data class NavigateToEdit(val visitId: String) : VisitDetailEffect
    data class NavigateToReport(val reportId: String) : VisitDetailEffect
    data class ShowMessage(val message: String) : VisitDetailEffect
}
