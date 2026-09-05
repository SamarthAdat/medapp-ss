package com.ss.medrecord.ui.feature.auth.signup

import com.ss.medrecord.core.ui.UiEffect
import com.ss.medrecord.core.ui.UiEvent
import com.ss.medrecord.core.ui.UiState

data class SignUpUiState(
    val name: String = "",
    val email: String = "",
    val password: String = "",
    val confirmPassword: String = "",
    val nameError: String? = null,
    val emailError: String? = null,
    val passwordError: String? = null,
    val confirmPasswordError: String? = null,
    val isSubmitting: Boolean = false,
) : UiState {
    val canSubmit: Boolean
        get() = !isSubmitting &&
            name.isNotBlank() &&
            email.isNotBlank() &&
            password.isNotBlank() &&
            confirmPassword.isNotBlank()
}

sealed interface SignUpEvent : UiEvent {
    data class NameChanged(val value: String) : SignUpEvent
    data class EmailChanged(val value: String) : SignUpEvent
    data class PasswordChanged(val value: String) : SignUpEvent
    data class ConfirmPasswordChanged(val value: String) : SignUpEvent
    data object Submit : SignUpEvent
    data object SignInClicked : SignUpEvent
}

sealed interface SignUpEffect : UiEffect {
    /**
     * The account exists. The session gate takes over and routes to consent,
     * which is mandatory before any patient data can be entered (spec 9.5).
     */
    data object AccountCreated : SignUpEffect
    data object NavigateToSignIn : SignUpEffect
    data class ShowMessage(val message: String) : SignUpEffect
}
