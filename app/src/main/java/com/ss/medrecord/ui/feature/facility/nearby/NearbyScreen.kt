package com.ss.medrecord.ui.feature.facility.nearby

import android.Manifest
import android.content.Context
import android.content.Intent
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
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
import com.ss.medrecord.ui.components.IconTile
import com.ss.medrecord.ui.components.MapPin
import com.ss.medrecord.ui.components.MedFilterChip
import com.ss.medrecord.ui.components.MedIconGlyph
import com.ss.medrecord.ui.components.MedPrimaryButton
import com.ss.medrecord.ui.components.StatusPill
import com.ss.medrecord.ui.components.openDirections
import com.ss.medrecord.ui.theme.MedIcon
import com.ss.medrecord.ui.theme.MedIcons
import com.ss.medrecord.ui.theme.MedRecordTheme
import com.ss.medrecord.ui.theme.MedTheme
import com.ss.medrecord.ui.theme.MedTypography

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

/**
 * The map and the results, at the same time.
 *
 * The screen is a map with a sheet pulled up over the bottom of it, because the
 * two halves answer different halves of the same question: the list says what
 * is near and how far, the map says which direction. An earlier version put
 * them behind a Map/List switch in the app bar, and the switch only appeared
 * once a search had returned - which meant that on the two occasions a user
 * most wants a map, before searching and after an empty search, there was no
 * sign the app had one. The sheet's handle is the only control now, and it
 * moves between showing more list and showing more map.
 *
 * [showMap] exists for the screen tests. The map is a real MapView needing
 * Play services and a GL surface, and the tests here are about which words
 * appear when a search is blocked - not about Google's renderer.
 */
@Composable
fun NearbyScreen(
    state: NearbyUiState,
    onEvent: (NearbyEvent) -> Unit,
    modifier: Modifier = Modifier,
    snackbarHostState: SnackbarHostState = remember { SnackbarHostState() },
    showMap: Boolean = true,
) {
    val colors = MedTheme.colors
    var sheetExpanded by remember { mutableStateOf(false) }

    BoxWithConstraints(
        modifier = modifier
            .fillMaxSize()
            .background(colors.canvas),
    ) {
        val screenHeight = maxHeight

        if (showMap && state.origin != null) {
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
        } else {
            // Rather than a decorative fake map. A stylised street grid in the
            // space where the real one goes is indistinguishable from a map
            // that failed to load.
            Column(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    // Centred in the band the map would fill, not on the screen:
                    // the sheet covers the screen's middle.
                    .padding(top = screenHeight * 0.28f)
                    .padding(horizontal = 32.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                IconTile(icon = MedIcons.Map, accent = colors.textTertiary, size = 52.dp)
                Text(
                    text = "The map fills this screen once a search has somewhere to " +
                        "centre on.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = colors.textTertiary,
                    textAlign = TextAlign.Center,
                )
            }
        }

        FloatingControls(
            state = state,
            onEvent = onEvent,
            modifier = Modifier
                .align(Alignment.TopCenter)
                .statusBarsPadding(),
        )

        // The sheet only takes a fixed share of the screen when there is a list
        // long enough to scroll. A blocker or the pre-search prompt is three
        // lines, and stretching either to half the screen would read as an
        // empty list rather than as a short message.
        val expandedFraction by animateFloatAsState(
            targetValue = if (sheetExpanded) 0.86f else 0.52f,
            label = "sheetHeight",
        )
        val sheetHeight = if (state.results.isNotEmpty()) {
            Modifier.heightIn(max = screenHeight * expandedFraction)
        } else {
            Modifier
        }

        ResultSheet(
            state = state,
            onEvent = onEvent,
            expanded = sheetExpanded,
            onToggleExpanded = { sheetExpanded = !sheetExpanded },
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .then(sheetHeight),
        )

        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .navigationBarsPadding(),
        )
    }
}

/**
 * Back, the search, and the category chips, floating over the map.
 *
 * They sit on a translucent slab rather than in a bar: a solid app bar over a
 * map costs a strip of the thing the user came here to look at.
 */
@Composable
private fun FloatingControls(
    state: NearbyUiState,
    onEvent: (NearbyEvent) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = MedTheme.colors
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .glass()
                    .clickable { onEvent(NearbyEvent.BackClicked) },
                contentAlignment = Alignment.Center,
            ) {
                MedIconGlyph(
                    icon = MedIcons.ArrowBack,
                    size = 21.dp,
                    tint = colors.textSecondary,
                    contentDescription = "Back",
                )
            }

            // Shaped like a search field because that is what it does: it runs
            // the search again, from wherever the device is now.
            Row(
                modifier = Modifier
                    .weight(1f)
                    .height(40.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .glass()
                    .clickable(enabled = state.canSearch) {
                        onEvent(NearbyEvent.SearchClicked)
                    }
                    .padding(horizontal = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                MedIconGlyph(
                    icon = MedIcons.Search,
                    size = 19.dp,
                    tint = colors.textTertiary,
                    contentDescription = null,
                )
                Text(
                    text = "${state.searchType.label} near me",
                    style = MaterialTheme.typography.bodyMedium,
                    color = colors.textSecondary,
                    modifier = Modifier.weight(1f),
                )
                if (state.isSearching) {
                    CircularProgressIndicator(
                        strokeWidth = 2.dp,
                        color = colors.jade,
                        modifier = Modifier.size(16.dp),
                    )
                }
            }
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 20.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            PlaceSearchType.entries.forEach { type ->
                MedFilterChip(
                    text = type.label,
                    selected = state.searchType == type,
                    onClick = { onEvent(NearbyEvent.SearchTypeChanged(type)) },
                    icon = type.icon,
                )
            }
        }
    }
}

