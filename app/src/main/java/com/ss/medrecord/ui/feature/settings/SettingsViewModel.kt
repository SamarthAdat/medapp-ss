package com.ss.medrecord.ui.feature.settings

import androidx.lifecycle.viewModelScope
import com.ss.medrecord.core.ui.BaseViewModel
import com.ss.medrecord.domain.repository.AuthRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val authRepository: AuthRepository,
) : BaseViewModel<SettingsUiState, SettingsEvent, SettingsEffect>(SettingsUiState()) {

    init {
        authRepository.observeCurrentUser()
            .onEach { user ->
                setState {
                    copy(
                        name = user?.name.orEmpty(),
                        email = user?.email.orEmpty(),
                        consentVersion = user?.consentVersion,
                    )
                }
            }
            .launchIn(viewModelScope)
    }

    override fun onEvent(event: SettingsEvent) {
        when (event) {
            SettingsEvent.BackClicked -> sendEffect(SettingsEffect.NavigateBack)
            SettingsEvent.SignOutClicked -> signOut()
        }
    }

    private fun signOut() {
        if (currentState.isSigningOut) return
        viewModelScope.launch {
            setState { copy(isSigningOut = true) }
            // No navigation effect: signing out changes the session, and the
            // navigation host relocates the user to the auth graph from there.
            authRepository.signOut()
        }
    }
}
