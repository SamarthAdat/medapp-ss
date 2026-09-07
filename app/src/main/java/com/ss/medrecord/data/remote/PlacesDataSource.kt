package com.ss.medrecord.data.remote

import android.content.Context
import com.google.android.gms.maps.model.LatLng
import com.google.android.libraries.places.api.Places
import com.google.android.libraries.places.api.model.CircularBounds
import com.google.android.libraries.places.api.model.Place
import com.google.android.libraries.places.api.model.PlaceTypes
import com.google.android.libraries.places.api.net.FetchPlaceRequest
import com.google.android.libraries.places.api.net.PlacesClient
import com.google.android.libraries.places.api.net.SearchNearbyRequest
import com.ss.medrecord.BuildConfig
import com.ss.medrecord.core.common.AppError
import com.ss.medrecord.core.location.Coordinates
import com.ss.medrecord.domain.model.NearbyPlace
import com.ss.medrecord.domain.model.PlaceSearchType
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.tasks.await
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

/**
 * The Places API, wrapped so nothing above this layer knows it exists.
 *
 * Initialised lazily rather than at app start: most sessions never open the
 * nearby screen, and there is no reason to construct a network client - or to
 * fail loudly about a missing key - for a feature the user has not asked for.
 *
 * Requests are deliberately narrow. Places bills per field, so the search asks
 * for the five fields a result row renders and nothing else; the phone number,
 * which only the saved facility needs, is fetched once for the one place the
 * user picks. Asking for phone numbers on twenty results the user scrolls past
 * costs twenty times what asking for the one they chose does.
 */
@Singleton
class PlacesDataSource @Inject constructor(
    @param:ApplicationContext private val context: Context,
) {

    val isConfigured: Boolean get() = BuildConfig.HAS_MAPS_KEY

    private val client: PlacesClient? by lazy {
        if (!isConfigured) return@lazy null
        // searchNearby is part of the new Places API and is unavailable on a
        // client initialised the legacy way.
        if (!Places.isInitialized()) {
            Places.initializeWithNewPlacesApiEnabled(context, BuildConfig.MAPS_API_KEY)
        }
        Places.createClient(context)
    }

    suspend fun searchNearby(
        origin: Coordinates,
        type: PlaceSearchType,
        radiusMetres: Double,
    ): List<NearbyPlace> {
        val places = client ?: throw IllegalStateException(NO_KEY)

        val request = SearchNearbyRequest
            .builder(
                CircularBounds.newInstance(
                    LatLng(origin.latitude, origin.longitude),
                    radiusMetres,
                ),
                SEARCH_FIELDS,
            )
            .setIncludedTypes(type.toPlaceTypes())
            // DISTANCE, not POPULARITY: someone looking for a clinic while
            // unwell wants the nearest one, not the best reviewed one.
            .setRankPreference(SearchNearbyRequest.RankPreference.DISTANCE)
            .setMaxResultCount(MAX_RESULTS)
            .build()

        return places.searchNearby(request).await().places.map { it.toNearbyPlace(origin, type) }
    }

    /** Fills in the phone number for one place the user is about to save. */
    suspend fun details(place: NearbyPlace): NearbyPlace {
        val places = client ?: throw IllegalStateException(NO_KEY)

        val request = FetchPlaceRequest.newInstance(place.placeId, DETAIL_FIELDS)
        val fetched = places.fetchPlace(request).await().place

        return place.copy(
            phone = fetched.nationalPhoneNumber?.takeIf { it.isNotBlank() } ?: place.phone,
            address = fetched.formattedAddress?.takeIf { it.isNotBlank() } ?: place.address,
        )
    }

    private fun Place.toNearbyPlace(origin: Coordinates, type: PlaceSearchType): NearbyPlace {
        val coordinates = location?.let { Coordinates(it.latitude, it.longitude) }
        return NearbyPlace(
            placeId = id.orEmpty(),
            // A result with no name is not showable, and the API does return
            // one occasionally for places mid-edit in their index.
            name = displayName?.takeIf { it.isNotBlank() } ?: "Unnamed place",
            address = formattedAddress?.takeIf { it.isNotBlank() },
            coordinates = coordinates,
            distanceMetres = coordinates?.let(origin::distanceMetresTo),
            type = type.facilityType,
        )
    }

    /**
     * One search type maps to several Places types. "Clinic" in particular has
     * no single equivalent - a GP surgery is a `doctor` and a small hospital is
     * a `hospital`, and a user looking for a clinic means either.
     */
    private fun PlaceSearchType.toPlaceTypes(): List<String> = when (this) {
        PlaceSearchType.HOSPITAL -> listOf(PlaceTypes.HOSPITAL)
        PlaceSearchType.CLINIC -> listOf(PlaceTypes.DOCTOR, PlaceTypes.HOSPITAL)
        PlaceSearchType.PHARMACY -> listOf(PlaceTypes.PHARMACY, PlaceTypes.DRUGSTORE)
        PlaceSearchType.LAB -> listOf(PlaceTypes.DOCTOR, PlaceTypes.DENTIST, PlaceTypes.PHYSIOTHERAPIST)
    }

    companion object {
        private const val NO_KEY = "No Maps API key is configured for this build."

        private const val MAX_RESULTS = 20

        /** What a result row renders. Every extra field here is billed per call. */
        private val SEARCH_FIELDS = listOf(
            Place.Field.ID,
            Place.Field.DISPLAY_NAME,
            Place.Field.FORMATTED_ADDRESS,
            Place.Field.LOCATION,
        )

        /** Only fetched for the one place the user decides to save. */
        private val DETAIL_FIELDS = listOf(
            Place.Field.ID,
            Place.Field.FORMATTED_ADDRESS,
            Place.Field.NATIONAL_PHONE_NUMBER,
        )

        /**
         * Places failures are network failures or quota/key failures, and the
         * two need different words: one is worth retrying, the other is a
         * configuration problem the user cannot fix by moving somewhere with
         * better signal.
         */
        fun mapPlacesError(throwable: Throwable): AppError = when {
            throwable is IllegalStateException -> AppError.Validation(throwable.message.orEmpty())
            throwable is IOException -> AppError.Network(throwable)
            throwable.message?.contains("API key", ignoreCase = true) == true ->
                AppError.Validation("This build's Maps API key was rejected.")

            throwable.message?.contains("quota", ignoreCase = true) == true ->
                AppError.Validation("The Places quota for this key has run out.")

            else -> AppError.Unknown(throwable)
        }
    }
}
