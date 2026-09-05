package com.ss.medrecord.ui.feature.patient.list

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ss.medrecord.core.ui.components.EmptyState
import com.ss.medrecord.domain.model.Patient
import com.ss.medrecord.domain.model.Relationship
import com.ss.medrecord.ui.components.PatientAvatar
import com.ss.medrecord.ui.theme.MedRecordTheme

@Composable
fun PatientListRoute(
    onNavigateBack: () -> Unit,
    onNavigateToEdit: (String?) -> Unit,
    onPatientActivated: () -> Unit,
    viewModel: PatientListViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(Unit) {
        viewModel.effect.collect { effect ->
            when (effect) {
                PatientListEffect.NavigateBack -> onNavigateBack()
                is PatientListEffect.NavigateToEdit -> onNavigateToEdit(effect.patientId)
                PatientListEffect.PatientActivated -> onPatientActivated()
                is PatientListEffect.ShowMessage -> snackbarHostState.showSnackbar(effect.message)
            }
        }
    }

    PatientListScreen(
        state = state,
        onEvent = viewModel::onEvent,
        snackbarHostState = snackbarHostState,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PatientListScreen(
    state: PatientListUiState,
    onEvent: (PatientListEvent) -> Unit,
    modifier: Modifier = Modifier,
    snackbarHostState: SnackbarHostState = remember { SnackbarHostState() },
) {
    state.pendingDeletion?.let { patient ->
        DeleteConfirmationDialog(
            patient = patient,
            onConfirm = { onEvent(PatientListEvent.DeleteConfirmed) },
            onDismiss = { onEvent(PatientListEvent.DeleteDismissed) },
        )
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { Text(text = "Patients") },
                navigationIcon = {
                    TextButton(onClick = { onEvent(PatientListEvent.BackClicked) }) {
                        Text(text = "Back")
                    }
                },
                actions = {
                    if (state.hasArchived) {
                        TextButton(onClick = { onEvent(PatientListEvent.ToggleShowArchived) }) {
                            Text(text = if (state.showArchived) "Hide archived" else "Archived")
                        }
                    }
                },
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { onEvent(PatientListEvent.AddPatient) },
                text = { Text(text = "Add patient") },
                icon = { Text(text = "+", style = MaterialTheme.typography.titleLarge) },
            )
        },
    ) { innerPadding ->
        if (state.isEmpty && !state.showArchived) {
            EmptyState(
                title = "No patients yet",
                description = "Add a profile for yourself or a family member to start keeping records.",
                modifier = Modifier.padding(innerPadding),
            )
            return@Scaffold
        }

        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(
                start = 16.dp,
                end = 16.dp,
                top = 8.dp,
                // Clears the FAB so the last card is never trapped underneath it.
                bottom = 88.dp,
            ),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            items(items = state.patients, key = { it.patientId }) { patient ->
                PatientCard(
                    patient = patient,
                    isActive = patient.patientId == state.activePatientId,
                    onEvent = onEvent,
                )
            }

            if (state.showArchived && state.archivedPatients.isNotEmpty()) {
                item {
                    Text(
                        text = "Archived",
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 16.dp, bottom = 4.dp),
                    )
                }
                items(items = state.archivedPatients, key = { it.patientId }) { patient ->
                    PatientCard(patient = patient, isActive = false, onEvent = onEvent)
                }
            }
        }
    }
}

@Composable
private fun PatientCard(
    patient: Patient,
    isActive: Boolean,
    onEvent: (PatientListEvent) -> Unit,
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = !patient.isArchived) {
                onEvent(PatientListEvent.PatientSelected(patient))
            },
        colors = CardDefaults.cardColors(
            containerColor = if (isActive) {
                MaterialTheme.colorScheme.secondaryContainer
            } else {
                MaterialTheme.colorScheme.surfaceVariant
            },
        ),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                PatientAvatar(patient = patient)
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .padding(start = 12.dp),
                ) {
                    Text(
                        text = patient.name,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = if (isActive) FontWeight.SemiBold else FontWeight.Normal,
                    )
                    Text(
                        text = patientSubtitle(patient),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                if (isActive) {
                    Text(
                        text = "Active",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
            ) {
                TextButton(onClick = { onEvent(PatientListEvent.EditPatient(patient)) }) {
                    Text(text = "Edit")
                }
                TextButton(onClick = { onEvent(PatientListEvent.ArchiveToggled(patient)) }) {
                    Text(text = if (patient.isArchived) "Restore" else "Archive")
                }
                TextButton(onClick = { onEvent(PatientListEvent.DeleteRequested(patient)) }) {
                    Text(text = "Delete", color = MaterialTheme.colorScheme.error)
                }
            }
        }
    }
}

/** "Self - 34 yrs - O+", skipping whatever was not recorded. */
private fun patientSubtitle(patient: Patient): String = listOfNotNull(
    patient.relationship.label,
    patient.ageYears()?.let { "$it yrs" },
    patient.bloodGroup?.label,
).joinToString(separator = " · ")

@Composable
private fun DeleteConfirmationDialog(
    patient: Patient,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(text = "Delete ${patient.name}?") },
        text = {
            Text(
                text = "This removes the profile and every visit, report and reminder " +
                    "under it. The records are recoverable for 30 days before being " +
                    "permanently erased.",
            )
        },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text(text = "Delete", color = MaterialTheme.colorScheme.error)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(text = "Cancel") }
        },
    )
}

@Preview(showBackground = true)
@Composable
private fun PatientListScreenPreview() {
    MedRecordTheme {
        PatientListScreen(
            state = PatientListUiState(
                isLoading = false,
                activePatientId = "p1",
                patients = listOf(
                    Patient(
                        patientId = "p1",
                        userId = "u1",
                        name = "Asha Rao",
                        relationship = Relationship.SELF,
                        dateOfBirthEpochDay = 10_000L,
                        createdAt = 0L,
                        updatedAt = 0L,
                    ),
                    Patient(
                        patientId = "p2",
                        userId = "u1",
                        name = "Vikram Rao",
                        relationship = Relationship.CHILD,
                        createdAt = 0L,
                        updatedAt = 0L,
                    ),
                ),
            ),
            onEvent = {},
        )
    }
}
