package com.ss.medrecord.data.repository

import com.ss.medrecord.core.common.DataResult
import com.ss.medrecord.core.common.DispatcherProvider
import com.ss.medrecord.core.location.Coordinates
import com.ss.medrecord.data.remote.PlacesDataSource
import com.ss.medrecord.domain.model.NearbyPlace
import com.ss.medrecord.domain.model.PlaceSearchType
import com.ss.medrecord.domain.repository.PlacesRepository
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Thin over [PlacesDataSource], with one thing added: results are re-sorted by
 * true distance from the origin.
 *
 * The API is asked to rank by distance and mostly does, but its notion of
 * distance is its own and results with no coordinates come back interleaved.
 * A screen that says "450 m" next to "1.2 km" above it is a screen the user
 * stops believing, so the ordering is made to match the numbers shown.
 */
@Singleton
class PlacesRepositoryImpl @Inject constructor(
    private val placesDataSource: PlacesDataSource,
    private val dispatchers: DispatcherProvider,
) : PlacesRepository {

    override val isConfigured: Boolean get() = placesDataSource.isConfigured

    override suspend fun searchNearby(
        origin: Coordinates,
        type: PlaceSearchType,
        radiusMetres: Double,
    ): DataResult<List<NearbyPlace>> = withContext(dispatchers.io) {
        DataResult.catching(PlacesDataSource::mapPlacesError) {
            placesDataSource.searchNearby(origin, type, radiusMetres)
                // Places with no coordinates sort last rather than first: they
                // cannot show a distance, and burying them is kinder than
                // heading the list with results the user cannot navigate to.
                .sortedBy { it.distanceMetres ?: Double.MAX_VALUE }
        }
    }

    override suspend fun details(place: NearbyPlace): DataResult<NearbyPlace> =
        withContext(dispatchers.io) {
            DataResult.catching(PlacesDataSource::mapPlacesError) {
                placesDataSource.details(place)
            }
        }
}
