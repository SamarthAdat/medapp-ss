package com.ss.medrecord.core.location

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Distance arithmetic and how it is shown.
 *
 * Worth pinning because it is the only number on the nearby screen the user can
 * check against reality: if "450 m" is wrong, or if a list ordered by it
 * disagrees with the numbers printed on it, the whole screen stops being
 * trusted.
 */
class CoordinatesTest {

    @Test
    fun `the distance from a point to itself is zero`() {
        val point = Coordinates(12.9716, 77.5946)

        assertEquals(0.0, point.distanceMetresTo(point), 0.001)
    }

    @Test
    fun `distance is symmetric`() {
        val a = Coordinates(12.9716, 77.5946)
        val b = Coordinates(12.9352, 77.6245)

        assertEquals(a.distanceMetresTo(b), b.distanceMetresTo(a), 0.001)
    }

    @Test
    fun `a known separation comes out about right`() {
        // Bengaluru city centre to Koramangala, roughly 5 km apart.
        val cityCentre = Coordinates(12.9716, 77.5946)
        val koramangala = Coordinates(12.9352, 77.6245)

        val metres = cityCentre.distanceMetresTo(koramangala)

        assertTrue("expected roughly 5 km, got $metres m", metres in 4_000.0..6_000.0)
    }

    @Test
    fun `a degree of latitude is about 111 km`() {
        // The one case with an exact known answer, independent of longitude.
        val south = Coordinates(0.0, 0.0)
        val north = Coordinates(1.0, 0.0)

        assertEquals(111_195.0, south.distanceMetresTo(north), 500.0)
    }

    @Test
    fun `crossing the antimeridian does not produce a wrong short distance`() {
        // Naive subtraction of longitudes gives ~40,000 km here instead of ~220.
        val west = Coordinates(0.0, 179.0)
        val east = Coordinates(0.0, -179.0)

        val metres = west.distanceMetresTo(east)

        assertTrue("expected about 222 km, got $metres m", metres in 200_000.0..250_000.0)
    }

    @Test
    fun `short distances are shown in whole metres`() {
        assertEquals("450 m", formatDistance(450.4))
        assertEquals("999 m", formatDistance(999.9))
    }

    @Test
    fun `distances over a kilometre gain a decimal`() {
        assertEquals("1.0 km", formatDistance(1000.0))
        assertEquals("2.3 km", formatDistance(2340.0))
    }

    @Test
    fun `long distances drop the decimal`() {
        // "12.4 km away" is false precision for somewhere you would drive to.
        assertEquals("12 km", formatDistance(12_400.0))
    }
}
