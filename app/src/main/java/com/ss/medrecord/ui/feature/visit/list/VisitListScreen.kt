package com.ss.medrecord.ui.feature.visit.list

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
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
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ss.medrecord.core.ui.components.EmptyState
import com.ss.medrecord.domain.model.Facility
import com.ss.medrecord.domain.model.FacilityType
import com.ss.medrecord.domain.model.Patient
import com.ss.medrecord.domain.model.Relationship
import com.ss.medrecord.domain.model.Visit
import com.ss.medrecord.domain.model.VisitWithFacility
import com.ss.medrecord.ui.theme.MedRecordTheme
import java.time.LocalDate
import java.time.format.DateTimeFormatter

@Composable
fun VisitListRoute(
    onNavigateBack: () -> Unit,
    onNavigateToDetail: (String) -> Unit,
    onNavigateToEdit: (String?) -> Unit,
    viewModel: VisitListViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(Unit) {
        viewModel.effect.collect { effect ->
            when (effect) {
                VisitListEffect.NavigateBack -> onNavigateBack()
                is VisitListEffect.NavigateToDetail -> onNavigateToDetail(effect.visitId)
                is VisitListEffect.NavigateToEdit -> onNavigateToEdit(effect.visitId)
                is VisitListEffect.ShowMessage -> snackbarHostState.showSnackbar(effect.message)
            }
        }
    }

    VisitListScreen(
        state = state,
        onEvent = viewModel::onEvent,
        snackbarHostState = snackbarHostState,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VisitListScreen(
    state: VisitListUiState,
    onEvent: (VisitListEvent) -> Unit,
    modifier: Modifier = Modifier,
    snackbarHostState: SnackbarHostState = remember { SnackbarHostState() },
) {
    state.pendingDeletion?.let { target ->
        DeleteVisitDialog(
            visit = target,
            onConfirm = { onEvent(VisitListEvent.DeleteConfirmed) },
            onDismiss = { onEvent(VisitListEvent.DeleteDismissed) },
        )
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(text = "Visits")
                        state.activePatient?.let { patient ->
                            Text(
                                text = patient.name,
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                },
                navigationIcon = {
                    TextButton(onClick = { onEvent(VisitListEvent.BackClicked) }) {
                        Text(text = "Back")
                    }
                },
                actions = {
                    if (state.visits.isNotEmpty()) {
                        TextButton(onClick = { onEvent(VisitListEvent.ToggleGrouping) }) {
                            Text(text = if (state.groupByFacility) "By date" else "By clinic")
                        }
                    }
                },
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { onEvent(VisitListEvent.AddVisit) },
                text = { Text(text = "Add visit") },
                icon = { Text(text = "+", style = MaterialTheme.typography.titleLarge) },
            )
        },
    ) { innerPadding ->
        when {
            state.hasNoPatient -> EmptyState(
                title = "No patient selected",
                description = "Add or choose a patient first. Every visit is filed under one.",
                modifier = Modifier.padding(innerPadding),
            )

            state.isEmpty -> EmptyState(
                title = "No visits recorded",
                description = "Log a clinic or hospital visit to start building this patient's history.",
                modifier = Modifier.padding(innerPadding),
            )

            else -> LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
                // Bottom padding clears the FAB so the last card is reachable.
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 88.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                if (state.groupByFacility) {
                    state.groupedByFacility.forEach { (facilityName, visits) ->
                        item(key = "header-$facilityName") {
                            Text(
                                text = facilityName,
                                style = MaterialTheme.typography.titleSmall,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.padding(top = 12.dp, bottom = 2.dp),
                            )
                        }
                        items(items = visits, key = { it.visit.visitId }) { visit ->
                            VisitCard(visit = visit, showFacility = false, onEvent = onEvent)
                        }
                    }
                } else {
                    items(items = state.visits, key = { it.visit.visitId }) { visit ->
                        VisitCard(visit = visit, showFacility = true, onEvent = onEvent)
                    }
                }
            }
        }
    }
}

@Composable
private fun VisitCard(
    visit: VisitWithFacility,
    showFacility: Boolean,
    onEvent: (VisitListEvent) -> Unit,
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onEvent(VisitListEvent.VisitClicked(visit)) },
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant,
        ),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    if (showFacility) {
                        Text(
                            text = visit.facilityName,
                            style = MaterialTheme.typography.titleMedium,
                        )
                    }
                    Text(
                        text = formatDate(visit.visit.visitDateEpochDay),
                        style = if (showFacility) {
                            MaterialTheme.typography.bodyMedium
                        } else {
                            MaterialTheme.typography.titleMedium
                        },
                    )
                    visit.visit.doctorName?.takeIf { it.isNotBlank() }?.let { doctor ->
                        Text(
                            text = doctor,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }

                NextVisitBadge(visit.visit)
            }

            visit.visit.notes?.takeIf { it.isNotBlank() }?.let { notes ->
                Text(
                    text = notes,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    modifier = Modifier.padding(top = 8.dp),
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
            ) {
                TextButton(onClick = { onEvent(VisitListEvent.EditVisit(visit)) }) {
                    Text(text = "Edit")
                }
                TextButton(onClick = { onEvent(VisitListEvent.DeleteRequested(visit)) }) {
                    Text(text = "Delete", color = MaterialTheme.colorScheme.error)
                }
            }
        }
    }
}

/** Only shown for a follow-up that has not passed; a stale date is noise. */
@Composable
private fun NextVisitBadge(visit: Visit) {
    val days = visit.daysUntilNextVisit() ?: return
    if (days < 0) return

    AssistChip(
        onClick = {},
        enabled = false,
        label = {
            Text(
                text = when (days) {
                    0L -> "Today"
                    1L -> "Tomorrow"
                    else -> "In $days days"
                },
                style = MaterialTheme.typography.labelSmall,
            )
        },
    )
}

@Composable
private fun DeleteVisitDialog(
    visit: VisitWithFacility,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(text = "Delete this visit?") },
        text = {
            Text(
                text = "The visit at ${visit.facilityName} on " +
                    "${formatDate(visit.visit.visitDateEpochDay)} will be removed, along with " +
                    "its reports and medicines. Records are recoverable for 30 days.",
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

internal fun formatDate(epochDay: Long): String =
    LocalDate.ofEpochDay(epochDay).format(DATE_FORMAT)

@Preview(showBackground = true)
@Composable
private fun VisitListScreenPreview() {
    val facility = Facility(
        facilityId = "f1",
        userId = "u1",
        name = "City Care Clinic",
        type = FacilityType.CLINIC,
        createdAt = 0L,
        updatedAt = 0L,
    )
    MedRecordTheme {
        VisitListScreen(
            state = VisitListUiState(
                isLoading = false,
                activePatient = Patient(
                    patientId = "p1",
                    userId = "u1",
                    name = "Asha Rao",
                    relationship = Relationship.SELF,
                    createdAt = 0L,
                    updatedAt = 0L,
                ),
                visits = listOf(
                    VisitWithFacility(
                        visit = Visit(
                            visitId = "v1",
                            userId = "u1",
                            patientId = "p1",
                            facilityId = "f1",
                            doctorName = "Dr Mehta",
                            visitDateEpochDay = LocalDate.now().toEpochDay(),
                            notes = "Routine check-up, blood work ordered.",
                            nextVisitDateEpochDay = LocalDate.now().plusDays(14).toEpochDay(),
                            createdAt = 0L,
                            updatedAt = 0L,
                        ),
                        facility = facility,
                    ),
                ),
            ),
            onEvent = {},
        )
    }
}
