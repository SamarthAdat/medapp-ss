package com.ss.medrecord.domain.validation

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class VisitValidatorTest {

    private val today = LocalDate.of(2026, 9, 5)

    @Test
    fun `visit date is required`() {
        val result = VisitValidator.validateVisitDate(null, today)

        assertFalse(result.isValid)
        assertEquals("Visit date is required", result.errorOrNull)
    }

    @Test
    fun `a visit cannot be in the future`() {
        // A future appointment is a next-visit date, which is its own field.
        val tomorrow = today.plusDays(1).toEpochDay()
        assertFalse(VisitValidator.validateVisitDate(tomorrow, today).isValid)
    }

    @Test
    fun `today is a valid visit date`() {
        assertTrue(VisitValidator.validateVisitDate(today.toEpochDay(), today).isValid)
    }

    @Test
    fun `implausibly old visits are rejected`() {
        val ancient = today.minusYears(VisitValidator.MAX_HISTORY_YEARS + 1).toEpochDay()
        assertFalse(VisitValidator.validateVisitDate(ancient, today).isValid)

        val old = today.minusYears(30).toEpochDay()
        assertTrue(VisitValidator.validateVisitDate(old, today).isValid)
    }

    @Test
    fun `next visit is optional`() {
        assertTrue(
            VisitValidator.validateNextVisitDate(
                nextEpochDay = null,
                visitEpochDay = today.toEpochDay(),
            ).isValid,
        )
    }

    @Test
    fun `next visit cannot precede the visit that scheduled it`() {
        val visit = today.toEpochDay()
        val before = today.minusDays(1).toEpochDay()

        val result = VisitValidator.validateNextVisitDate(before, visit)

        assertFalse(result.isValid)
        assertEquals("Next visit cannot be before the visit itself", result.errorOrNull)
    }

    @Test
    fun `next visit on the same day is allowed`() {
        // Same-day follow-up is real: bloods in the morning, results after lunch.
        val visit = today.toEpochDay()
        assertTrue(VisitValidator.validateNextVisitDate(visit, visit).isValid)
    }

    @Test
    fun `next visit in the future is allowed`() {
        val visit = today.toEpochDay()
        val later = today.plusMonths(6).toEpochDay()
        assertTrue(VisitValidator.validateNextVisitDate(later, visit).isValid)
    }

    @Test
    fun `a facility must be named`() {
        assertFalse(VisitValidator.validateFacility("").isValid)
        assertFalse(VisitValidator.validateFacility("   ").isValid)
        assertTrue(VisitValidator.validateFacility("City Care").isValid)
    }

    @Test
    fun `doctor name and notes are capped but optional`() {
        assertTrue(VisitValidator.validateDoctorName("").isValid)
        assertFalse(
            VisitValidator.validateDoctorName(
                "x".repeat(VisitValidator.MAX_DOCTOR_NAME_LENGTH + 1),
            ).isValid,
        )
        assertTrue(VisitValidator.validateNotes("").isValid)
        assertFalse(
            VisitValidator.validateNotes("x".repeat(VisitValidator.MAX_NOTES_LENGTH + 1)).isValid,
        )
    }
}
