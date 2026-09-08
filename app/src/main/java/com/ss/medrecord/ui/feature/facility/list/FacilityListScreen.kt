package com.ss.medrecord.ui.feature.facility.list

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ss.medrecord.core.location.Coordinates
import com.ss.medrecord.domain.model.Facility
import com.ss.medrecord.domain.model.FacilityType
import com.ss.medrecord.ui.components.FacilityMap
import com.ss.medrecord.ui.components.MapPin
import com.ss.medrecord.ui.components.MedBarAction
import com.ss.medrecord.ui.components.MedCard
import com.ss.medrecord.ui.components.MedEmptyState
import com.ss.medrecord.ui.components.MedFab
import com.ss.medrecord.ui.components.MedFilterChip
import com.ss.medrecord.ui.components.MedListRow
import com.ss.medrecord.ui.components.MedLoading
import com.ss.medrecord.ui.components.MedScreen
import com.ss.medrecord.ui.components.MedSearchField
import com.ss.medrecord.ui.components.MedTopBar
import com.ss.medrecord.ui.components.StatusPill
import com.ss.medrecord.ui.theme.MedIcons
import com.ss.medrecord.ui.theme.MedRecordTheme
import com.ss.medrecord.ui.theme.MedTheme

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

/**
 * [showMap] exists for the screen tests, for the same reason as on the nearby
 * screen: the map is a real MapView and needs Play services and a GL surface.
 */
@Composable
fun FacilityListScreen(
    state: FacilityListUiState,
    onEvent: (FacilityListEvent) -> Unit,
    modifier: Modifier = Modifier,
    showMap: Boolean = true,
) {
    val colors = MedTheme.colors
    MedScreen(
        modifier = modifier,
        topBar = {
            MedTopBar(
                title = "Clinics and hospitals",
                subtitle = "Saved from visits and from searching nearby",
                onBack = { onEvent(FacilityListEvent.BackClicked) },
                actions = {
                    if (state.canShowMap) {
                        MedBarAction(
                            text = if (state.isMapView) "List" else "Map",
                            onClick = { onEvent(FacilityListEvent.ToggleMapView) },
                        )
                    }
                },
            )
        },
        floatingActionButton = {
            MedFab(
                text = "Find nearby",
                icon = MedIcons.MyLocation,
                onClick = { onEvent(FacilityListEvent.FindNearbyClicked) },
            )
        },
    ) { innerPadding ->
        if (state.isLoading) {
            MedLoading(modifier = Modifier.padding(innerPadding))
            return@MedScreen
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            MedSearchField(
                value = state.query,
                onValueChange = { onEvent(FacilityListEvent.QueryChanged(it)) },
                placeholder = "Search clinics and hospitals",
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp),
            )

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 20.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                MedFilterChip(
                    text = "All",
                    selected = state.typeFilter == null,
                    onClick = { onEvent(FacilityListEvent.TypeFilterChanged(null)) },
                )
                FacilityType.entries.forEach { type ->
                    MedFilterChip(
                        text = type.label,
                        selected = state.typeFilter == type,
                        onClick = { onEvent(FacilityListEvent.TypeFilterChanged(type)) },
                        icon = type.icon,
                    )
                }
            }

            if (state.isMapView) {
                if (showMap) {
                    FacilityMap(
                        pins = state.mappable.map { it.toPin() },
                        onPinClick = { facilityId ->
                            state.facilities.firstOrNull { it.facilityId == facilityId }
                                ?.let { onEvent(FacilityListEvent.FacilityClicked(it)) }
                        },
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(top = 4.dp)
                            .clip(RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)),
                    )
                }
                return@Column
            }

            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(
                    start = 20.dp,
                    end = 20.dp,
                    top = 4.dp,
                    // Clears the FAB so the last row is never trapped under it.
                    bottom = 88.dp,
                ),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                if (state.isEmpty) {
                    item {
                        MedEmptyState(
                            title = if (state.hasFiltersApplied) {
                                "Nothing matches that"
                            } else {
                                "No clinics saved yet"
                            },
                            message = "Clinics are added automatically when you log a " +
                                "visit, or you can search for one nearby.",
                            icon = MedIcons.LocalHospital,
                            actionText = "Find nearby".takeUnless { state.hasFiltersApplied },
                            onAction = { onEvent(FacilityListEvent.FindNearbyClicked) },
                        )
                    }
                }

                items(state.visible, key = { it.facilityId }) { facility ->
                    MedCard(
                        onClick = { onEvent(FacilityListEvent.FacilityClicked(facility)) },
                        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 2.dp),
                    ) {
                        MedListRow(
                            title = facility.name,
                            subtitle = listOfNotNull(
                                facility.type.label,
                                facility.address?.takeIf { it.isNotBlank() },
                            ).joinToString(" · "),
                            icon = facility.type.icon,
                            accent = colors.jade,
                            trailing = {
                                // Says which rows the Map button can actually
                                // show: a clinic typed into a visit form has a
                                // name and nothing else.
                                if (facility.hasLocation) {
                                    StatusPill(text = "Mapped", accent = colors.azure)
                                }
                            },
                            showChevron = true,
                        )
                    }
                }
            }
        }
    }
}

private val FacilityType.icon
    get() = when (this) {
        FacilityType.HOSPITAL -> MedIcons.LocalHospital
        FacilityType.CLINIC -> MedIcons.Stethoscope
        FacilityType.DIAGNOSTIC_CENTER -> MedIcons.Science
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
            showMap = false,
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
                        latitude = 12.97,
                        longitude = 77.60,
                        createdAt = 0L,
                        updatedAt = 0L,
                    ),
                ),
            ),
            onEvent = {},
        )
    }
}