/**
 * Everything the search has to say, on a sheet over the map.
 *
 * One container for four states - blocked, not yet searched, nothing found,
 * results - so that whichever one the user lands in, the words arrive in the
 * same place.
 */
@Composable
private fun ResultSheet(
    state: NearbyUiState,
    onEvent: (NearbyEvent) -> Unit,
    expanded: Boolean,
    onToggleExpanded: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = MedTheme.colors
    val shape = RoundedCornerShape(topStart = 26.dp, topEnd = 26.dp)
    val listState = rememberLazyListState()

    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(shape)
            .background(colors.card)
            .border(1.dp, colors.hairline, shape)
            .navigationBarsPadding()
            .padding(bottom = 12.dp),
    ) {
        // Offered only when there is something under the fold. A handle that
        // expands a sheet already showing everything is a control that teaches
        // the user it does nothing. The row's height is fixed so that showing
        // or hiding the chevron cannot itself change what fits.
        val canExpand = listState.canScrollForward || listState.canScrollBackward || expanded
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(28.dp)
                .then(
                    if (canExpand) Modifier.clickable(onClick = onToggleExpanded) else Modifier,
                ),
            contentAlignment = Alignment.Center,
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Box(
                    modifier = Modifier
                        .width(40.dp)
                        .height(4.dp)
                        .clip(RoundedCornerShape(2.dp))
                        .background(colors.hairlineStrong),
                )
                if (canExpand) {
                    MedIconGlyph(
                        icon = if (expanded) MedIcons.ExpandMore else MedIcons.ExpandLess,
                        size = 18.dp,
                        tint = colors.textTertiary,
                        contentDescription = if (expanded) {
                            "Show the map"
                        } else {
                            "Show the whole list"
                        },
                    )
                }
            }
        }

        val blocker = state.blocker
        when {
            blocker != null -> Blocker(
                blocker = blocker,
                onEvent = onEvent,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp),
            )

            !state.hasSearched -> BeforeSearching(
                state = state,
                onEvent = onEvent,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp),
            )

            else -> Results(state = state, onEvent = onEvent, listState = listState)
        }
    }
}

@Composable
private fun BeforeSearching(
    state: NearbyUiState,
    onEvent: (NearbyEvent) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = MedTheme.colors
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(
            text = "Search around you",
            style = MaterialTheme.typography.titleMedium,
            color = colors.textPrimary,
        )
        Text(
            text = "Your location is used to rank results and is never saved or synced.",
            style = MaterialTheme.typography.bodyMedium,
            color = colors.textSecondary,
        )
        MedPrimaryButton(
            text = "Search",
            onClick = { onEvent(NearbyEvent.SearchClicked) },
            enabled = state.canSearch,
            loading = state.isSearching,
            icon = MedIcons.Search,
            modifier = Modifier.padding(top = 12.dp),
        )
    }
}

@Composable
private fun Results(
    state: NearbyUiState,
    onEvent: (NearbyEvent) -> Unit,
    listState: LazyListState,
) {
    val colors = MedTheme.colors

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = if (state.results.isEmpty()) {
                "Nothing found nearby"
            } else {
                "${state.results.size} ${state.searchType.label.lowercase()} nearby"
            },
            style = MaterialTheme.typography.titleMedium,
            color = colors.textPrimary,
            modifier = Modifier.weight(1f),
        )
        // The promise from the permission prompt, repeated where the results of
        // acting on it are. A privacy claim is worth least on the screen the
        // user has already left.
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(5.dp),
        ) {
            MedIconGlyph(
                icon = MedIcons.Lock,
                size = 15.dp,
                tint = colors.textTertiary,
                contentDescription = null,
            )
            Text(
                text = "Location never saved",
                style = MaterialTheme.typography.labelSmall,
                color = colors.textTertiary,
            )
        }
    }

    state.errorMessage?.let { message ->
        Text(
            text = message,
            style = MaterialTheme.typography.bodyMedium,
            color = colors.coral,
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
        )
    }

    if (state.isEmpty) {
        Text(
            text = "Try a different category, or check your connection - this is the " +
                "one screen that needs one.",
            style = MaterialTheme.typography.bodyMedium,
            color = colors.textSecondary,
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
        )
        return
    }

    LazyColumn(
        state = listState,
        modifier = Modifier.fillMaxWidth(),
        contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 14.dp, bottom = 8.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        itemsIndexed(state.results) { index, place ->
            PlaceRow(
                place = place,
                searchType = state.searchType,
                // Results are ranked by distance, so the first one is the
                // nearest - the only ranking this screen is entitled to assert.
                nearest = index == 0,
                saved = state.isSaved(place),
                isSaving = state.savingPlaceId == place.placeId,
                onSave = { onEvent(NearbyEvent.SaveClicked(place)) },
                onClick = { onEvent(NearbyEvent.PlaceClicked(place)) },
            )
        }
    }
}

