package com.ss.medrecord.domain.model

import com.ss.medrecord.core.location.Coordinates

/**
 * A medical facility found by searching, not yet saved (spec section 5.10).
 *
 * Deliberately not a [Facility]. A search result is somebody else's data about
 * a place that happens to exist; a facility is a record that this account has
 * been treated somewhere. Collapsing them would put every clinic the user ever
 * scrolled past into their medical history, and the whole point of the save
 * step is that it is a decision.
 *
 * Nothing here is persisted until the user saves it, and the search itself is
 * never recorded - what someone looked for is as revealing as where they went.
 */
data class NearbyPlace(
    /** Google's id for the place, used to fetch details on demand. */
    val placeId: String,
    val name: String,
    val address: String?,
    val coordinates: Coordinates?,
    val phone: String? = null,
    /** Straight-line metres from the search origin; null when there was none. */
    val distanceMetres: Double? = null,
    val type: FacilityType = FacilityType.CLINIC,
) {
    /**
     * Converts a saved search result into a record on this account.
     *
     * The address and coordinates come across; the place id deliberately does
     * not. Once saved, this is the user's record of where they were treated,
     * and tying it to a Google identifier would make it silently dependent on
     * that place still existing in someone else's database.
     */
    fun toFacility(userId: String): Facility = Facility(
        facilityId = "",
        userId = userId,
        name = name,
        type = type,
        address = address,
        latitude = coordinates?.latitude,
        longitude = coordinates?.longitude,
        phone = phone,
        createdAt = 0L,
        updatedAt = 0L,
    )
}

/**
 * What the nearby search is looking for. Maps onto the Places API's own type
 * vocabulary in the data layer, which is why this is a search concept rather
 * than reusing [FacilityType] directly - "pharmacy" is worth searching for and
 * is not a place visits are logged at.
 */
enum class PlaceSearchType(val label: String, val facilityType: FacilityType) {
    HOSPITAL("Hospitals", FacilityType.HOSPITAL),
    CLINIC("Clinics", FacilityType.CLINIC),
    PHARMACY("Pharmacies", FacilityType.CLINIC),
    LAB("Labs", FacilityType.DIAGNOSTIC_CENTER),
}
