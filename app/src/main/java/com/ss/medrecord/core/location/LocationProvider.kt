package com.ss.medrecord.core.location

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.LocationManager
import android.util.Log
import androidx.core.content.ContextCompat
import com.google.android.gms.location.CurrentLocationRequest
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.tasks.await
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

private const val TAG = "LocationProvider"

/**
 * The device's current position, used for one thing only: ordering nearby
 * clinics by distance (spec section 5.10).
 *
 * The privacy rule this class exists to enforce is that a location is **never
 * stored and never leaves the device except as a search radius**. It is not
 * written to Room, not synced to Firestore, not put in the audit trail and not
 * held in a field here. Where someone is at a given moment is inferable health
 * data in an app like this - being near an oncology centre says something -
 * and the only defensible way to hold it is not to.
 *
 * Coarse accuracy is requested rather than fine. Ranking clinics within a few
 * kilometres does not need metre-level precision, and asking for less is the
 * difference between a permission prompt the user can reasonably accept and one
 * they should refuse.
 */
@Singleton
class LocationProvider @Inject constructor(
    @param:ApplicationContext private val context: Context,
) {

    private val client by lazy { LocationServices.getFusedLocationProviderClient(context) }

    /** True once either location permission has been granted. */
    fun hasPermission(): Boolean =
        isGranted(Manifest.permission.ACCESS_COARSE_LOCATION) ||
            isGranted(Manifest.permission.ACCESS_FINE_LOCATION)

    /**
     * Whether the device's location services are switched on at all. Distinct
     * from the app's permission: both can block a fix, and telling the user the
     * wrong one sends them to the wrong settings screen.
     */
    fun isLocationEnabled(): Boolean {
        val manager = ContextCompat.getSystemService(context, LocationManager::class.java)
        return manager?.isProviderEnabled(LocationManager.GPS_PROVIDER) == true ||
            manager?.isProviderEnabled(LocationManager.NETWORK_PROVIDER) == true
    }

    /**
     * A single coarse fix, or null if one cannot be obtained.
     *
     * Null rather than an exception for every failure mode - no permission,
     * location off, no fix in time - because the caller's response is the same
     * in all three: fall back to searching without a location. The distinction
     * that matters to the user is surfaced by [hasPermission] and
     * [isLocationEnabled], which the UI checks before it gets here.
     */
    suspend fun currentLocation(): Coordinates? {
        if (!hasPermission()) return null

        return try {
            val request = CurrentLocationRequest.Builder()
                .setPriority(Priority.PRIORITY_BALANCED_POWER_ACCURACY)
                // A stale fix from the last few minutes is fine for ranking
                // clinics and avoids waking the GPS at all.
                .setMaxUpdateAgeMillis(MAX_FIX_AGE_MILLIS)
                .setDurationMillis(FIX_TIMEOUT_MILLIS)
                .build()

            client.getCurrentLocation(request, null).await()
                ?.let { Coordinates(it.latitude, it.longitude) }
        } catch (e: SecurityException) {
            // Permission revoked between the check and the call.
            Log.d(TAG, "Location permission withdrawn", e)
            null
        } catch (e: Exception) {
            Log.d(TAG, "No location fix available", e)
            null
        }
    }

    private fun isGranted(permission: String): Boolean =
        ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED

    private companion object {
        const val MAX_FIX_AGE_MILLIS = 5 * 60 * 1000L
        const val FIX_TIMEOUT_MILLIS = 10_000L
    }
}

/**
 * A point on the map.
 *
 * Deliberately its own type rather than the Maps SDK's LatLng: this travels
 * through the domain layer, which should not have to depend on Google Play
 * Services to describe where a clinic is.
 */
data class Coordinates(
    val latitude: Double,
    val longitude: Double,
) {
    /**
     * Great-circle distance in metres.
     *
     * Computed here rather than taken from the Places response: the API returns
     * results ranked by its own notion of relevance, and "which of these is
     * actually closest to me" is a different question that the user is entitled
     * to see answered honestly.
     */
    fun distanceMetresTo(other: Coordinates): Double {
        val earthRadiusMetres = 6_371_000.0
        val dLat = Math.toRadians(other.latitude - latitude)
        val dLon = Math.toRadians(other.longitude - longitude)
        val a = Math.sin(dLat / 2) * Math.sin(dLat / 2) +
            Math.cos(Math.toRadians(latitude)) * Math.cos(Math.toRadians(other.latitude)) *
            Math.sin(dLon / 2) * Math.sin(dLon / 2)
        return 2 * earthRadiusMetres * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a))
    }
}

/**
 * "450 m" or "2.3 km" - a distance a person can act on, not six decimal places.
 *
 * The locale is passed explicitly because it is being read by a person: a
 * device set to a locale that writes decimals with a comma should see "2,3 km".
 * Leaving it implicit is the same call made by accident.
 */
fun formatDistance(metres: Double): String = when {
    metres < 1000 -> "${metres.toInt()} m"
    metres < 10_000 -> String.format(Locale.getDefault(), "%.1f km", metres / 1000)
    else -> "${(metres / 1000).toInt()} km"
}
