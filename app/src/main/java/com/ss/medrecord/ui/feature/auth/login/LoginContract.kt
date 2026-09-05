package com.ss.medrecord.ui.feature.auth.login

import com.ss.medrecord.core.ui.UiEffect
import com.ss.medrecord.core.ui.UiEvent
import com.ss.medrecord.core.ui.UiState

data class LoginUiState(
    val email: String = "",
    val password: String = "",
    val emailError: String? = null,
    val passwordError: String? = null,
    val isSubmitting: Boolean = false,
) : UiState {
    /**
     * Only gates the spinner and double taps. Field validation still runs on
     * submit so the user gets told what is wrong rather than facing a dead
     * button with no explanation.
     */
    val canSubmit: Boolean
        get() = !isSubmitting && email.isNotBlank() && password.isNotBlank()
}

sealed interface LoginEvent : UiEvent {
    data class EmailChanged(val value: String) : LoginEvent
    data class PasswordChanged(val value: String) : LoginEvent
    data object Submit : LoginEvent
    data object ForgotPasswordClicked : LoginEvent
    data object SignUpClicked : LoginEvent
}

sealed interface LoginEffect : UiEffect {
    /** Navigation is left to the session gate, which knows about consent. */
    data object SignedIn : LoginEffect
    data object NavigateToSignUp : LoginEffect
    data object NavigateToForgotPassword : LoginEffect
    data class ShowMessage(val message: String) : LoginEffect
}
