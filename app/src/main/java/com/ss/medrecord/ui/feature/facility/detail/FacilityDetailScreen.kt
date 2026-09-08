package com.ss.medrecord.ui.feature.facility.detail

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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ss.medrecord.core.location.Coordinates
import com.ss.medrecord.domain.model.Facility
import com.ss.medrecord.domain.model.FacilityType
import com.ss.medrecord.domain.model.Visit
import com.ss.medrecord.ui.components.FacilityMap
import com.ss.medrecord.ui.components.MapPin
import com.ss.medrecord.ui.components.MedCard
import com.ss.medrecord.ui.components.MedDetailRow
import com.ss.medrecord.ui.components.MedEmptyState
import com.ss.medrecord.ui.components.MedListRow
import com.ss.medrecord.ui.components.MedLoading
import com.ss.medrecord.ui.components.MedOutlineButton
import com.ss.medrecord.ui.components.MedScreen
import com.ss.medrecord.ui.components.MedTopBar
import com.ss.medrecord.ui.components.SectionHeader
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

/**
 * [showMap] exists for the screen tests, for the same reason as on the nearby
 * screen: the map is a real MapView and needs Play services and a GL surface.
 */
@Composable
fun FacilityDetailScreen(
    state: FacilityDetailUiState,
    onEvent: (FacilityDetailEvent) -> Unit,
    modifier: Modifier = Modifier,
    snackbarHostState: SnackbarHostState = remember { SnackbarHostState() },
    showMap: Boolean = true,
) {
    val colors = MedTheme.colors
    MedScreen(
        modifier = modifier,
        snackbarHostState = snackbarHostState,
        topBar = {
            MedTopBar(
                title = state.facility?.name ?: "Facility",
                subtitle = state.facility?.type?.label,
                onBack = { onEvent(FacilityDetailEvent.BackClicked) },
            )
        },
    ) { innerPadding ->
        val facility = state.facility

        when {
            state.isLoading -> MedLoading(modifier = Modifier.padding(innerPadding))

            facility == null -> MedEmptyState(
                title = "This facility is gone",
                message = "It was deleted, or it belongs to another account.",
                icon = MedIcons.LocalHospital,
                modifier = Modifier.padding(innerPadding),
            )

            else -> LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
                contentPadding = PaddingValues(start = 20.dp, end = 20.dp, bottom = 32.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                if (state.hasLocation && showMap) {
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
                                .height(200.dp)
                                .clip(RoundedCornerShape(20.dp)),
                        )
                    }
                }

                item {
                    MedCard(modifier = Modifier.fillMaxWidth()) {
                        Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                            state.address?.let { address ->
                                MedDetailRow(label = "Address", value = address)
                            }
                            state.phone?.let { phone ->
                                MedDetailRow(label = "Phone", value = phone)
                            }
                            facility.notes?.takeIf { it.isNotBlank() }?.let { notes ->
                                MedDetailRow(label = "Notes", value = notes)
                            }
                            if (state.address == null && state.phone == null) {
                                Text(
                                    text = "Only a name is on file. Saving this clinic " +
                                        "from a nearby search fills in the rest.",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = colors.textSecondary,
                                )
                            }
                        }
                    }
                }

                item {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        MedOutlineButton(
                            text = "Directions",
                            icon = MedIcons.Directions,
                            onClick = { onEvent(FacilityDetailEvent.DirectionsClicked) },
                            enabled = state.hasLocation,
                            modifier = Modifier.weight(1f),
                        )
                        MedOutlineButton(
                            text = "Call",
                            icon = MedIcons.Call,
                            onClick = { onEvent(FacilityDetailEvent.CallClicked) },
                            enabled = state.phone != null,
                            modifier = Modifier.weight(1f),
                        )
                    }
                }

                item {
                    SectionHeader(
                        title = "Visits here",
                        trailing = state.visits.size.toString().takeIf { state.visits.isNotEmpty() },
                        modifier = Modifier.padding(top = 8.dp),
                    )
                }

                if (state.visits.isEmpty()) {
                    item {
                        Text(
                            text = "No visits logged at this place yet.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = colors.textSecondary,
                        )
                    }
                }

                items(state.visits, key = { it.visitId }) { visit ->
                    MedCard(
                        onClick = { onEvent(FacilityDetailEvent.VisitClicked(visit)) },
                        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 2.dp),
                    ) {
                        MedListRow(
                            title = LocalDate.ofEpochDay(visit.visitDateEpochDay)
                                .format(DATE_FORMAT),
                            subtitle = visit.doctorName?.takeIf { it.isNotBlank() },
                            icon = MedIcons.Stethoscope,
                            accent = colors.jade,
                            showChevron = true,
                        )
                    }
                }
            }
        }
    }
}

private val DATE_FORMAT: DateTimeFormatter = DateTimeFormatter.ofPattern("d MMM yyyy")

@Preview(showBackground = true)
@Composable
private fun FacilityDetailScreenPreview() {
    MedRecordTheme {
        FacilityDetailScreen(
            showMap = false,
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
