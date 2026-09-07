package com.ss.medrecord.ui.feature.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ss.medrecord.domain.model.DashboardSnapshot
import com.ss.medrecord.domain.model.Patient
import com.ss.medrecord.domain.model.RecordCounts
import com.ss.medrecord.domain.model.Relationship
import com.ss.medrecord.domain.model.TimelineEntry
import com.ss.medrecord.domain.model.TimelineKind
import com.ss.medrecord.domain.model.UpcomingAppointment
import com.ss.medrecord.ui.components.ActivePatientChip
import com.ss.medrecord.ui.components.SyncStatusIndicator
import com.ss.medrecord.ui.components.TimelineRow
import com.ss.medrecord.ui.theme.MedRecordTheme
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * Stateful entry point: owns the ViewModel and translates one-shot effects into
 * navigation calls. Kept thin so the stateless [HomeScreen] stays previewable.
 */
@Composable
fun HomeRoute(
    onNavigateToPatients: () -> Unit,
    onNavigateToSettings: () -> Unit,
    onNavigateToAddPatient: () -> Unit,
    onNavigateToVisits: () -> Unit,
    onNavigateToAddVisit: () -> Unit,
    onNavigateToReports: () -> Unit,
    onNavigateToMedicines: () -> Unit,
    onNavigateToTimeline: () -> Unit,
    onNavigateToFacilities: () -> Unit,
    onOpenVisit: (String) -> Unit,
    onOpenReport: (String) -> Unit,
    onOpenMedicine: (String) -> Unit,
    viewModel: HomeViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(Unit) {
        viewModel.effect.collect { effect ->
            when (effect) {
                HomeEffect.NavigateToPatients -> onNavigateToPatients()
                HomeEffect.NavigateToSettings -> onNavigateToSettings()
                HomeEffect.NavigateToAddPatient -> onNavigateToAddPatient()
                HomeEffect.NavigateToVisits -> onNavigateToVisits()
                HomeEffect.NavigateToAddVisit -> onNavigateToAddVisit()
                HomeEffect.NavigateToReports -> onNavigateToReports()
                HomeEffect.NavigateToMedicines -> onNavigateToMedicines()
                HomeEffect.NavigateToTimeline -> onNavigateToTimeline()
                HomeEffect.NavigateToFacilities -> onNavigateToFacilities()
                is HomeEffect.NavigateToVisit -> onOpenVisit(effect.visitId)
                is HomeEffect.NavigateToReport -> onOpenReport(effect.reportId)
                is HomeEffect.NavigateToMedicine -> onOpenMedicine(effect.medicineId)
                is HomeEffect.ShowMessage -> snackbarHostState.showSnackbar(effect.message)
            }
        }
    }

    HomeScreen(
        state = state,
        onEvent = viewModel::onEvent,
        snackbarHostState = snackbarHostState,
    )
}

