package com.ss.medrecord.ui.feature.facility.list

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
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ss.medrecord.core.location.Coordinates
import com.ss.medrecord.core.ui.components.FullScreenLoading
import com.ss.medrecord.domain.model.Facility
import com.ss.medrecord.domain.model.FacilityType
import com.ss.medrecord.ui.components.FacilityMap
import com.ss.medrecord.ui.components.MapPin
import com.ss.medrecord.ui.theme.MedRecordTheme

@Composable
fun FacilityListRoute(
    onNavigateBack: () -> Unit,
    onNavigateToDetail: (String) -> Unit,
    onNavigateToNearby: () -> Unit,
    viewModel: FacilityListViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    LaunchedEffect(Unit) {
        viewModel.effect.collect { effect ->
            when (effect) {
                FacilityListEffect.NavigateBack -> onNavigateBack()
                is FacilityListEffect.NavigateToDetail -> onNavigateToDetail(effect.facilityId)
                FacilityListEffect.NavigateToNearby -> onNavigateToNearby()
            }
        }
    }

    FacilityListScreen(state = state, onEvent = viewModel::onEvent)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FacilityListScreen(
    state: FacilityListUiState,
    onEvent: (FacilityListEvent) -> Unit,
    modifier: Modifier = Modifier,
) {
    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = { Text(text = "Clinics and hospitals") },
                navigationIcon = {
                    TextButton(onClick = { onEvent(FacilityListEvent.BackClicked) }) {
                        Text(text = "Back")
                    }
                },
                actions = {
                    if (state.canShowMap) {
                        TextButton(onClick = { onEvent(FacilityListEvent.ToggleMapView) }) {
                            Text(text = if (state.isMapView) "List" else "Map")
                        }
                    }
                },
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { onEvent(FacilityListEvent.FindNearbyClicked) },
                text = { Text(text = "Find nearby") },
                icon = {},
            )
        },
    ) { innerPadding ->
        if (state.isLoading) {
            FullScreenLoading(modifier = Modifier.padding(innerPadding))
            return@Scaffold
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
        ) {
            OutlinedTextField(
                value = state.query,
                onValueChange = { onEvent(FacilityListEvent.QueryChanged(it)) },
                label = { Text(text = "Search") },
                singleLine = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
            )

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                FilterChip(
                    selected = state.typeFilter == null,
                    onClick = { onEvent(FacilityListEvent.TypeFilterChanged(null)) },
                    label = { Text(text = "All") },
                )
                FacilityType.entries.forEach { type ->
                    FilterChip(
                        selected = state.typeFilter == type,
                        onClick = { onEvent(FacilityListEvent.TypeFilterChanged(type)) },
                        label = { Text(text = type.label) },
                    )
                }
            }

            if (state.isMapView) {
                FacilityMap(
                    pins = state.mappable.map { it.toPin() },
                    onPinClick = { facilityId ->
                        state.facilities.firstOrNull { it.facilityId == facilityId }
                            ?.let { onEvent(FacilityListEvent.FacilityClicked(it)) }
                    },
                    modifier = Modifier.fillMaxSize(),
                )
                return@Column
            }

            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(
                    start = 16.dp,
                    end = 16.dp,
                    top = 8.dp,
                    // Clears the FAB so the last row is never trapped under it.
                    bottom = 88.dp,
                ),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                if (state.isEmpty) {
                    item {
                        Column(modifier = Modifier.padding(vertical = 40.dp)) {
                            Text(
                                text = if (state.hasFiltersApplied) {
                                    "Nothing matches that"
                                } else {
                                    "No clinics saved yet"
                                },
                                style = MaterialTheme.typography.titleMedium,
                            )
                            Text(
                                text = "Clinics are added automatically when you log a " +
                                    "visit, or you can search for one nearby.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(top = 8.dp),
                            )
                        }
                    }
                }

                items(state.visible, key = { it.facilityId }) { facility ->
                    FacilityRow(
                        facility = facility,
                        onClick = { onEvent(FacilityListEvent.FacilityClicked(facility)) },
                    )
                }
            }
        }
    }
}

@Composable
private fun FacilityRow(facility: Facility, onClick: () -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant,
        ),
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Text(
                text = facility.name,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Medium,
            )
            Text(
                text = facility.type.label,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            facility.address?.takeIf { it.isNotBlank() }?.let { address ->
                Text(
                    text = address,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                )
            }
        }
    }
}

private fun Facility.toPin() = MapPin(
    id = facilityId,
    // Only called for facilities where hasLocation is already true.
    coordinates = Coordinates(latitude!!, longitude!!),
    title = name,
    snippet = address,
)

@Preview(showBackground = true)
@Composable
private fun FacilityListScreenPreview() {
    MedRecordTheme {
        FacilityListScreen(
            state = FacilityListUiState(
                isLoading = false,
                facilities = listOf(
                    Facility(
                        facilityId = "f1",
                        userId = "u1",
                        name = "City Care Clinic",
                        type = FacilityType.CLINIC,
                        address = "12 MG Road, Bengaluru",
                        createdAt = 0L,
                        updatedAt = 0L,
                    ),
                    Facility(
                        facilityId = "f2",
                        userId = "u1",
                        name = "Sunrise Hospital",
                        type = FacilityType.HOSPITAL,
                        address = "44 Residency Road, Bengaluru",
                        createdAt = 0L,
                        updatedAt = 0L,
                    ),
                ),
            ),
            onEvent = {},
        )
    }
}
