package com.ss.medrecord.ui.feature.visit.detail

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ss.medrecord.core.ui.components.FullScreenLoading
import com.ss.medrecord.domain.model.Facility
import com.ss.medrecord.domain.model.FacilityType
import com.ss.medrecord.domain.model.Visit
import com.ss.medrecord.domain.model.VisitWithFacility
import com.ss.medrecord.ui.theme.MedRecordTheme
import java.time.LocalDate
import java.time.format.DateTimeFormatter

@Composable
fun VisitDetailRoute(
    onNavigateBack: () -> Unit,
    onEdit: (String) -> Unit,
    viewModel: VisitDetailViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    LaunchedEffect(Unit) {
        viewModel.effect.collect { effect ->
            when (effect) {
                VisitDetailEffect.NavigateBack -> onNavigateBack()
                is VisitDetailEffect.NavigateToEdit -> onEdit(effect.visitId)
            }
        }
    }

    VisitDetailScreen(state = state, onEvent = viewModel::onEvent)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VisitDetailScreen(
    state: VisitDetailUiState,
    onEvent: (VisitDetailEvent) -> Unit,
    modifier: Modifier = Modifier,
) {
    Scaffold(
        modifier = modifier.fillMaxSize(),
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

                Text(
                    text = "Attached reports and prescribed medicines appear here from " +
                        "Phases 5 and 6.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 8.dp),
                )

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
