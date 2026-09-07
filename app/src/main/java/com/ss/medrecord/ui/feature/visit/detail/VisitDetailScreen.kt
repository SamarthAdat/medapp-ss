package com.ss.medrecord.ui.feature.visit.detail

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import com.ss.medrecord.core.ui.components.FullScreenLoading
import com.ss.medrecord.domain.model.Facility
import com.ss.medrecord.domain.model.FacilityType
import com.ss.medrecord.domain.model.Medicine
import com.ss.medrecord.domain.model.Report
import com.ss.medrecord.domain.model.Visit
import com.ss.medrecord.domain.model.VisitWithFacility
import com.ss.medrecord.ui.components.MedicineTile
import com.ss.medrecord.ui.components.ReportTile
import com.ss.medrecord.ui.theme.MedRecordTheme
import java.time.LocalDate
import java.time.format.DateTimeFormatter

@Composable
fun VisitDetailRoute(
    onNavigateBack: () -> Unit,
    onEdit: (String) -> Unit,
    onOpenReport: (String) -> Unit,
    onAddMedicine: (String) -> Unit,
    onOpenMedicine: (String) -> Unit,
    viewModel: VisitDetailViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(Unit) {
        viewModel.effect.collect { effect ->
            when (effect) {
                VisitDetailEffect.NavigateBack -> onNavigateBack()
                is VisitDetailEffect.NavigateToEdit -> onEdit(effect.visitId)
                is VisitDetailEffect.NavigateToReport -> onOpenReport(effect.reportId)
                is VisitDetailEffect.NavigateToAddMedicine -> onAddMedicine(effect.visitId)
                is VisitDetailEffect.NavigateToMedicine -> onOpenMedicine(effect.medicineId)
                is VisitDetailEffect.ShowMessage -> snackbarHostState.showSnackbar(effect.message)
            }
        }
    }

    if (state.isAttachSheetVisible) {
        AttachReportSheet(
            onFileSelected = { uri -> viewModel.onEvent(VisitDetailEvent.FileSelected(uri)) },
            onDismiss = { viewModel.onEvent(VisitDetailEvent.AttachDismissed) },
            createCaptureUri = viewModel::newCaptureUri,
            onPickerUnavailable = { message ->
                viewModel.onEvent(VisitDetailEvent.PickerUnavailable(message))
            },
        )
    }

    VisitDetailScreen(
        state = state,
        onEvent = viewModel::onEvent,
        loadThumbnail = viewModel::loadThumbnail,
        snackbarHostState = snackbarHostState,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VisitDetailScreen(
    state: VisitDetailUiState,
    onEvent: (VisitDetailEvent) -> Unit,
    modifier: Modifier = Modifier,
    loadThumbnail: suspend (Report) -> ImageBitmap? = { null },
    snackbarHostState: SnackbarHostState = remember { SnackbarHostState() },
) {
    Scaffold(
        modifier = modifier.fillMaxSize(),
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { Text(text = "Visit") },
                navigationIcon = {
                    TextButton(onClick = { onEvent(VisitDetailEvent.BackClicked) }) {
                        Text(text = "Back")
                    }
                },
                actions = {
                    if (state.visit != null) {
                        TextButton(onClick = { onEvent(VisitDetailEvent.EditClicked) }) {
                            Text(text = "Edit")
                        }
                    }
                },
            )
        },
    ) { innerPadding ->
        val visit = state.visit
        when {
            state.isLoading -> FullScreenLoading(modifier = Modifier.padding(innerPadding))

            visit == null -> Text(
                text = "This visit is no longer available.",
                modifier = Modifier
                    .padding(innerPadding)
                    .padding(24.dp),
            )

            else -> Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .verticalScroll(rememberScrollState())
                    .padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.secondaryContainer,
                    ),
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            text = visit.facilityName,
                            style = MaterialTheme.typography.titleLarge,
                        )
                        visit.facility?.let { facility ->
                            Text(
                                text = facility.type.label,
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            facility.address?.takeIf { it.isNotBlank() }?.let { address ->
                                Text(
                                    text = address,
                                    style = MaterialTheme.typography.bodySmall,
                                    modifier = Modifier.padding(top = 4.dp),
                                )
                            }
                            facility.phone?.takeIf { it.isNotBlank() }?.let { phone ->
                                Text(text = phone, style = MaterialTheme.typography.bodySmall)
                            }
                        }
                    }
                }

                DetailRow(label = "Visit date", value = formatDate(visit.visit.visitDateEpochDay))

                visit.visit.doctorName?.takeIf { it.isNotBlank() }?.let { doctor ->
                    DetailRow(label = "Doctor", value = doctor)
                }

                state.patientName?.let { name ->
                    DetailRow(label = "Patient", value = name)
                }

                visit.visit.nextVisitDateEpochDay?.let { next ->
                    val days = visit.visit.daysUntilNextVisit()
                    DetailRow(
                        label = "Next visit",
                        value = buildString {
                            append(formatDate(next))
                            when {
                                days == null -> Unit
                                days < 0 -> append("  (passed)")
                                days == 0L -> append("  (today)")
                                days == 1L -> append("  (tomorrow)")
                                else -> append("  (in $days days)")
                            }
                        },
                    )
                }

                visit.visit.notes?.takeIf { it.isNotBlank() }?.let { notes ->
                    Column(modifier = Modifier.padding(top = 4.dp)) {
                        Text(
                            text = "Diagnosis and notes",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Text(
                            text = notes,
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.padding(top = 4.dp),
                        )
                    }
                }

                ReportsSection(
                    reports = state.reports,
                    isImporting = state.isImporting,
                    onEvent = onEvent,
                    loadThumbnail = loadThumbnail,
                )

                MedicinesSection(medicines = state.medicines, onEvent = onEvent)

                OutlinedButton(
                    onClick = { onEvent(VisitDetailEvent.EditClicked) },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(text = "Edit visit")
                }
            }
        }
    }
}

