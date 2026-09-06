package com.ss.medrecord.ui.feature.report.list

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ss.medrecord.core.ui.components.EmptyState
import com.ss.medrecord.domain.model.Patient
import com.ss.medrecord.domain.model.Relationship
import com.ss.medrecord.domain.model.Report
import com.ss.medrecord.domain.model.ReportFileType
import com.ss.medrecord.domain.model.ReportWithContext
import com.ss.medrecord.domain.model.UploadStatus
import com.ss.medrecord.ui.components.ReportTile
import com.ss.medrecord.ui.theme.MedRecordTheme
import java.time.LocalDate
import java.time.format.DateTimeFormatter

@Composable
fun ReportListRoute(
    onNavigateBack: () -> Unit,
    onNavigateToViewer: (String) -> Unit,
    viewModel: ReportListViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(Unit) {
        viewModel.effect.collect { effect ->
            when (effect) {
                ReportListEffect.NavigateBack -> onNavigateBack()
                is ReportListEffect.NavigateToViewer -> onNavigateToViewer(effect.reportId)
                is ReportListEffect.ShowMessage -> snackbarHostState.showSnackbar(effect.message)
            }
        }
    }

    ReportListScreen(
        state = state,
        onEvent = viewModel::onEvent,
        loadThumbnail = viewModel::loadThumbnail,
        snackbarHostState = snackbarHostState,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReportListScreen(
    state: ReportListUiState,
    onEvent: (ReportListEvent) -> Unit,
    loadThumbnail: suspend (Report) -> ImageBitmap?,
    modifier: Modifier = Modifier,
    snackbarHostState: SnackbarHostState = remember { SnackbarHostState() },
) {
    state.pendingDeletion?.let { target ->
        DeleteReportDialog(
            entry = target,
            onConfirm = { onEvent(ReportListEvent.DeleteConfirmed) },
            onDismiss = { onEvent(ReportListEvent.DeleteDismissed) },
        )
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(text = "Reports")
                        Text(
                            text = if (state.showAllPatients) {
                                "All patients"
                            } else {
                                state.activePatient?.name.orEmpty()
                            },
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                },
                navigationIcon = {
                    TextButton(onClick = { onEvent(ReportListEvent.BackClicked) }) {
                        Text(text = "Back")
                    }
                },
                actions = {
                    TextButton(onClick = { onEvent(ReportListEvent.ToggleAllPatients) }) {
                        Text(text = if (state.showAllPatients) "This patient" else "All patients")
                    }
                },
            )
        },
    ) { innerPadding ->
        Column(modifier = Modifier.padding(innerPadding)) {
            FilterRow(state = state, onEvent = onEvent)

            when {
                state.hasNoPatient -> EmptyState(
                    title = "No patient selected",
                    description = "Choose a patient, or switch to all patients.",
                )

                state.isEmpty && state.hasFiltersApplied -> EmptyState(
                    title = "Nothing matches these filters",
                    description = "Clear a filter to see the rest of this patient's reports.",
                )

                state.isEmpty -> EmptyState(
                    title = "No reports yet",
                    description = "Attach a scan, prescription or lab result from a visit.",
                )

                else -> LazyVerticalGrid(
                    columns = GridCells.Adaptive(minSize = 160.dp),
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(16.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    items(items = state.reports, key = { it.report.reportId }) { entry ->
                        Column {
                            ReportTile(
                                report = entry.report,
                                subtitle = entry.subtitleFor(state.showAllPatients),
                                onClick = { onEvent(ReportListEvent.ReportClicked(entry)) },
                                loadThumbnail = loadThumbnail,
                                modifier = Modifier.fillMaxWidth(),
                            )
                            TileActions(entry = entry, onEvent = onEvent)
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun FilterRow(state: ReportListUiState, onEvent: (ReportListEvent) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        ReportFileType.entries.forEach { type ->
            FilterChip(
                selected = state.fileTypeFilter == type,
                onClick = {
                    onEvent(
                        ReportListEvent.FileTypeFilterChanged(
                            // Tapping the active chip clears it, so the filter
                            // row needs no separate "All" affordance.
                            type.takeIf { state.fileTypeFilter != type },
                        ),
                    )
                },
                label = { Text(text = type.label) },
            )
        }

        state.availableFacilities.forEach { facility ->
            FilterChip(
                selected = state.facilityFilter == facility,
                onClick = {
                    onEvent(
                        ReportListEvent.FacilityFilterChanged(
                            facility.takeIf { state.facilityFilter != facility },
                        ),
                    )
                },
                label = { Text(text = facility) },
            )
        }
    }
}

@Composable
private fun TileActions(entry: ReportWithContext, onEvent: (ReportListEvent) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.End,
    ) {
        if (entry.report.canRetryUpload) {
            TextButton(onClick = { onEvent(ReportListEvent.RetryUpload(entry)) }) {
                Text(text = "Retry")
            }
        }
        TextButton(onClick = { onEvent(ReportListEvent.DeleteRequested(entry)) }) {
            Text(text = "Delete", color = MaterialTheme.colorScheme.error)
        }
    }
}

@Composable
private fun DeleteReportDialog(
    entry: ReportWithContext,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(text = "Delete this report?") },
        text = {
            Text(
                text = "${entry.report.fileName} will be removed from this visit. " +
                    "Records are recoverable for 30 days.",
            )
        },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text(text = "Delete", color = MaterialTheme.colorScheme.error)
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(text = "Cancel") } },
    )
}

private val DATE_FORMAT: DateTimeFormatter = DateTimeFormatter.ofPattern("d MMM yyyy")

/**
 * What a tile says under its name: where the report came from, and - only when
 * the grid spans patients - whose it is. Repeating the patient name on every
 * tile in a single-patient view is noise.
 */
private fun ReportWithContext.subtitleFor(showAllPatients: Boolean): String {
    val date = visitDateEpochDay?.let { LocalDate.ofEpochDay(it).format(DATE_FORMAT) }
    val place = facilityName
    val who = patientName?.takeIf { showAllPatients }
    return listOfNotNull(who, place, date).joinToString(separator = " - ")
}

@Preview(showBackground = true)
@Composable
private fun ReportListScreenPreview() {
    fun report(id: String, name: String, type: ReportFileType, status: UploadStatus) = Report(
        reportId = id,
        userId = "u1",
        patientId = "p1",
        visitId = "v1",
        fileName = name,
        fileType = type,
        fileSizeBytes = 812_004,
        uploadStatus = status,
        createdAt = 0L,
        updatedAt = 0L,
    )

    MedRecordTheme {
        ReportListScreen(
            state = ReportListUiState(
                isLoading = false,
                activePatient = Patient(
                    patientId = "p1",
                    userId = "u1",
                    name = "Asha Rao",
                    relationship = Relationship.SELF,
                    createdAt = 0L,
                    updatedAt = 0L,
                ),
                allReports = listOf(
                    ReportWithContext(
                        report = report("r1", "blood-panel.pdf", ReportFileType.PDF, UploadStatus.UPLOADED),
                        visitDateEpochDay = LocalDate.now().toEpochDay(),
                        facilityName = "City Care Clinic",
                        patientName = "Asha Rao",
                    ),
                    ReportWithContext(
                        report = report("r2", "xray.jpg", ReportFileType.IMAGE, UploadStatus.FAILED),
                        visitDateEpochDay = LocalDate.now().minusDays(30).toEpochDay(),
                        facilityName = "St Mary Hospital",
                        patientName = "Asha Rao",
                    ),
                ),
            ),
            onEvent = {},
            loadThumbnail = { null },
        )
    }
}
