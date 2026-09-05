package com.ss.medrecord.ui.feature.auth.signup

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
class SignUpViewModel @Inject constructor(
    private val authRepository: AuthRepository,
) : BaseViewModel<SignUpUiState, SignUpEvent, SignUpEffect>(SignUpUiState()) {

    override fun onEvent(event: SignUpEvent) {
        when (event) {
            is SignUpEvent.NameChanged -> setState { copy(name = event.value, nameError = null) }
            is SignUpEvent.EmailChanged -> setState { copy(email = event.value, emailError = null) }
            is SignUpEvent.PasswordChanged -> setState {
                // Re-check the confirmation against the new password so the
                // mismatch error clears as soon as the two agree again.
                copy(
                    password = event.value,
                    passwordError = null,
                    confirmPasswordError = if (confirmPassword.isEmpty()) {
                        null
                    } else {
                        AuthValidator.validatePasswordConfirmation(event.value, confirmPassword)
                            .errorOrNull
                    },
                )
            }

            is SignUpEvent.ConfirmPasswordChanged -> setState {
                copy(confirmPassword = event.value, confirmPasswordError = null)
            }

            SignUpEvent.Submit -> submit()
            SignUpEvent.SignInClicked -> sendEffect(SignUpEffect.NavigateToSignIn)
        }
    }

    private fun submit() {
        if (currentState.isSubmitting) return
        val state = currentState

        val nameResult = AuthValidator.validateName(state.name)
        val emailResult = AuthValidator.validateEmail(state.email)
        val passwordResult = AuthValidator.validatePassword(state.password)
        val confirmResult =
            AuthValidator.validatePasswordConfirmation(state.password, state.confirmPassword)

        val allValid = nameResult.isValid &&
            emailResult.isValid &&
            passwordResult.isValid &&
            confirmResult.isValid

        if (!allValid) {
            setState {
                copy(
                    nameError = nameResult.errorOrNull,
                    emailError = emailResult.errorOrNull,
                    passwordError = passwordResult.errorOrNull,
                    confirmPasswordError = confirmResult.errorOrNull,
                )
            }
            return
        }

        viewModelScope.launch {
            setState { copy(isSubmitting = true) }
            val result = authRepository.signUp(state.name, state.email, state.password)
            when (result) {
                is DataResult.Success -> {
                    // Credentials are dropped from state as soon as they are no
                    // longer needed, so they are not retained in a saved state
                    // bundle or a heap dump.
                    setState { copy(isSubmitting = false, password = "", confirmPassword = "") }
                    sendEffect(SignUpEffect.AccountCreated)
                }

                is DataResult.Error -> {
                    setState { copy(isSubmitting = false) }
                    sendEffect(SignUpEffect.ShowMessage(result.error.toUserMessage()))
                }
            }
        }
    }
}
