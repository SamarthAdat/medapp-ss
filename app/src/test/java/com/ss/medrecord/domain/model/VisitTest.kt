package com.ss.medrecord.domain.model

import com.ss.medrecord.core.common.toInitials
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class VisitTest {

    private val today = LocalDate.of(2026, 9, 5)

    @Test
    fun `dates round-trip through epoch day without drift`() {
        val visitDate = LocalDate.of(2026, 3, 1)
        val visit = visit(visitDate = visitDate)

        assertEquals(visitDate, visit.visitDate)
    }

    @Test
    fun `days until next visit counts forward and backward`() {
        assertEquals(7L, visit(nextDate = today.plusDays(7)).daysUntilNextVisit(today))
        assertEquals(0L, visit(nextDate = today).daysUntilNextVisit(today))
        // Negative once it has passed, which is what lets the badge hide itself.
        assertEquals(-3L, visit(nextDate = today.minusDays(3)).daysUntilNextVisit(today))
    }

    @Test
    fun `no next visit means no countdown`() {
        assertNull(visit(nextDate = null).daysUntilNextVisit(today))
        assertFalse(visit(nextDate = null).hasUpcomingVisit(today))
    }

    @Test
    fun `an appointment today still counts as upcoming`() {
        assertTrue(visit(nextDate = today).hasUpcomingVisit(today))
        assertTrue(visit(nextDate = today.plusDays(1)).hasUpcomingVisit(today))
        assertFalse(visit(nextDate = today.minusDays(1)).hasUpcomingVisit(today))
    }

    @Test
    fun `deletedAt drives isDeleted`() {
        assertFalse(visit().isDeleted)
        assertTrue(visit().copy(deletedAt = 1L).isDeleted)
    }

    @Test
    fun `a missing facility falls back to a readable label`() {
        val withFacility = VisitWithFacility(visit = visit(), facility = facility("City Care"))
        assertEquals("City Care", withFacility.facilityName)

        // Happens mid-sync when the visit has arrived but the clinic has not.
        val orphan = VisitWithFacility(visit = visit(), facility = null)
        assertEquals("Unknown facility", orphan.facilityName)
    }

    @Test
    fun `facility initials share the patient helper`() {
        assertEquals("CC", facility("City Care Clinic").initials)
        assertEquals("A", facility("Apollo").initials)
        assertEquals("?", facility("   ").initials)
        assertEquals("CC", "City Care".toInitials())
    }

    private fun visit(
        visitDate: LocalDate = today,
        nextDate: LocalDate? = null,
    ) = Visit(
        visitId = "v1",
        userId = "u1",
        patientId = "p1",
        facilityId = "f1",
        visitDateEpochDay = visitDate.toEpochDay(),
        nextVisitDateEpochDay = nextDate?.toEpochDay(),
        createdAt = 0L,
        updatedAt = 0L,
    )

    private fun facility(name: String) = Facility(
        facilityId = "f1",
        userId = "u1",
        name = name,
        type = FacilityType.CLINIC,
        createdAt = 0L,
        updatedAt = 0L,
    )
}
