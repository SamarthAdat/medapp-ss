package com.ss.medrecord.domain.session

import com.ss.medrecord.core.common.AppConstants
import com.ss.medrecord.core.common.DataResult
import com.ss.medrecord.domain.model.AppUser
import com.ss.medrecord.domain.model.AuthSession
import com.ss.medrecord.domain.repository.AuthRepository
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * The consent gate is a compliance control (spec section 9.5), so the mapping
 * from stored state to [AuthSession] is pinned down here rather than left to be
 * discovered by hand on a device.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class SessionManagerTest {

    @Test
    fun `no firebase user resolves to signed out`() = runTest {
        val manager = sessionManager(uid = null)

        assertEquals(AuthSession.SignedOut, manager.awaitResolved())
    }

    @Test
    fun `current consent version resolves to authenticated`() = runTest {
        val manager = sessionManager(
            uid = USER_ID,
            user = user(consentVersion = AppConstants.CURRENT_CONSENT_VERSION),
        )

        assertEquals(AuthSession.Authenticated(USER_ID), manager.awaitResolved())
    }

    @Test
    fun `never accepted consent resolves to pending consent`() = runTest {
        val manager = sessionManager(uid = USER_ID, user = user(consentVersion = null))

        assertEquals(AuthSession.PendingConsent(USER_ID), manager.awaitResolved())
    }

    @Test
    fun `superseded consent version re-prompts`() = runTest {
        // The whole point of versioning: an old acceptance must not carry over
        // when the consent text changes.
        val manager = sessionManager(
            uid = USER_ID,
            user = user(consentVersion = AppConstants.CURRENT_CONSENT_VERSION - 1),
        )

        assertEquals(AuthSession.PendingConsent(USER_ID), manager.awaitResolved())
    }

    @Test
    fun `signed in with no local profile fails closed to pending consent`() = runTest {
        val manager = sessionManager(uid = USER_ID, user = null)

        assertEquals(AuthSession.PendingConsent(USER_ID), manager.awaitResolved())
    }

    private suspend fun SessionManager.awaitResolved(): AuthSession =
        session.first { it != AuthSession.Unknown }

    private fun TestScope.sessionManager(
        uid: String?,
        user: AppUser? = null,
    ): SessionManager {
        val repository = mockk<AuthRepository>()
        every { repository.authState } returns flowOf(uid)
        every { repository.observeUser(any()) } returns flowOf(user)
        coEvery { repository.ensureLocalUser(any()) } returns
            (user?.let { DataResult.Success(it) } ?: DataResult.Success(user(null)))

        return SessionManager(
            authRepository = repository,
            scope = CoroutineScope(UnconfinedTestDispatcher(testScheduler)),
        )
    }

    private fun user(consentVersion: Int?): AppUser = AppUser(
        userId = USER_ID,
        name = "Test User",
        email = "test@example.com",
        createdAt = 0L,
        updatedAt = 0L,
        consentAcceptedAt = consentVersion?.let { 1_000L },
        consentVersion = consentVersion,
    )

    private companion object {
        const val USER_ID = "user-1"
    }
}
