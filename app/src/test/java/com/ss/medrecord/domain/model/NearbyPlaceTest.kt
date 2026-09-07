package com.ss.medrecord.domain.model

import com.ss.medrecord.core.location.Coordinates
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * The conversion from "a place Google knows about" to "somewhere this account
 * has been treated".
 *
 * This is the only point in the app where third-party data becomes a user
 * record, so what does and does not cross the boundary is worth pinning.
 */
class NearbyPlaceTest {

    @Test
    fun `saving carries across the details worth keeping`() {
        val facility = place().toFacility(USER_ID)

        assertEquals("Sunrise Hospital", facility.name)
        assertEquals("44 Residency Road", facility.address)
        assertEquals("+91 80 1234 5678", facility.phone)
        assertEquals(12.97, facility.latitude!!, 0.0001)
        assertEquals(77.60, facility.longitude!!, 0.0001)
        assertEquals(USER_ID, facility.userId)
    }

    @Test
    fun `the saved facility has coordinates, so it can be mapped`() {
        assertEquals(true, place().toFacility(USER_ID).hasLocation)
    }

    @Test
    fun `a place with no coordinates still saves`() {
        // Places occasionally returns a result with no location. Refusing to
        // save it would lose a clinic the user explicitly chose over a field
        // they never asked for.
        val facility = place(coordinates = null).toFacility(USER_ID)

        assertNull(facility.latitude)
        assertEquals(false, facility.hasLocation)
        assertEquals("Sunrise Hospital", facility.name)
    }

    @Test
    fun `the id is left blank for the repository to assign`() {
        // A search result carries Google's place id; the saved record must get
        // this app's own, or two users saving the same clinic would collide.
        assertEquals("", place().toFacility(USER_ID).facilityId)
    }

    @Test
    fun `the search type decides what kind of facility is saved`() {
        val lab = place().copy(type = PlaceSearchType.LAB.facilityType)

        assertEquals(FacilityType.DIAGNOSTIC_CENTER, lab.toFacility(USER_ID).type)
    }

    @Test
    fun `a pharmacy is saved as a clinic`() {
        // There is no pharmacy FacilityType, and inventing one would put a
        // chemist in the "where were you treated" picker. Clinic is the least
        // wrong of the three that exist.
        assertEquals(FacilityType.CLINIC, PlaceSearchType.PHARMACY.facilityType)
    }

    private fun place(
        coordinates: Coordinates? = Coordinates(12.97, 77.60),
    ) = NearbyPlace(
        placeId = "google-place-id",
        name = "Sunrise Hospital",
        address = "44 Residency Road",
        coordinates = coordinates,
        phone = "+91 80 1234 5678",
        distanceMetres = 420.0,
        type = FacilityType.HOSPITAL,
    )

    private companion object {
        const val USER_ID = "u1"
    }
}
