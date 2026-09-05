package com.ss.medrecord.domain.validation

import java.time.LocalDate

/**
 * Input rules for the add/edit patient form. Pure, so the rules can be unit
 * tested without Android.
 */
object PatientValidator {

    const val MIN_NAME_LENGTH = 2
    const val MAX_NAME_LENGTH = 80
    const val MAX_ALLERGIES_LENGTH = 500

    /** Nobody alive is older than this; anything beyond it is a typo. */
    const val MAX_AGE_YEARS = 130L

    fun validateName(name: String): ValidationResult {
        val trimmed = name.trim()
        return when {
            trimmed.isEmpty() -> ValidationResult.invalid("Name is required")
            trimmed.length < MIN_NAME_LENGTH ->
                ValidationResult.invalid("Name must be at least $MIN_NAME_LENGTH characters")

            trimmed.length > MAX_NAME_LENGTH ->
                ValidationResult.invalid("Name must be under $MAX_NAME_LENGTH characters")

            else -> ValidationResult.Valid
        }
    }

    /**
     * A date of birth is optional, but a present one must be plausible. A future
     * date would make every age calculation in the app negative.
     */
    fun validateDateOfBirth(
        epochDay: Long?,
        today: LocalDate = LocalDate.now(),
    ): ValidationResult {
        if (epochDay == null) return ValidationResult.Valid
        val date = LocalDate.ofEpochDay(epochDay)
        return when {
            date.isAfter(today) -> ValidationResult.invalid("Date of birth cannot be in the future")
            date.isBefore(today.minusYears(MAX_AGE_YEARS)) ->
                ValidationResult.invalid("Check the date of birth")

            else -> ValidationResult.Valid
        }
    }

    fun validateAllergies(allergies: String): ValidationResult = when {
        allergies.length > MAX_ALLERGIES_LENGTH ->
            ValidationResult.invalid("Keep allergies under $MAX_ALLERGIES_LENGTH characters")

        else -> ValidationResult.Valid
    }
}
