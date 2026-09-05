package com.ss.medrecord.domain.validation

/**
 * Client-side input rules for the auth forms. Pure and side-effect free so the
 * ViewModels can validate on every keystroke and the rules can be unit tested
 * without Android or Firebase.
 *
 * These are a UX affordance, not a security control - Firebase enforces its own
 * rules server-side regardless of what passes here.
 */
object AuthValidator {

    const val MIN_PASSWORD_LENGTH = 8
    const val MIN_NAME_LENGTH = 2

    fun validateName(name: String): ValidationResult {
        val trimmed = name.trim()
        return when {
            trimmed.isEmpty() -> ValidationResult.invalid("Name is required")
            trimmed.length < MIN_NAME_LENGTH ->
                ValidationResult.invalid("Name must be at least $MIN_NAME_LENGTH characters")

            else -> ValidationResult.Valid
        }
    }

    fun validateEmail(email: String): ValidationResult {
        val trimmed = email.trim()
        return when {
            trimmed.isEmpty() -> ValidationResult.invalid("Email is required")
            !EMAIL_PATTERN.matches(trimmed) -> ValidationResult.invalid("Enter a valid email address")
            else -> ValidationResult.Valid
        }
    }

    /**
     * Length plus character variety. Firebase itself only requires six
     * characters; health data warrants more, so the floor is raised here.
     */
    fun validatePassword(password: String): ValidationResult = when {
        password.isEmpty() -> ValidationResult.invalid("Password is required")
        password.length < MIN_PASSWORD_LENGTH ->
            ValidationResult.invalid("Use at least $MIN_PASSWORD_LENGTH characters")

        password.none { it.isDigit() } ->
            ValidationResult.invalid("Include at least one number")

        password.none { it.isLetter() } ->
            ValidationResult.invalid("Include at least one letter")

        else -> ValidationResult.Valid
    }

    fun validatePasswordConfirmation(password: String, confirmation: String): ValidationResult =
        when {
            confirmation.isEmpty() -> ValidationResult.invalid("Confirm your password")
            password != confirmation -> ValidationResult.invalid("Passwords do not match")
            else -> ValidationResult.Valid
        }

    /**
     * Deliberately permissive: one at-sign, a dot-separated domain, no spaces.
     * Rejecting exotic-but-legal addresses is worse than letting Firebase have
     * the final say.
     */
    private val EMAIL_PATTERN = Regex("""^[^\s@]+@[^\s@]+\.[^\s@]{2,}$""")
}

/** Outcome of one field check; [errorMessage] is user-presentable when invalid. */
sealed interface ValidationResult {
    data object Valid : ValidationResult
    data class Invalid(val errorMessage: String) : ValidationResult

    val isValid: Boolean get() = this is Valid
    val errorOrNull: String? get() = (this as? Invalid)?.errorMessage

    companion object {
        fun invalid(message: String): ValidationResult = Invalid(message)
    }
}
