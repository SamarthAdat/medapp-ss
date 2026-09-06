package com.ss.medrecord.ui.feature.visit.detail

import android.net.Uri
import androidx.compose.ui.graphics.ImageBitmap
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import com.ss.medrecord.core.common.DataResult
import com.ss.medrecord.core.file.CameraCaptureStore
import com.ss.medrecord.core.ui.BaseViewModel
import com.ss.medrecord.core.ui.toUserMessage
import com.ss.medrecord.domain.audit.AuditLogger
import com.ss.medrecord.domain.model.AuditAction
import com.ss.medrecord.domain.model.AuditEntityType
import com.ss.medrecord.domain.model.Report
import com.ss.medrecord.domain.repository.PatientRepository
import com.ss.medrecord.domain.repository.ReportImportResult
import com.ss.medrecord.domain.repository.ReportRepository
import com.ss.medrecord.domain.repository.VisitRepository
import com.ss.medrecord.ui.feature.report.ReportImageLoader
import com.ss.medrecord.ui.navigation.VisitDetailDestination
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class VisitDetailViewModel @Inject constructor(
    visitRepository: VisitRepository,
    private val reportRepository: ReportRepository,
    private val patientRepository: PatientRepository,
    private val imageLoader: ReportImageLoader,
    private val cameraCaptureStore: CameraCaptureStore,
    private val auditLogger: AuditLogger,
    savedStateHandle: SavedStateHandle,
) : BaseViewModel<VisitDetailUiState, VisitDetailEvent, VisitDetailEffect>(VisitDetailUiState()) {

    private val route = savedStateHandle.toRoute<VisitDetailDestination>()

    init {
        visitRepository.observeVisit(route.visitId)
            .onEach { visit ->
                setState { copy(visit = visit, isLoading = false) }
                visit?.let { loadPatientName(it.visit.patientId) }
            }
            .launchIn(viewModelScope)

        reportRepository.observeReportsForVisit(route.visitId)
            .onEach { attached -> setState { copy(reports = attached) } }
            .launchIn(viewModelScope)

        // Opening a record is a VIEW under spec 4.9. Logged once per screen
        // entry rather than on every flow emission, so a background sync
        // refreshing the row does not manufacture phantom accesses.
        viewModelScope.launch {
            auditLogger.log(
                action = AuditAction.VIEW,
                entityType = AuditEntityType.VISIT,
                entityId = route.visitId,
            )
        }
    }

    /**
     * A Uri the camera app may write one capture to. Handed out here rather
     * than built in the composable so the file's whole life - created, read,
     * wiped - stays with the code that knows when the import is finished.
     */
    fun newCaptureUri(): Uri = cameraCaptureStore.newCaptureUri()

    suspend fun loadThumbnail(report: Report): ImageBitmap? = imageLoader.thumbnail(report)

    private fun loadPatientName(patientId: String) {
        if (currentState.patientName != null) return
        viewModelScope.launch {
            val patient = patientRepository.getPatient(patientId)
            setState { copy(patientName = patient?.name) }
        }
    }

    override fun onEvent(event: VisitDetailEvent) {
        when (event) {
            VisitDetailEvent.BackClicked -> sendEffect(VisitDetailEffect.NavigateBack)
            VisitDetailEvent.EditClicked ->
                sendEffect(VisitDetailEffect.NavigateToEdit(route.visitId))

            VisitDetailEvent.AttachClicked -> setState { copy(isAttachSheetVisible = true) }
            VisitDetailEvent.AttachDismissed -> setState { copy(isAttachSheetVisible = false) }
            is VisitDetailEvent.FileSelected -> importFile(event.uri)
            is VisitDetailEvent.ReportClicked -> openReport(event.report)
            is VisitDetailEvent.RetryUpload -> retryUpload(event.report)

            is VisitDetailEvent.PickerUnavailable -> {
                setState { copy(isAttachSheetVisible = false) }
                sendEffect(VisitDetailEffect.ShowMessage(event.message))
            }
        }
    }

    private fun importFile(uri: Uri) {
        val patientId = currentState.visit?.visit?.patientId ?: return
        setState { copy(isAttachSheetVisible = false, isImporting = true) }

        viewModelScope.launch {
            val result = reportRepository.importReport(
                uri = uri,
                patientId = patientId,
                visitId = route.visitId,
            )
            setState { copy(isImporting = false) }

            // Whatever happened, the camera's plaintext copy has served its
            // purpose. Cleared on every path, including failure.
            cameraCaptureStore.clear()

            when (result) {
                is DataResult.Error ->
                    sendEffect(VisitDetailEffect.ShowMessage(result.error.toUserMessage()))

                is DataResult.Success -> when (val outcome = result.data) {
                    is ReportImportResult.Rejected ->
                        sendEffect(VisitDetailEffect.ShowMessage(outcome.message))

                    is ReportImportResult.Stored -> sendEffect(
                        VisitDetailEffect.ShowMessage(
                            if (outcome.wasCompressed) {
                                "Report attached, compressed to fit the 2 MB limit"
                            } else {
                                "Report attached"
                            },
                        ),
                    )
                }
            }
        }
    }

    private fun openReport(report: Report) {
        when {
            report.isRejected -> sendEffect(
                VisitDetailEffect.ShowMessage(
                    "This file was never saved - it was over the size limit.",
                ),
            )

            report.isViewable ->
                sendEffect(VisitDetailEffect.NavigateToReport(report.reportId))

            else -> sendEffect(
                VisitDetailEffect.ShowMessage(
                    "This report has not finished uploading from the device that added it.",
                ),
            )
        }
    }

    private fun retryUpload(report: Report) {
        viewModelScope.launch {
            when (val result = reportRepository.retryUpload(report.reportId)) {
                is DataResult.Success ->
                    sendEffect(VisitDetailEffect.ShowMessage("Upload queued"))

                is DataResult.Error ->
                    sendEffect(VisitDetailEffect.ShowMessage(result.error.toUserMessage()))
            }
        }
    }
}
