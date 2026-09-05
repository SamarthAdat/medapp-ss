package com.ss.medrecord.domain.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDate

class PatientTest {

    @Test
    fun `age is whole years and accounts for a birthday later this year`() {
        val today = LocalDate.of(2026, 9, 5)

        // Birthday already passed this year.
        assertEquals(34, patient(LocalDate.of(1992, 3, 1)).ageYears(today))
        // Birthday still to come this year.
        assertEquals(33, patient(LocalDate.of(1992, 12, 1)).ageYears(today))
        // Birthday is today.
        assertEquals(34, patient(LocalDate.of(1992, 9, 5)).ageYears(today))
        // Born today.
        assertEquals(0, patient(today).ageYears(today))
    }

    @Test
    fun `age is null without a date of birth`() {
        assertNull(
            Patient(
                patientId = "p1",
                userId = "u1",
                name = "No DOB",
                relationship = Relationship.OTHER,
                createdAt = 0L,
                updatedAt = 0L,
            ).ageYears(LocalDate.of(2026, 9, 5)),
        )
    }

    @Test
    fun `a future date of birth yields no age rather than a negative one`() {
        val today = LocalDate.of(2026, 9, 5)
        assertNull(patient(today.plusDays(1)).ageYears(today))
    }

    @Test
    fun `epoch day round-trips without timezone drift`() {
        val date = LocalDate.of(1992, 3, 1)
        assertEquals(date, patient(date).dateOfBirth)
    }

    @Test
    fun `initials take the first two words`() {
        assertEquals("AR", named("Asha Rao").initials)
        assertEquals("AK", named("asha kumari rao").initials)
        assertEquals("A", named("Asha").initials)
        // Collapses runs of whitespace rather than producing a blank initial.
        assertEquals("AR", named("  Asha   Rao  ").initials)
    }

    @Test
    fun `initials fall back rather than crash on an empty name`() {
        assertEquals("?", named("   ").initials)
    }

    @Test
    fun `deletedAt drives isDeleted`() {
        assertEquals(false, named("Asha").isDeleted)
        assertEquals(true, named("Asha").copy(deletedAt = 1L).isDeleted)
    }

    private fun patient(dob: LocalDate) = named("Test Patient").copy(
        dateOfBirthEpochDay = dob.toEpochDay(),
    )

    private fun named(name: String) = Patient(
        patientId = "p1",
        userId = "u1",
        name = name,
        relationship = Relationship.SELF,
        createdAt = 0L,
        updatedAt = 0L,
    )
}
