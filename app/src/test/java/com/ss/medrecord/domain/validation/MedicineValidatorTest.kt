package com.ss.medrecord.domain.validation

import com.ss.medrecord.domain.model.MedicineFrequency
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class MedicineValidatorTest {

    @Test
    fun `a name is required`() {
        assertFalse(MedicineValidator.validateName("   ").isValid)
        assertTrue(MedicineValidator.validateName("Metformin").isValid)
    }

    @Test
    fun `a course may start in the past`() {
        // People log what they are already taking; a start date months back is
        // the normal case, not a mistake.
        val lastYear = LocalDate.now().minusYears(1).toEpochDay()
        assertTrue(MedicineValidator.validateStartDate(lastYear).isValid)
    }

    @Test
    fun `a start date far in the future is rejected`() {
        val absurd = LocalDate.now().plusYears(5).toEpochDay()
        assertFalse(MedicineValidator.validateStartDate(absurd).isValid)
    }

    @Test
    fun `the course cannot end before it starts`() {
        val start = LocalDate.of(2026, 3, 10).toEpochDay()
        val end = LocalDate.of(2026, 3, 1).toEpochDay()

        assertEquals(
            "The course cannot end before it starts",
            MedicineValidator.validateEndDate(end, start).errorOrNull,
        )
    }

    @Test
    fun `an open-ended course is valid`() {
        val start = LocalDate.now().toEpochDay()
        assertTrue(MedicineValidator.validateEndDate(null, start).isValid)
    }

    @Test
    fun `a scheduled medicine needs at least one time`() {
        // Otherwise it sits in the list looking managed while reminding nobody.
        assertFalse(
            MedicineValidator.validateTimes(emptyList(), MedicineFrequency.DAILY).isValid,
        )
    }

    @Test
    fun `an as-needed medicine needs no times`() {
        assertTrue(
            MedicineValidator.validateTimes(emptyList(), MedicineFrequency.AS_NEEDED).isValid,
        )
    }

    @Test
    fun `duplicate times are rejected`() {
        val eight = 8 * 60
        assertFalse(
            MedicineValidator.validateTimes(
                listOf(eight, eight),
                MedicineFrequency.DAILY,
            ).isValid,
        )
    }

    @Test
    fun `more doses than a day can hold is rejected`() {
        val tooMany = (0 until MedicineValidator.MAX_TIMES_PER_DAY + 1).map { it * 60 }
        assertFalse(MedicineValidator.validateTimes(tooMany, MedicineFrequency.DAILY).isValid)
    }

    @Test
    fun `a time outside the day is rejected`() {
        assertFalse(
            MedicineValidator.validateTimes(listOf(24 * 60), MedicineFrequency.DAILY).isValid,
        )
    }

    @Test
    fun `long free text is rejected rather than silently truncated`() {
        val long = "x".repeat(MedicineValidator.MAX_INSTRUCTIONS_LENGTH + 1)
        assertFalse(MedicineValidator.validateInstructions(long).isValid)
        assertTrue(MedicineValidator.validateInstructions("After food").isValid)
    }
}
