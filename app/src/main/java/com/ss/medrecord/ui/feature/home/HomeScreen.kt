package com.ss.medrecord.ui.feature.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ss.medrecord.domain.model.Patient
import com.ss.medrecord.domain.model.Relationship
import com.ss.medrecord.ui.components.ActivePatientChip
import com.ss.medrecord.ui.theme.MedRecordTheme

/**
 * Stateful entry point: owns the ViewModel and translates one-shot effects into
 * navigation calls. Kept thin so the stateless [HomeScreen] stays previewable.
 */
@Composable
fun HomeRoute(
    onNavigateToPatients: () -> Unit,
    onNavigateToSettings: () -> Unit,
    onNavigateToAddPatient: () -> Unit,
    viewModel: HomeViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    LaunchedEffect(Unit) {
        viewModel.effect.collect { effect ->
            when (effect) {
                HomeEffect.NavigateToPatients -> onNavigateToPatients()
                HomeEffect.NavigateToSettings -> onNavigateToSettings()
                HomeEffect.NavigateToAddPatient -> onNavigateToAddPatient()
            }
        }
    }

    HomeScreen(state = state, onEvent = viewModel::onEvent)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    state: HomeUiState,
    onEvent: (HomeEvent) -> Unit,
    modifier: Modifier = Modifier,
) {
    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = { Text(text = "MedRecord Keeper") },
                actions = {
                    // The persistent patient chip from spec 5.2: whose records
                    // are on screen is visible from every patient-scoped screen.
                    if (!state.needsFirstPatient) {
                        ActivePatientChip(
                            patient = state.activePatient,
                            onClick = { onEvent(HomeEvent.SwitchPatient) },
                            modifier = Modifier.padding(end = 12.dp),
                        )
                    }
                },
            )
        },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (state.needsFirstPatient) {
                FirstPatientCard(onAdd = { onEvent(HomeEvent.AddFirstPatient) })
            } else {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.secondaryContainer,
                    ),
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            text = state.activePatient?.name ?: "No patient selected",
                            style = MaterialTheme.typography.titleMedium,
                        )
                        Text(
                            text = activePatientSummary(state.activePatient),
                            style = MaterialTheme.typography.bodyMedium,
                        )
                        Text(
                            text = if (state.isOnline) "Online" else "Offline",
                            style = MaterialTheme.typography.labelSmall,
                            modifier = Modifier.padding(top = 8.dp),
                        )
                    }
                }

                Text(
                    text = "Visits, reports and reminders for this patient arrive in Phases 4 to 6.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            Button(
                onClick = { onEvent(HomeEvent.OpenPatients) },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(text = "Patients")
            }

            OutlinedButton(
                onClick = { onEvent(HomeEvent.OpenSettings) },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(text = "Settings")
            }
        }
    }
}

@Composable
private fun FirstPatientCard(onAdd: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.secondaryContainer,
        ),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(text = "Add your first patient", style = MaterialTheme.typography.titleMedium)
            Text(
                text = "Every record is filed under a patient profile. Start with yourself, " +
                    "or with whoever you are keeping records for.",
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(top = 4.dp),
            )
            Button(
                onClick = onAdd,
                modifier = Modifier.padding(top = 12.dp),
            ) {
                Text(text = "Add patient")
            }
        }
    }
}

private fun activePatientSummary(patient: Patient?): String {
    if (patient == null) return "Choose a patient to see their records."
    return listOfNotNull(
        patient.relationship.label,
        patient.ageYears()?.let { "$it yrs" },
        patient.bloodGroup?.label,
    ).joinToString(separator = " · ")
}

@Preview(showBackground = true)
@Composable
private fun HomeScreenPreview() {
    MedRecordTheme {
        HomeScreen(
            state = HomeUiState(
                patientCount = 2,
                activePatient = Patient(
                    patientId = "p1",
                    userId = "u1",
                    name = "Asha Rao",
                    relationship = Relationship.SELF,
                    dateOfBirthEpochDay = 10_000L,
                    createdAt = 0L,
                    updatedAt = 0L,
                ),
            ),
            onEvent = {},
        )
    }
}

@Preview(showBackground = true, name = "No patients yet")
@Composable
private fun HomeScreenEmptyPreview() {
    MedRecordTheme {
        HomeScreen(state = HomeUiState(), onEvent = {})
    }
}
