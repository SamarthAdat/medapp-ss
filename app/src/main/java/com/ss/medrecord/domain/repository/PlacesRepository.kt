package com.ss.medrecord.domain.repository

import com.ss.medrecord.core.common.DataResult
import com.ss.medrecord.core.location.Coordinates
import com.ss.medrecord.domain.model.NearbyPlace
import com.ss.medrecord.domain.model.PlaceSearchType

/**
 * Medical facilities near a point.
 *
 * The only repository in this app with no offline story, and the only one that
 * returns [DataResult] from a read rather than a Flow from Room. That is
 * honest: these results are not the user's records, they are a live query
 * against someone else's index, and there is nothing to cache that would still
 * be true tomorrow. Everything else in the app works offline; this one screen
 * says so when it cannot.
 */
interface PlacesRepository {

    /** False when no Maps API key was configured at build time. */
    val isConfigured: Boolean

    suspend fun searchNearby(
        origin: Coordinates,
        type: PlaceSearchType,
        radiusMetres: Double,
    ): DataResult<List<NearbyPlace>>

    /**
     * Fills in the fields the nearby search does not return - currently the
     * phone number - for one place the user is about to save.
     *
     * Separate from the search because Places bills per field requested: asking
     * for a phone number for twenty results the user will scroll past costs
     * twenty times what asking for the one they picked does.
     */
    suspend fun details(place: NearbyPlace): DataResult<NearbyPlace>
}
