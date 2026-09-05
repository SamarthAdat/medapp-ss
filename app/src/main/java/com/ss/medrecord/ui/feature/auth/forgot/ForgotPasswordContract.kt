package com.ss.medrecord.ui.feature.auth.forgot

import com.ss.medrecord.core.ui.UiEffect
import com.ss.medrecord.core.ui.UiEvent
import com.ss.medrecord.core.ui.UiState

data class ForgotPasswordUiState(
    val email: String = "",
    val emailError: String? = null,
    val isSubmitting: Boolean = false,
    val isSent: Boolean = false,
) : UiState {
    val canSubmit: Boolean get() = !isSubmitting && email.isNotBlank()
}

sealed interface ForgotPasswordEvent : UiEvent {
    data class EmailChanged(val value: String) : ForgotPasswordEvent
    data object Submit : ForgotPasswordEvent
    data object BackToSignIn : ForgotPasswordEvent
}

sealed interface ForgotPasswordEffect : UiEffect {
    data object NavigateBack : ForgotPasswordEffect
    data class ShowMessage(val message: String) : ForgotPasswordEffect
}
