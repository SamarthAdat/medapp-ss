package com.ss.medrecord.ui.feature.auth.forgot

import androidx.lifecycle.viewModelScope
import com.ss.medrecord.core.common.AppError
import com.ss.medrecord.core.common.DataResult
import com.ss.medrecord.core.ui.BaseViewModel
import com.ss.medrecord.core.ui.toUserMessage
import com.ss.medrecord.domain.repository.AuthRepository
import com.ss.medrecord.domain.validation.AuthValidator
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class ForgotPasswordViewModel @Inject constructor(
    private val authRepository: AuthRepository,
) : BaseViewModel<ForgotPasswordUiState, ForgotPasswordEvent, ForgotPasswordEffect>(
    ForgotPasswordUiState(),
) {

    override fun onEvent(event: ForgotPasswordEvent) {
        when (event) {
            is ForgotPasswordEvent.EmailChanged ->
                setState { copy(email = event.value, emailError = null, isSent = false) }

            ForgotPasswordEvent.Submit -> submit()
            ForgotPasswordEvent.BackToSignIn -> sendEffect(ForgotPasswordEffect.NavigateBack)
        }
    }

    private fun submit() {
        if (currentState.isSubmitting) return

        val emailResult = AuthValidator.validateEmail(currentState.email)
        if (!emailResult.isValid) {
            setState { copy(emailError = emailResult.errorOrNull) }
            return
        }

        viewModelScope.launch {
            setState { copy(isSubmitting = true) }
            val result = authRepository.sendPasswordReset(currentState.email)
            setState { copy(isSubmitting = false) }

            when {
                // An unknown address is reported as success on purpose. Telling
                // an anonymous caller which emails have accounts would leak that
                // a named person is a patient of this service.
                result is DataResult.Error && result.error.isAccountEnumerating() ->
                    setState { copy(isSent = true) }

                result is DataResult.Error ->
                    sendEffect(ForgotPasswordEffect.ShowMessage(result.error.toUserMessage()))

                else -> setState { copy(isSent = true) }
            }
        }
    }

    private fun AppError.isAccountEnumerating(): Boolean =
        this is AppError.Auth &&
            (
                reason == AppError.AuthReason.USER_NOT_FOUND ||
                    reason == AppError.AuthReason.INVALID_CREDENTIALS
                )
}
