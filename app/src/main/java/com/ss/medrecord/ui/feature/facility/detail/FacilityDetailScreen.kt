package com.ss.medrecord.ui.feature.facility.detail

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ss.medrecord.core.location.Coordinates
import com.ss.medrecord.core.ui.components.FullScreenLoading
import com.ss.medrecord.domain.model.Facility
import com.ss.medrecord.domain.model.FacilityType
import com.ss.medrecord.domain.model.Visit
import com.ss.medrecord.ui.components.FacilityMap
import com.ss.medrecord.ui.components.MapPin
import com.ss.medrecord.ui.components.MedBarAction
import com.ss.medrecord.ui.components.MedScreen
import com.ss.medrecord.ui.components.MedTopBar
import com.ss.medrecord.ui.components.openDialer
import com.ss.medrecord.ui.components.openDirections
import com.ss.medrecord.ui.theme.MedIcons
import com.ss.medrecord.ui.theme.MedRecordTheme
import com.ss.medrecord.ui.theme.MedTheme
import java.time.LocalDate
import java.time.format.DateTimeFormatter

@Composable
fun FacilityDetailRoute(
    onNavigateBack: () -> Unit,
    onOpenVisit: (String) -> Unit,
    viewModel: FacilityDetailViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val context = LocalContext.current

    LaunchedEffect(Unit) {
        viewModel.effect.collect { effect ->
            when (effect) {
                FacilityDetailEffect.NavigateBack -> onNavigateBack()
                is FacilityDetailEffect.NavigateToVisit -> onOpenVisit(effect.visitId)
                is FacilityDetailEffect.ShowMessage ->
                    snackbarHostState.showSnackbar(effect.message)

                FacilityDetailEffect.OpenDirections -> {
                    val facility = viewModel.uiState.value.facility
                    val opened = facility?.latitude != null && facility.longitude != null &&
                        context.openDirections(
                            Coordinates(facility.latitude!!, facility.longitude!!),
                            facility.name,
                        )
                    if (!opened) {
                        snackbarHostState.showSnackbar("No app on this device can show maps.")
                    }
                }

                is FacilityDetailEffect.Dial -> {
                    if (!context.openDialer(effect.phone)) {
                        snackbarHostState.showSnackbar("This device cannot place calls.")
                    }
                }
            }
        }
    }

    FacilityDetailScreen(
        state = state,
        onEvent = viewModel::onEvent,
        snackbarHostState = snackbarHostState,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FacilityDetailScreen(
    state: FacilityDetailUiState,
    onEvent: (FacilityDetailEvent) -> Unit,
    modifier: Modifier = Modifier,
    snackbarHostState: SnackbarHostState = remember { SnackbarHostState() },
) {
    MedScreen(
        modifier = modifier,
        topBar = {
            MedTopBar(
                title = state.facility?.name ?: "Facility",
                onBack = { onEvent(FacilityDetailEvent.BackClicked) },
            )
        },
    ) { innerPadding ->
        val facility = state.facility

        when {
            state.isLoading -> FullScreenLoading(modifier = Modifier.padding(innerPadding))

            facility == null -> Text(
                text = "This facility is no longer available.",
                modifier = Modifier
                    .padding(innerPadding)
                    .padding(24.dp),
            )

            else -> LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                if (state.hasLocation) {
                    item {
                        FacilityMap(
                            pins = listOf(
                                MapPin(
                                    id = facility.facilityId,
                                    coordinates = Coordinates(
                                        facility.latitude!!,
                                        facility.longitude!!,
                                    ),
                                    title = facility.name,
                                    snippet = facility.address,
                                ),
                            ),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(200.dp),
                        )
                    }
                }

                item {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.secondaryContainer,
                        ),
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Text(
                                text = facility.type.label,
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            state.address?.let { address ->
                                Text(text = address, style = MaterialTheme.typography.bodyMedium)
                            }
                            state.phone?.let { phone ->
                                Text(text = phone, style = MaterialTheme.typography.bodyMedium)
                            }
                            facility.notes?.takeIf { it.isNotBlank() }?.let { notes ->
                                Text(
                                    text = notes,
                                    style = MaterialTheme.typography.bodySmall,
                                    modifier = Modifier.padding(top = 4.dp),
                                )
                            }
                        }
                    }
                }

                item {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        OutlinedButton(
                            onClick = { onEvent(FacilityDetailEvent.DirectionsClicked) },
                            modifier = Modifier.weight(1f),
                        ) {
                            Text(text = "Directions")
                        }
                        OutlinedButton(
                            onClick = { onEvent(FacilityDetailEvent.CallClicked) },
                            enabled = state.phone != null,
                            modifier = Modifier.weight(1f),
                        ) {
                            Text(text = "Call")
                        }
                    }
                }

                item {
                    Text(
                        text = if (state.visits.isEmpty()) {
                            "Visits here"
                        } else {
                            "Visits here (${state.visits.size})"
                        },
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.padding(top = 8.dp),
                    )
                }

                if (state.visits.isEmpty()) {
                    item {
                        Text(
                            text = "No visits logged at this place yet.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }

                items(state.visits, key = { it.visitId }) { visit ->
                    VisitRow(
                        visit = visit,
                        onClick = { onEvent(FacilityDetailEvent.VisitClicked(visit)) },
                    )
                }
            }
        }
    }
}

@Composable
private fun VisitRow(visit: Visit, onClick: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 8.dp),
    ) {
        Text(
            text = LocalDate.ofEpochDay(visit.visitDateEpochDay).format(DATE_FORMAT),
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = FontWeight.Medium,
        )
        visit.doctorName?.takeIf { it.isNotBlank() }?.let { doctor ->
            Text(
                text = doctor,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        HorizontalDivider(modifier = Modifier.padding(top = 8.dp))
    }
}

private val DATE_FORMAT: DateTimeFormatter = DateTimeFormatter.ofPattern("d MMM yyyy")

@Preview(showBackground = true)
@Composable
private fun FacilityDetailScreenPreview() {
    MedRecordTheme {
        FacilityDetailScreen(
            state = FacilityDetailUiState(
                isLoading = false,
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
                visits = listOf(
                    Visit(
                        visitId = "v1",
                        userId = "u1",
                        patientId = "p1",
                        facilityId = "f1",
                        doctorName = "Dr Mehta",
                        visitDateEpochDay = LocalDate.now().minusDays(20).toEpochDay(),
                        createdAt = 0L,
                        updatedAt = 0L,
                    ),
                ),
            ),
            onEvent = {},
        )
    }
}
