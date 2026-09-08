package com.ss.medrecord.ui.feature.facility.nearby

import android.Manifest
import android.content.Context
import android.content.Intent
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.LinearProgressIndicator
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ss.medrecord.core.location.Coordinates
import com.ss.medrecord.core.location.formatDistance
import com.ss.medrecord.domain.model.NearbyPlace
import com.ss.medrecord.domain.model.PlaceSearchType
import com.ss.medrecord.ui.components.FacilityMap
import com.ss.medrecord.ui.components.MapPin
import com.ss.medrecord.ui.components.MedBarAction
import com.ss.medrecord.ui.components.MedScreen
import com.ss.medrecord.ui.components.MedTopBar
import com.ss.medrecord.ui.components.openDirections
import com.ss.medrecord.ui.theme.MedIcons
import com.ss.medrecord.ui.theme.MedRecordTheme
import com.ss.medrecord.ui.theme.MedTheme

@Composable
fun NearbyRoute(
    onNavigateBack: () -> Unit,
    onNavigateToFacility: (String) -> Unit,
    viewModel: NearbyViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val context = LocalContext.current

    val locationPermission = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { viewModel.onEvent(NearbyEvent.PermissionsRechecked) }

    // Both the permission and the system location toggle are changed outside
    // the app and neither reports back, so they are re-read on resume.
    LifecycleResumeEffect(Unit) {
        viewModel.onEvent(NearbyEvent.PermissionsRechecked)
        onPauseOrDispose {}
    }

    LaunchedEffect(Unit) {
        viewModel.effect.collect { effect ->
            when (effect) {
                NearbyEffect.NavigateBack -> onNavigateBack()
                is NearbyEffect.NavigateToFacility -> onNavigateToFacility(effect.facilityId)
                is NearbyEffect.ShowMessage -> snackbarHostState.showSnackbar(effect.message)

                NearbyEffect.RequestLocationPermission ->
                    locationPermission.launch(Manifest.permission.ACCESS_COARSE_LOCATION)

                NearbyEffect.OpenLocationSettings -> context.openLocationSettings()

                is NearbyEffect.OpenDirections -> {
                    val coordinates = effect.place.coordinates
                    val opened = coordinates != null &&
                        context.openDirections(coordinates, effect.place.name)
                    if (!opened) {
                        snackbarHostState.showSnackbar("No app on this device can show maps.")
                    }
                }
            }
        }
    }

    NearbyScreen(
        state = state,
        onEvent = viewModel::onEvent,
        snackbarHostState = snackbarHostState,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NearbyScreen(
    state: NearbyUiState,
    onEvent: (NearbyEvent) -> Unit,
    modifier: Modifier = Modifier,
    snackbarHostState: SnackbarHostState = remember { SnackbarHostState() },
) {
    MedScreen(
        modifier = modifier,
        topBar = {
            MedTopBar(
                title = "Find nearby",
                subtitle = "Your location is used to search and never saved",
                onBack = { onEvent(NearbyEvent.BackClicked) },
                actions = {
                    if (state.mappable.isNotEmpty()) {
                        MedBarAction(
                            text = if (state.isMapView) "List" else "Map",
                            onClick = { onEvent(NearbyEvent.ToggleMapView) },
                        )
                    }
                },
            )
        },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                PlaceSearchType.entries.forEach { type ->
                    FilterChip(
                        selected = state.searchType == type,
                        onClick = { onEvent(NearbyEvent.SearchTypeChanged(type)) },
                        enabled = state.canSearch || state.searchType == type,
                        label = { Text(text = type.label) },
                    )
                }
            }

            state.blocker?.let { blocker ->
                BlockerCard(blocker = blocker, onEvent = onEvent)
                return@Column
            }

            if (state.isSearching) {
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            }

            state.errorMessage?.let { message ->
                Text(
                    text = message,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                )
            }

            if (!state.hasSearched && !state.isSearching) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "Search around you",
                        style = MaterialTheme.typography.titleMedium,
                    )
                    Text(
                        text = "Your location is used to rank results and is never " +
                            "saved or synced.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 4.dp, bottom = 12.dp),
                    )
                    Button(
                        onClick = { onEvent(NearbyEvent.SearchClicked) },
                        enabled = state.canSearch,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(text = "Search")
                    }
                }
                return@Column
            }

            if (state.isMapView && state.mappable.isNotEmpty()) {
                FacilityMap(
                    pins = state.mappable.map { place ->
                        MapPin(
                            id = place.placeId,
                            coordinates = place.coordinates!!,
                            title = place.name,
                            snippet = place.address,
                        )
                    },
                    focus = state.origin,
                    showMyLocation = state.hasLocationPermission,
                    onPinClick = { placeId ->
                        state.results.firstOrNull { it.placeId == placeId }
                            ?.let { onEvent(NearbyEvent.PlaceClicked(it)) }
                    },
                    modifier = Modifier.fillMaxSize(),
                )
                return@Column
            }

            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                if (state.isEmpty) {
                    item {
                        Column(modifier = Modifier.padding(vertical = 32.dp)) {
                            Text(
                                text = "Nothing found nearby",
                                style = MaterialTheme.typography.titleMedium,
                            )
                            Text(
                                text = "Try a different category, or check your " +
                                    "connection - this is the one screen that needs one.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(top = 8.dp),
                            )
                        }
                    }
                }

                items(state.results, key = { it.placeId }) { place ->
                    PlaceRow(
                        place = place,
                        isSaving = state.savingPlaceId == place.placeId,
                        onSave = { onEvent(NearbyEvent.SaveClicked(place)) },
                        onClick = { onEvent(NearbyEvent.PlaceClicked(place)) },
                    )
                }

                if (state.results.isNotEmpty()) {
                    item {
                        TextButton(
                            onClick = { onEvent(NearbyEvent.SearchClicked) },
                            enabled = state.canSearch,
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Text(text = "Search again from here")
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun PlaceRow(
    place: NearbyPlace,
    isSaving: Boolean,
    onSave: () -> Unit,
    onClick: () -> Unit,
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant,
        ),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 14.dp, top = 12.dp, end = 6.dp, bottom = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = place.name,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                )
                place.address?.let { address ->
                    Text(
                        text = address,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
                    )
                }
                place.distanceMetres?.let { metres ->
                    Text(
                        text = formatDistance(metres),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
            }

            if (isSaving) {
                CircularProgressIndicator(modifier = Modifier.padding(12.dp))
            } else {
                TextButton(onClick = onSave) { Text(text = "Save") }
            }
        }
    }
}

