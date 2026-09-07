package com.ss.medrecord.domain.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

/**
 * The schedule rules that decide when a reminder fires. Wrong answers here are
 * missed doses, so each case is pinned rather than inferred from the engine.
 */
class MedicineTest {

    @Test
    fun `a daily course falls on every day in range`() {
        val medicine = medicine(frequency = MedicineFrequency.DAILY)

        assertTrue(medicine.fallsOn(START))
        assertTrue(medicine.fallsOn(START.plusDays(1)))
        assertTrue(medicine.fallsOn(START.plusDays(2)))
    }

    @Test
    fun `an alternate-day course is anchored to its start date`() {
        // Anchored, not driven by the calendar: otherwise a course starting on
        // an odd day would drift a dose every time the schedule was rebuilt.
        val medicine = medicine(frequency = MedicineFrequency.ALTERNATE_DAYS)

        assertTrue(medicine.fallsOn(START))
        assertFalse(medicine.fallsOn(START.plusDays(1)))
        assertTrue(medicine.fallsOn(START.plusDays(2)))
        assertFalse(medicine.fallsOn(START.plusDays(3)))
    }

    @Test
    fun `a weekly course repeats on its start weekday`() {
        val medicine = medicine(frequency = MedicineFrequency.WEEKLY)

        assertTrue(medicine.fallsOn(START))
        assertFalse(medicine.fallsOn(START.plusDays(6)))
        assertTrue(medicine.fallsOn(START.plusDays(7)))
    }

    @Test
    fun `an as-needed medicine never falls on a day`() {
        val medicine = medicine(frequency = MedicineFrequency.AS_NEEDED)

        assertFalse(medicine.fallsOn(START))
        assertFalse(medicine.fallsOn(START.plusDays(1)))
    }

    @Test
    fun `an as-needed medicine is still current`() {
        // It has no schedule, but it is part of what the person is taking - the
        // reminder engine skips it, the medicine list must not.
        val medicine = medicine(frequency = MedicineFrequency.AS_NEEDED)

        assertTrue(medicine.isCurrentOn(START))
    }

    @Test
    fun `nothing falls before the course starts`() {
        val medicine = medicine()

        assertFalse(medicine.fallsOn(START.minusDays(1)))
    }

    @Test
    fun `nothing falls after the course ends`() {
        val medicine = medicine(endDate = START.plusDays(4))

        assertTrue(medicine.fallsOn(START.plusDays(4)))
        assertFalse(medicine.fallsOn(START.plusDays(5)))
    }

    @Test
    fun `a paused course falls on nothing`() {
        val medicine = medicine(isActive = false)

        assertFalse(medicine.fallsOn(START))
        assertFalse(medicine.isCurrentOn(START))
    }

    @Test
    fun `a deleted course falls on nothing`() {
        val medicine = medicine().copy(deletedAt = 1L)

        assertFalse(medicine.fallsOn(START))
    }

    @Test
    fun `times are exposed in ascending order whatever order they were stored`() {
        val medicine = medicine(times = listOf(20 * 60, 8 * 60, 13 * 60))

        assertEquals(
            listOf("08:00", "13:00", "20:00"),
            medicine.times.map(Medicine::formatTime),
        )
    }

    @Test
    fun `doses per day is the number of times`() {
        assertEquals(2, medicine(times = listOf(8 * 60, 20 * 60)).dosesPerDay)
    }

    @Test
    fun `the schedule summary omits times for an as-needed medicine`() {
        val medicine = medicine(
            frequency = MedicineFrequency.AS_NEEDED,
            times = listOf(8 * 60),
        )

        assertEquals("As needed", medicine.scheduleSummary())
    }

    @Test
    fun `the schedule summary lists times for a scheduled medicine`() {
        val medicine = medicine(times = listOf(8 * 60, 20 * 60))

        assertEquals("Every day · 08:00, 20:00", medicine.scheduleSummary())
    }

    private fun medicine(
        frequency: MedicineFrequency = MedicineFrequency.DAILY,
        times: List<Int> = listOf(8 * 60),
        endDate: LocalDate? = null,
        isActive: Boolean = true,
    ) = Medicine(
        medicineId = "m1",
        userId = "u1",
        patientId = "p1",
        name = "Metformin",
        dosage = "500 mg",
        frequency = frequency,
        reminderTimes = times,
        startDateEpochDay = START.toEpochDay(),
        endDateEpochDay = endDate?.toEpochDay(),
        isActive = isActive,
        createdAt = 0L,
        updatedAt = 0L,
    )

    private companion object {
        val START: LocalDate = LocalDate.of(2026, 3, 2)
    }
}
