package com.ss.medrecord.domain.session

import com.ss.medrecord.core.common.AppConstants
import com.ss.medrecord.di.ApplicationScope
import com.ss.medrecord.domain.model.AuthSession
import com.ss.medrecord.domain.repository.AuthRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject
import javax.inject.Singleton

/**
 * The single source of truth for "who is signed in, and may they see data yet".
 *
 * Consent is part of the session rather than a flag checked by each screen: an
 * account with a missing or stale consent version resolves to
 * [AuthSession.PendingConsent], and the navigation graph has no path from there
 * into any data screen. That makes the section 9.5 rule - consent before any
 * processing - structural rather than something a future screen can forget.
 */
@Singleton
class SessionManager @Inject constructor(
    private val authRepository: AuthRepository,
    @param:ApplicationScope private val scope: CoroutineScope,
) {

    @OptIn(ExperimentalCoroutinesApi::class)
    val session: StateFlow<AuthSession> = authRepository.authState
        .distinctUntilChanged()
        .flatMapLatest { userId -> sessionFor(userId) }
        .distinctUntilChanged()
        .stateIn(
            scope = scope,
            started = SharingStarted.Eagerly,
            initialValue = AuthSession.Unknown,
        )

    private fun sessionFor(userId: String?): Flow<AuthSession> {
        if (userId == null) return flowOf(AuthSession.SignedOut)
        return flow {
            emit(AuthSession.Unknown)
            // Blocks the decision until a local profile exists, so a signed-in
            // user is never mistaken for un-consented just because Room has not
            // been populated yet.
            authRepository.ensureLocalUser(userId)
            emitAll(
                authRepository.observeUser(userId).map { user ->
                    when {
                        user == null -> AuthSession.PendingConsent(userId)
                        user.hasAcceptedConsent(AppConstants.CURRENT_CONSENT_VERSION) ->
                            AuthSession.Authenticated(userId)

                        else -> AuthSession.PendingConsent(userId)
                    }
                },
            )
        }
    }
}