/**
 * Attached reports (spec section 5.6). A column of full-width tiles rather than
 * a grid: this screen already scrolls vertically, and a nested scrolling grid
 * inside it would fight the parent for the same gesture.
 */
@Composable
private fun ReportsSection(
    reports: List<Report>,
    isImporting: Boolean,
    onEvent: (VisitDetailEvent) -> Unit,
    loadThumbnail: suspend (Report) -> ImageBitmap?,
) {
    Column(modifier = Modifier.padding(top = 12.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = if (reports.isEmpty()) "Reports" else "Reports (${reports.size})",
                style = MaterialTheme.typography.titleMedium,
            )
            if (isImporting) {
                CircularProgressIndicator(modifier = Modifier.padding(end = 8.dp))
            } else {
                TextButton(onClick = { onEvent(VisitDetailEvent.AttachClicked) }) {
                    Text(text = "Attach")
                }
            }
        }

        if (reports.isEmpty()) {
            Text(
                text = "Scans, prescriptions and lab results attached to this visit " +
                    "appear here.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            return@Column
        }

        reports.forEach { report ->
            Column(modifier = Modifier.padding(top = 8.dp)) {
                ReportTile(
                    report = report,
                    subtitle = null,
                    onClick = { onEvent(VisitDetailEvent.ReportClicked(report)) },
                    loadThumbnail = loadThumbnail,
                    modifier = Modifier.fillMaxWidth(),
                )
                if (report.canRetryUpload) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End,
                    ) {
                        TextButton(onClick = { onEvent(VisitDetailEvent.RetryUpload(report)) }) {
                            Text(text = "Retry upload")
                        }
                    }
                }
            }
        }
    }
}

/**
 * Medicines prescribed at this visit (spec section 5.6).
 *
 * Only the ones linked to this appointment. A patient's full list lives on the
 * medicines screen; repeating it here would make an unrelated long-term
 * prescription look like something this doctor started.
 */
@Composable
private fun MedicinesSection(
    medicines: List<Medicine>,
    onEvent: (VisitDetailEvent) -> Unit,
) {
    Column(modifier = Modifier.padding(top = 12.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = if (medicines.isEmpty()) {
                    "Medicines"
                } else {
                    "Medicines (${medicines.size})"
                },
                style = MaterialTheme.typography.titleMedium,
            )
            TextButton(onClick = { onEvent(VisitDetailEvent.AddMedicineClicked) }) {
                Text(text = "Add")
            }
        }

        if (medicines.isEmpty()) {
            Text(
                text = "Anything prescribed at this visit can be added here and " +
                    "reminded about.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            return@Column
        }

        medicines.forEach { medicine ->
            MedicineTile(
                medicine = medicine,
                subtitle = null,
                onClick = { onEvent(VisitDetailEvent.MedicineClicked(medicine)) },
                // The toggle is deliberately inert here: pausing a course is a
                // decision about the medicine, not about this visit record, and
                // it belongs on the screen that shows the whole schedule.
                onToggleActive = { onEvent(VisitDetailEvent.MedicineClicked(medicine)) },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp),
            )
        }
    }
}

@Composable
private fun DetailRow(label: String, value: String) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(text = value, style = MaterialTheme.typography.bodyLarge)
    }
}

private val DATE_FORMAT: DateTimeFormatter = DateTimeFormatter.ofPattern("d MMM yyyy")

private fun formatDate(epochDay: Long): String =
    LocalDate.ofEpochDay(epochDay).format(DATE_FORMAT)

@Preview(showBackground = true)
@Composable
private fun VisitDetailScreenPreview() {
    MedRecordTheme {
        VisitDetailScreen(
            state = VisitDetailUiState(
                isLoading = false,
                patientName = "Asha Rao",
                visit = VisitWithFacility(
                    visit = Visit(
                        visitId = "v1",
                        userId = "u1",
                        patientId = "p1",
                        facilityId = "f1",
                        doctorName = "Dr Mehta",
                        visitDateEpochDay = LocalDate.now().toEpochDay(),
                        notes = "Routine check-up. Blood work ordered, results next week.",
                        nextVisitDateEpochDay = LocalDate.now().plusDays(7).toEpochDay(),
                        createdAt = 0L,
                        updatedAt = 0L,
                    ),
                    facility = Facility(
                        facilityId = "f1",
                        userId = "u1",
                        name = "City Care Clinic",
                        type = FacilityType.CLINIC,
                        address = "12 MG Road, Bengaluru",
                        phone = "+91 80 1234 5678",
                        createdAt = 0L,
                        updatedAt = 0L,
                    ),
                ),
            ),
            onEvent = {},
        )
    }
}
