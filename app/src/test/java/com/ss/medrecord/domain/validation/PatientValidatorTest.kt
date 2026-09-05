package com.ss.medrecord.domain.validation

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class PatientValidatorTest {

    private val today = LocalDate.of(2026, 9, 5)

    @Test
    fun `name must be present and of reasonable length`() {
        assertFalse(PatientValidator.validateName("").isValid)
        assertFalse(PatientValidator.validateName("   ").isValid)
        assertFalse(PatientValidator.validateName("A").isValid)
        assertTrue(PatientValidator.validateName("Jo").isValid)
        assertTrue(PatientValidator.validateName("  Asha Rao  ").isValid)
        assertFalse(PatientValidator.validateName("x".repeat(81)).isValid)
    }

    @Test
    fun `date of birth is optional`() {
        assertTrue(PatientValidator.validateDateOfBirth(null, today).isValid)
    }

    @Test
    fun `date of birth cannot be in the future`() {
        val tomorrow = today.plusDays(1).toEpochDay()
        val result = PatientValidator.validateDateOfBirth(tomorrow, today)

        assertFalse(result.isValid)
        assertEquals("Date of birth cannot be in the future", result.errorOrNull)
    }

    @Test
    fun `today is an acceptable date of birth`() {
        // A newborn is a legitimate patient, so the boundary has to be inclusive.
        assertTrue(PatientValidator.validateDateOfBirth(today.toEpochDay(), today).isValid)
    }

    @Test
    fun `implausibly old dates are rejected`() {
        val tooOld = today.minusYears(PatientValidator.MAX_AGE_YEARS + 1).toEpochDay()
        assertFalse(PatientValidator.validateDateOfBirth(tooOld, today).isValid)

        val oldButPlausible = today.minusYears(100).toEpochDay()
        assertTrue(PatientValidator.validateDateOfBirth(oldButPlausible, today).isValid)
    }

    @Test
    fun `allergies are capped but may be empty`() {
        assertTrue(PatientValidator.validateAllergies("").isValid)
        assertTrue(PatientValidator.validateAllergies("Penicillin, sulfa drugs").isValid)
        assertFalse(
            PatientValidator.validateAllergies(
                "x".repeat(PatientValidator.MAX_ALLERGIES_LENGTH + 1),
            ).isValid,
        )
    }
}