/**
 * The dashboard (spec section 5.3).
 *
 * Ordered by how time-sensitive each section is rather than by how the data is
 * stored: anything to act on today comes first, then who is on screen, then
 * what is on file, then history. A dashboard that opens with a record count is
 * one nobody reads twice.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    state: HomeUiState,
    onEvent: (HomeEvent) -> Unit,
    modifier: Modifier = Modifier,
    snackbarHostState: SnackbarHostState = remember { SnackbarHostState() },
) {
    Scaffold(
        modifier = modifier.fillMaxSize(),
        snackbarHost = { SnackbarHost(snackbarHostState) },
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
        val dashboard = state.dashboard

        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                // Tapping the indicator is the manual "Sync now" from spec 5.13.
                SyncStatusIndicator(
                    status = state.syncStatus,
                    onClick = { onEvent(HomeEvent.SyncNowClicked) },
                )
            }

            if (state.needsFirstPatient) {
                item { FirstPatientCard(onAdd = { onEvent(HomeEvent.AddFirstPatient) }) }
            } else {
                if (dashboard.hasUpcoming) {
                    item { UpcomingCard(dashboard = dashboard, onEvent = onEvent) }
                }

                item { HealthSummaryCard(state = state) }

                item { CountsRow(counts = dashboard.counts, onEvent = onEvent) }

                item {
                    Button(
                        onClick = { onEvent(HomeEvent.AddVisit) },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(text = "Add visit")
                    }
                }

                if (dashboard.recentActivity.isNotEmpty()) {
                    item { RecentActivityHeader(onEvent = onEvent) }

                    items(dashboard.recentActivity, key = { it.id }) { entry ->
                        TimelineRow(
                            entry = entry,
                            onClick = { onEvent(HomeEvent.ActivityClicked(entry)) },
                            // Recent activity spans the whole account, so a row
                            // that did not say whose it is would be ambiguous the
                            // moment there is more than one profile.
                            showPatientName = true,
                        )
                    }
                }
            }

            item {
                OutlinedButton(
                    onClick = { onEvent(HomeEvent.OpenPatients) },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(text = "Patients")
                }
            }

            item {
                OutlinedButton(
                    onClick = { onEvent(HomeEvent.OpenFacilities) },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(text = "Clinics and hospitals")
                }
            }

            item {
                OutlinedButton(
                    onClick = { onEvent(HomeEvent.OpenSettings) },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(text = "Settings")
                }
            }
        }
    }
}

/**
 * Appointments and doses still ahead. Shown only when there is something in it -
 * an empty "nothing due" card every evening teaches the user to skip this part
 * of the screen, which is the one part that must never be skipped.
 */
@Composable
private fun UpcomingCard(
    dashboard: DashboardSnapshot,
    onEvent: (HomeEvent) -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer,
        ),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = "Coming up",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
            )

            dashboard.upcomingAppointments.forEach { appointment ->
                AppointmentRow(
                    appointment = appointment,
                    onClick = { onEvent(HomeEvent.AppointmentClicked(appointment.visitId)) },
                )
            }

            if (dashboard.dosesDueToday.isNotEmpty()) {
                Text(
                    text = if (dashboard.dosesDueToday.size == 1) {
                        "1 dose still due today"
                    } else {
                        "${dashboard.dosesDueToday.size} doses still due today"
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                    modifier = Modifier.padding(top = 8.dp),
                )
                dashboard.dosesDueToday.take(MAX_DOSES_LISTED).forEach { reminder ->
                    Text(
                        text = "${formatClock(reminder.triggerAtMillis)}  ${reminder.title}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                    )
                }
                TextButton(onClick = { onEvent(HomeEvent.OpenMedicines) }) {
                    Text(text = "View medicines")
                }
            }
        }
    }
}

@Composable
private fun AppointmentRow(appointment: UpcomingAppointment, onClick: () -> Unit) {
    val days = appointment.daysAway()
    val whenLabel = when (days) {
        0L -> "Today"
        1L -> "Tomorrow"
        else -> "In $days days"
    }

    TextButton(
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 4.dp),
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Text(
                text = "$whenLabel · ${appointment.facilityName ?: "Unknown facility"}",
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
            )
            Text(
                text = listOfNotNull(
                    appointment.patientName,
                    appointment.doctorName,
                    appointment.date.format(DATE_FORMAT),
                ).joinToString(" · "),
                style = MaterialTheme.typography.bodySmall,
            )
        }
    }
}

/**
 * Who is on screen, and the handful of facts someone else would need in an
 * emergency. Allergies get their own line and the error colour: it is the one
 * field here whose absence changes what a clinician should do.
 */
@Composable
private fun HealthSummaryCard(state: HomeUiState) {
    val patient = state.activePatient

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.secondaryContainer,
        ),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = patient?.name ?: "No patient selected",
                style = MaterialTheme.typography.titleMedium,
            )
            Text(
                text = summaryLine(patient),
                style = MaterialTheme.typography.bodyMedium,
            )

            state.allergies?.let { allergies ->
                HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                Text(
                    text = "Allergies",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.error,
                )
                Text(
                    text = allergies,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.error,
                )
            }
        }
    }
}

