package com.ss.medrecord.ui.feature.consent

import androidx.lifecycle.viewModelScope
import com.ss.medrecord.core.common.AppConstants
import com.ss.medrecord.core.common.DataResult
import com.ss.medrecord.core.ui.BaseViewModel
import com.ss.medrecord.core.ui.toUserMessage
import com.ss.medrecord.domain.repository.AuthRepository
import com.ss.medrecord.domain.repository.ConsentRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class ConsentViewModel @Inject constructor(
    private val authRepository: AuthRepository,
    private val consentRepository: ConsentRepository,
) : BaseViewModel<ConsentUiState, ConsentEvent, ConsentEffect>(
    ConsentUiState(version = AppConstants.CURRENT_CONSENT_VERSION),
) {

    init {
        viewModelScope.launch {
            val userId = authRepository.currentUserId ?: return@launch
            // A previously accepted older version means the text has changed,
            // which section 9.5 requires us to surface as a re-consent rather
            // than a first-time prompt.
            val previous = consentRepository.latestAcceptedVersion(userId)
            setState { copy(isReconsent = previous != null) }
        }
    }

    override fun onEvent(event: ConsentEvent) {
        when (event) {
            is ConsentEvent.ToggleConsent -> setState {
                copy(accepted = accepted + (event.type to event.accepted))
            }

            ConsentEvent.Submit -> submit()
            ConsentEvent.Decline -> decline()
        }
    }

    private fun submit() {
        if (!currentState.canSubmit) return
        val userId = authRepository.currentUserId
        if (userId == null) {
            sendEffect(ConsentEffect.ShowMessage("Your session expired. Please sign in again."))
            return
        }

        viewModelScope.launch {
            setState { copy(isSubmitting = true) }
            val result = consentRepository.acceptCurrentConsent(
                userId = userId,
                version = AppConstants.CURRENT_CONSENT_VERSION,
            )
            setState { copy(isSubmitting = false) }

            when (result) {
                // The session gate observes the user row and moves the app on by
                // itself; the effect only exists so the screen can react.
                is DataResult.Success -> sendEffect(ConsentEffect.ConsentGranted)
                is DataResult.Error ->
                    sendEffect(ConsentEffect.ShowMessage(result.error.toUserMessage()))
            }
        }
    }

    private fun decline() {
        viewModelScope.launch {
            authRepository.signOut()
            sendEffect(ConsentEffect.Declined)
        }
    }
}