/**
 * Why the search cannot run, and the one action that fixes it. Each blocker
 * needs its own words: sending someone to app settings when the problem is the
 * device's location toggle wastes their time and teaches them the prompt is
 * wrong.
 */
@Composable
private fun BlockerCard(blocker: NearbyBlocker, onEvent: (NearbyEvent) -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.secondaryContainer,
        ),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            when (blocker) {
                NearbyBlocker.NOT_CONFIGURED -> {
                    Text(
                        text = "Search is unavailable",
                        style = MaterialTheme.typography.titleMedium,
                    )
                    Text(
                        text = "This build has no Maps API key. Saved clinics and " +
                            "everything else still work.",
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(top = 4.dp),
                    )
                }

                NearbyBlocker.NO_PERMISSION -> {
                    Text(
                        text = "Location needed to search",
                        style = MaterialTheme.typography.titleMedium,
                    )
                    Text(
                        text = "Approximate location only, used to rank clinics by " +
                            "distance. It is never saved, never synced and never leaves " +
                            "this device except as a search radius.",
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(top = 4.dp),
                    )
                    Button(
                        onClick = { onEvent(NearbyEvent.GrantLocationClicked) },
                        modifier = Modifier.padding(top = 12.dp),
                    ) {
                        Text(text = "Allow location")
                    }
                }

                NearbyBlocker.LOCATION_OFF -> {
                    Text(
                        text = "Location is switched off",
                        style = MaterialTheme.typography.titleMedium,
                    )
                    Text(
                        text = "Turn on location for this device to search nearby.",
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(top = 4.dp),
                    )
                    Button(
                        onClick = { onEvent(NearbyEvent.EnableLocationClicked) },
                        modifier = Modifier.padding(top = 12.dp),
                    ) {
                        Text(text = "Open settings")
                    }
                }
            }
        }
    }
}

private fun Context.openLocationSettings() {
    runCatching {
        startActivity(
            Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun NearbyScreenPreview() {
    MedRecordTheme {
        NearbyScreen(
            state = NearbyUiState(
                hasSearched = true,
                hasLocationPermission = true,
                origin = Coordinates(12.97, 77.59),
                results = listOf(
                    NearbyPlace(
                        placeId = "p1",
                        name = "Sunrise Hospital",
                        address = "44 Residency Road, Bengaluru",
                        coordinates = Coordinates(12.97, 77.60),
                        distanceMetres = 420.0,
                    ),
                    NearbyPlace(
                        placeId = "p2",
                        name = "City Care Clinic",
                        address = "12 MG Road, Bengaluru",
                        coordinates = Coordinates(12.98, 77.61),
                        distanceMetres = 2300.0,
                    ),
                ),
            ),
            onEvent = {},
        )
    }
}