@Composable
private fun PlaceRow(
    place: NearbyPlace,
    searchType: PlaceSearchType,
    nearest: Boolean,
    saved: Boolean,
    isSaving: Boolean,
    onSave: () -> Unit,
    onClick: () -> Unit,
) {
    val colors = MedTheme.colors
    val shape = RoundedCornerShape(16.dp)
    val accent = if (nearest) colors.jade else colors.textSecondary

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(if (nearest) colors.jade.copy(alpha = 0.10f) else colors.cardRaised)
            .border(
                width = 1.dp,
                color = if (nearest) colors.jade.copy(alpha = 0.32f) else colors.hairline,
                shape = shape,
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 13.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        IconTile(icon = searchType.icon, accent = accent, size = 38.dp)

        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                text = place.name,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                color = colors.textPrimary,
                maxLines = 1,
            )
            val address = place.address
            if (address != null || saved) {
                Text(
                    text = listOfNotNull(address, "saved".takeIf { saved })
                        .joinToString(" · "),
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.textSecondary,
                    maxLines = 2,
                )
            }
        }

        Column(
            horizontalAlignment = Alignment.End,
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            place.distanceMetres?.let { metres ->
                Text(
                    text = formatDistance(metres),
                    style = MedTypography.monoNumber,
                    color = if (nearest) colors.jade else colors.textSecondary,
                )
            }
            when {
                isSaving -> CircularProgressIndicator(
                    strokeWidth = 2.dp,
                    color = colors.jade,
                    modifier = Modifier.size(18.dp),
                )

                saved -> StatusPill(text = "Saved", accent = colors.jade)

                else -> Text(
                    text = "Save",
                    style = MaterialTheme.typography.titleSmall,
                    color = colors.jade,
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .clickable(onClick = onSave)
                        .padding(horizontal = 8.dp, vertical = 4.dp),
                )
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
private fun Blocker(
    blocker: NearbyBlocker,
    onEvent: (NearbyEvent) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = MedTheme.colors
    val title: String
    val body: String
    val action: Pair<String, NearbyEvent>?

    when (blocker) {
        NearbyBlocker.NOT_CONFIGURED -> {
            title = "Search is unavailable"
            body = "This build has no Maps API key. Saved clinics and everything else " +
                "still work."
            // Nothing the user can do, so nothing is offered.
            action = null
        }

        NearbyBlocker.NO_PERMISSION -> {
            title = "Location needed to search"
            body = "Approximate location only, used to rank clinics by distance. It is " +
                "never saved, never synced and never leaves this device except as a " +
                "search radius."
            action = "Allow location" to NearbyEvent.GrantLocationClicked
        }

        NearbyBlocker.LOCATION_OFF -> {
            title = "Location is switched off"
            body = "Turn on location for this device to search nearby."
            action = "Open settings" to NearbyEvent.EnableLocationClicked
        }
    }

    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        IconTile(
            icon = if (blocker == NearbyBlocker.NOT_CONFIGURED) MedIcons.Map else MedIcons.MyLocation,
            accent = colors.amber,
        )
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                color = colors.textPrimary,
            )
            Text(
                text = body,
                style = MaterialTheme.typography.bodyMedium,
                color = colors.textSecondary,
            )
            action?.let { (label, event) ->
                MedPrimaryButton(
                    text = label,
                    onClick = { onEvent(event) },
                    modifier = Modifier.padding(top = 10.dp),
                )
            }
        }
    }
}

/** The translucent slab the floating controls sit on. */
@Composable
private fun Modifier.glass(): Modifier {
    val colors = MedTheme.colors
    return this
        .background(colors.canvas.copy(alpha = 0.82f))
        .border(1.dp, colors.hairlineStrong, RoundedCornerShape(12.dp))
}

/**
 * The glyph for what was searched for, rather than for what the place turned
 * out to be: [com.ss.medrecord.domain.model.FacilityType] has no pharmacy, so
 * a pharmacy result carries CLINIC and would otherwise draw a stethoscope.
 */
private val PlaceSearchType.icon: MedIcon
    get() = when (this) {
        PlaceSearchType.HOSPITAL -> MedIcons.LocalHospital
        PlaceSearchType.CLINIC -> MedIcons.Stethoscope
        PlaceSearchType.PHARMACY -> MedIcons.LocalPharmacy
        PlaceSearchType.LAB -> MedIcons.Science
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
            showMap = false,
            state = NearbyUiState(
                hasSearched = true,
                hasLocationPermission = true,
                origin = Coordinates(12.97, 77.59),
                savedNames = setOf("city care clinic"),
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
