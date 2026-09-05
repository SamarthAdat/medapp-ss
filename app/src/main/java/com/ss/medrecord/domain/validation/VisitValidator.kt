package com.ss.medrecord.domain.validation

import java.time.LocalDate

/** Input rules for the add/edit visit form (spec section 5.5). */
object VisitValidator {

    const val MAX_DOCTOR_NAME_LENGTH = 80
    const val MAX_NOTES_LENGTH = 2000

    /** Records older than this are almost certainly a mistyped year. */
    const val MAX_HISTORY_YEARS = 120L

    fun validateVisitDate(
        epochDay: Long?,
        today: LocalDate = LocalDate.now(),
    ): ValidationResult {
        if (epochDay == null) return ValidationResult.invalid("Visit date is required")
        val date = LocalDate.ofEpochDay(epochDay)
        return when {
            // A visit is something that happened. A future one is a next-visit
            // date, which is a separate field.
            date.isAfter(today) ->
                ValidationResult.invalid("Visit date cannot be in the future")

            date.isBefore(today.minusYears(MAX_HISTORY_YEARS)) ->
                ValidationResult.invalid("Check the visit date")

            else -> ValidationResult.Valid
        }
    }

    /**
     * The next visit is a forward-looking appointment, so it must not precede
     * the visit that scheduled it.
     */
    fun validateNextVisitDate(
        nextEpochDay: Long?,
        visitEpochDay: Long?,
    ): ValidationResult {
        if (nextEpochDay == null) return ValidationResult.Valid
        if (visitEpochDay != null && nextEpochDay < visitEpochDay) {
            return ValidationResult.invalid("Next visit cannot be before the visit itself")
        }
        return ValidationResult.Valid
    }

    fun validateFacility(facilityName: String): ValidationResult = when {
        facilityName.isBlank() -> ValidationResult.invalid("Choose or enter a clinic or hospital")
        else -> ValidationResult.Valid
    }

    fun validateDoctorName(name: String): ValidationResult = when {
        name.length > MAX_DOCTOR_NAME_LENGTH ->
            ValidationResult.invalid("Keep the name under $MAX_DOCTOR_NAME_LENGTH characters")

        else -> ValidationResult.Valid
    }

    fun validateNotes(notes: String): ValidationResult = when {
        notes.length > MAX_NOTES_LENGTH ->
            ValidationResult.invalid("Keep notes under $MAX_NOTES_LENGTH characters")

        else -> ValidationResult.Valid
    }
}
