package com.ss.medrecord.ui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.google.android.gms.maps.model.CameraPosition
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.MapStyleOptions
import com.google.maps.android.compose.GoogleMap
import com.google.maps.android.compose.MapProperties
import com.google.maps.android.compose.MapUiSettings
import com.google.maps.android.compose.Marker
import com.google.maps.android.compose.rememberMarkerState
import com.google.maps.android.compose.rememberCameraPositionState
import com.ss.medrecord.BuildConfig
import com.ss.medrecord.R
import com.ss.medrecord.core.location.Coordinates
import com.ss.medrecord.ui.theme.MedTheme

/** One thing to drop a pin on. */
data class MapPin(
    val id: String,
    val coordinates: Coordinates,
    val title: String,
    val snippet: String? = null,
)

/**
 * A map with pins on it, or an honest explanation of why there isn't one.
 *
 * The no-key branch matters more than it looks. Without a valid API key the
 * Maps SDK renders a blank grey rectangle and logs the reason where only a
 * developer will see it, which reads to a user as a broken app. Checking the
 * build flag first turns that into a sentence.
 *
 * [showMyLocation] is only ever passed true once the location permission has
 * actually been granted: the Maps SDK throws a SecurityException rather than
 * degrading if the layer is enabled without it.
 */
@Composable
fun FacilityMap(
    pins: List<MapPin>,
    modifier: Modifier = Modifier,
    focus: Coordinates? = null,
    showMyLocation: Boolean = false,
    onPinClick: (String) -> Unit = {},
) {
    if (!BuildConfig.HAS_MAPS_KEY) {
        MapUnavailable(
            message = "Maps are not configured in this build.",
            modifier = modifier,
        )
        return
    }

    val target = focus ?: pins.firstOrNull()?.coordinates
    if (target == null) {
        MapUnavailable(
            message = "No saved location to show yet.",
            modifier = modifier,
        )
        return
    }

    val cameraPositionState = rememberCameraPositionState {
        position = CameraPosition.fromLatLngZoom(
            LatLng(target.latitude, target.longitude),
            DEFAULT_ZOOM,
        )
    }

    // Recentres when the subject changes - picking a different result should
    // move the map, not leave the user to find the new pin themselves.
    LaunchedEffect(target.latitude, target.longitude) {
        cameraPositionState.position = CameraPosition.fromLatLngZoom(
            LatLng(target.latitude, target.longitude),
            cameraPositionState.position.zoom.takeIf { it > 1f } ?: DEFAULT_ZOOM,
        )
    }

    // Google's default map is a bright one. On the app's near-black canvas that
    // is a white rectangle with the screen built around it; the style file is
    // the same palette the rest of the design uses.
    val context = LocalContext.current
    val darkStyle = MedTheme.colors.isDark
    val mapStyle = remember(darkStyle) {
        if (darkStyle) {
            MapStyleOptions.loadRawResourceStyle(context, R.raw.map_style_dark)
        } else {
            null
        }
    }

    GoogleMap(
        modifier = modifier,
        cameraPositionState = cameraPositionState,
        properties = MapProperties(
            isMyLocationEnabled = showMyLocation,
            mapStyleOptions = mapStyle,
        ),
        uiSettings = MapUiSettings(
            // The dedicated button is redundant next to the app's own controls
            // and sits on top of content on a short map.
            myLocationButtonEnabled = false,
            zoomControlsEnabled = false,
            mapToolbarEnabled = false,
        ),
    ) {
        pins.forEach { pin ->
            // Remembered, not constructed inline. A MarkerState built during
            // composition is thrown away and rebuilt on every recomposition,
            // which drops whatever the marker was doing - an open info window
            // closes itself the moment anything else on the screen changes.
            val markerState = rememberMarkerState(
                key = pin.id,
                position = LatLng(pin.coordinates.latitude, pin.coordinates.longitude),
            )
            Marker(
                state = markerState,
                title = pin.title,
                snippet = pin.snippet,
                onClick = {
                    onPinClick(pin.id)
                    // False so the marker still shows its info window; returning
                    // true swallows the default behaviour entirely.
                    false
                },
            )
        }
    }
}

@Composable
private fun MapUnavailable(message: String, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier.padding(16.dp),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = message,
                style = MaterialTheme.typography.bodyMedium,
                color = MedTheme.colors.textSecondary,
                textAlign = TextAlign.Center,
            )
            Text(
                text = "Everything else on this screen still works.",
                style = MaterialTheme.typography.bodySmall,
                color = MedTheme.colors.textTertiary,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = 4.dp),
            )
        }
    }
}

/** Close enough to see a street, far enough to see a few blocks. */
private const val DEFAULT_ZOOM = 14f