@Composable
private fun CountsRow(counts: RecordCounts, onEvent: (HomeEvent) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        CountTile(
            label = "Visits",
            value = counts.visits,
            onClick = { onEvent(HomeEvent.OpenVisits) },
            modifier = Modifier.weight(1f),
        )
        CountTile(
            label = "Reports",
            value = counts.reports,
            onClick = { onEvent(HomeEvent.OpenReports) },
            modifier = Modifier.weight(1f),
        )
        CountTile(
            label = "Medicines",
            value = counts.activeMedicines,
            onClick = { onEvent(HomeEvent.OpenMedicines) },
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
private fun CountTile(
    label: String,
    value: Int,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Card(
        onClick = onClick,
        modifier = modifier,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant,
        ),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 14.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(text = value.toString(), style = MaterialTheme.typography.headlineSmall)
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun RecentActivityHeader(onEvent: (HomeEvent) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(text = "Recent activity", style = MaterialTheme.typography.titleMedium)
        TextButton(onClick = { onEvent(HomeEvent.OpenTimeline) }) {
            Text(text = "See all")
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
                text = "Every record is kept against a person. Start with yourself, " +
                    "then add family or dependents.",
                style = MaterialTheme.typography.bodyMedium,
                textAlign = TextAlign.Start,
                modifier = Modifier.padding(top = 4.dp),
            )
            Button(
                onClick = onAdd,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 12.dp),
            ) {
                Text(text = "Add patient")
            }
        }
    }
}

private fun summaryLine(patient: Patient?): String {
    if (patient == null) return "Choose a profile to see their records."
    val parts = buildList {
        add(patient.relationship.label)
        patient.ageYears()?.let { add("$it years") }
        patient.gender?.let { add(it.label) }
        patient.bloodGroup?.let { add(it.label) }
    }
    return parts.joinToString(" · ")
}

private fun formatClock(millis: Long): String {
    val time = Instant.ofEpochMilli(millis).atZone(ZoneId.systemDefault()).toLocalTime()
    return "%02d:%02d".format(time.hour, time.minute)
}

private const val MAX_DOSES_LISTED = 3

private val DATE_FORMAT: DateTimeFormatter = DateTimeFormatter.ofPattern("d MMM yyyy")

@Preview(showBackground = true)
@Composable
private fun HomeScreenPreview() {
    val today = LocalDate.now()
    MedRecordTheme {
        HomeScreen(
            state = HomeUiState(
                isLoading = false,
                dashboard = DashboardSnapshot(
                    activePatient = Patient(
                        patientId = "p1",
                        userId = "u1",
                        name = "Asha Rao",
                        relationship = Relationship.SELF,
                        dateOfBirthEpochDay = today.minusYears(41).toEpochDay(),
                        knownAllergies = "Penicillin, sulfa drugs",
                        createdAt = 0L,
                        updatedAt = 0L,
                    ),
                    patientCount = 3,
                    counts = RecordCounts(visits = 12, reports = 7, activeMedicines = 2),
                    upcomingAppointments = listOf(
                        UpcomingAppointment(
                            visitId = "v1",
                            patientId = "p1",
                            patientName = "Asha Rao",
                            facilityName = "City Care Clinic",
                            doctorName = "Dr Mehta",
                            onEpochDay = today.plusDays(2).toEpochDay(),
                        ),
                    ),
                    recentActivity = listOf(
                        TimelineEntry(
                            id = "VISIT:v1",
                            kind = TimelineKind.VISIT,
                            targetId = "v1",
                            patientId = "p1",
                            patientName = "Asha Rao",
                            title = "City Care Clinic",
                            subtitle = "Dr Mehta",
                            onEpochDay = today.minusDays(3).toEpochDay(),
                            recordedAtMillis = 0L,
                        ),
                        TimelineEntry(
                            id = "REPORT:r1",
                            kind = TimelineKind.REPORT,
                            targetId = "r1",
                            patientId = "p1",
                            patientName = "Asha Rao",
                            title = "blood-panel.pdf",
                            subtitle = "City Care Clinic · 726 KB",
                            onEpochDay = today.minusDays(3).toEpochDay(),
                            recordedAtMillis = 0L,
                        ),
                    ),
                ),
            ),
            onEvent = {},
        )
    }
}
