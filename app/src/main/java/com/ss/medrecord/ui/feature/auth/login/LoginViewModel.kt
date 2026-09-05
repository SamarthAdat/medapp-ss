package com.ss.medrecord.ui.feature.auth.login

import androidx.lifecycle.viewModelScope
import com.ss.medrecord.core.common.DataResult
import com.ss.medrecord.core.ui.BaseViewModel
import com.ss.medrecord.core.ui.toUserMessage
import com.ss.medrecord.domain.repository.AuthRepository
import com.ss.medrecord.domain.validation.AuthValidator
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class LoginViewModel @Inject constructor(
    private val authRepository: AuthRepository,
) : BaseViewModel<LoginUiState, LoginEvent, LoginEffect>(LoginUiState()) {

    override fun onEvent(event: LoginEvent) {
        when (event) {
            is LoginEvent.EmailChanged ->
                setState { copy(email = event.value, emailError = null) }

            is LoginEvent.PasswordChanged ->
                setState { copy(password = event.value, passwordError = null) }

            LoginEvent.Submit -> submit()
            LoginEvent.ForgotPasswordClicked -> sendEffect(LoginEffect.NavigateToForgotPassword)
            LoginEvent.SignUpClicked -> sendEffect(LoginEffect.NavigateToSignUp)
        }
    }

    private fun submit() {
        if (currentState.isSubmitting) return

        val emailResult = AuthValidator.validateEmail(currentState.email)
        // Sign-in only checks that a password was typed. Applying the signup
        // strength rules here would lock out anyone whose existing password
        // predates them.
        val passwordError = if (currentState.password.isEmpty()) "Password is required" else null

        if (!emailResult.isValid || passwordError != null) {
            setState { copy(emailError = emailResult.errorOrNull, passwordError = passwordError) }
            return
        }

        viewModelScope.launch {
            setState { copy(isSubmitting = true, emailError = null, passwordError = null) }
            when (val result = authRepository.signIn(currentState.email, currentState.password)) {
                is DataResult.Success -> {
                    setState { copy(isSubmitting = false, password = "") }
                    sendEffect(LoginEffect.SignedIn)
                }

                is DataResult.Error -> {
                    setState { copy(isSubmitting = false) }
                    sendEffect(LoginEffect.ShowMessage(result.error.toUserMessage()))
                }
            }
        }
    }
}
