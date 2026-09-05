package com.ss.medrecord.ui.navigation

import androidx.lifecycle.ViewModel
import com.ss.medrecord.domain.model.AuthSession
import com.ss.medrecord.domain.session.SessionManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.StateFlow
import javax.inject.Inject

/**
 * Exposes the app-wide session to the navigation host. Scoped to the activity,
 * so it survives every screen and there is exactly one place that decides which
 * graph the user belongs in.
 */
@HiltViewModel
class SessionViewModel @Inject constructor(
    sessionManager: SessionManager,
) : ViewModel() {

    val session: StateFlow<AuthSession> = sessionManager.session
}
