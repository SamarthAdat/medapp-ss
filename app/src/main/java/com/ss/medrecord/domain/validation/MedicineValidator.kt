package com.ss.medrecord.domain.validation

import com.ss.medrecord.domain.model.MedicineFrequency
import java.time.LocalDate

/** Input rules for the add/edit medicine form (spec section 5.8). */
object MedicineValidator {

    const val MAX_NAME_LENGTH = 100
    const val MAX_DOSAGE_LENGTH = 60
    const val MAX_INSTRUCTIONS_LENGTH = 500

    /** More doses than this in a day is a typo, not a prescription. */
    const val MAX_TIMES_PER_DAY = 8

    /** A course starting further ahead than this is almost certainly a slip. */
    const val MAX_FUTURE_START_DAYS = 365L

    fun validateName(name: String): ValidationResult {
        val trimmed = name.trim()
        return when {
            trimmed.isEmpty() -> ValidationResult.invalid("Medicine name is required")
            trimmed.length > MAX_NAME_LENGTH ->
                ValidationResult.invalid("Keep the name under $MAX_NAME_LENGTH characters")

            else -> ValidationResult.Valid
        }
    }

    fun validateDosage(dosage: String): ValidationResult = when {
        dosage.length > MAX_DOSAGE_LENGTH ->
            ValidationResult.invalid("Keep the dosage under $MAX_DOSAGE_LENGTH characters")

        else -> ValidationResult.Valid
    }

    fun validateInstructions(instructions: String): ValidationResult = when {
        instructions.length > MAX_INSTRUCTIONS_LENGTH ->
            ValidationResult.invalid("Keep instructions under $MAX_INSTRUCTIONS_LENGTH characters")

        else -> ValidationResult.Valid
    }

    /**
     * A course may start in the past - people log what they are already taking -
     * so only the future end of the range is bounded.
     */
    fun validateStartDate(
        epochDay: Long?,
        today: LocalDate = LocalDate.now(),
    ): ValidationResult {
        if (epochDay == null) return ValidationResult.invalid("Start date is required")
        val date = LocalDate.ofEpochDay(epochDay)
        return when {
            date.isAfter(today.plusDays(MAX_FUTURE_START_DAYS)) ->
                ValidationResult.invalid("Check the start date")

            else -> ValidationResult.Valid
        }
    }

    fun validateEndDate(endEpochDay: Long?, startEpochDay: Long?): ValidationResult {
        if (endEpochDay == null) return ValidationResult.Valid
        if (startEpochDay != null && endEpochDay < startEpochDay) {
            return ValidationResult.invalid("The course cannot end before it starts")
        }
        return ValidationResult.Valid
    }

    /**
     * A scheduled medicine with no times would sit in the list looking managed
     * while never reminding anyone, which is worse than not adding it. As-needed
     * medicines are exempt: they have no schedule by definition.
     */
    fun validateTimes(
        times: List<Int>,
        frequency: MedicineFrequency,
    ): ValidationResult = when {
        !frequency.schedulesDoses -> ValidationResult.Valid
        times.isEmpty() -> ValidationResult.invalid("Add at least one reminder time")
        times.size > MAX_TIMES_PER_DAY ->
            ValidationResult.invalid("At most $MAX_TIMES_PER_DAY times a day")

        times.distinct().size != times.size ->
            ValidationResult.invalid("That time is already on the list")

        times.any { it !in 0..MINUTES_IN_DAY_EXCLUSIVE } ->
            ValidationResult.invalid("Check the reminder times")

        else -> ValidationResult.Valid
    }

    private const val MINUTES_IN_DAY_EXCLUSIVE = 24 * 60 - 1
}
