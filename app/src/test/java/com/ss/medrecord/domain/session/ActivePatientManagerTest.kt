package com.ss.medrecord.domain.session

import com.ss.medrecord.core.common.DataResult
import com.ss.medrecord.data.local.datastore.ActivePatientStore
import com.ss.medrecord.domain.model.AuthSession
import com.ss.medrecord.domain.model.Patient
import com.ss.medrecord.domain.model.Relationship
import com.ss.medrecord.domain.repository.PatientRepository
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * The active patient is what every later phase filters on, so the rules for
 * resolving a stored selection are pinned down here.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ActivePatientManagerTest {

    @Test
    fun `stored selection is used when it is still available`() = runTest {
        val manager = manager(
            storedId = "p2",
            available = listOf(patient("p1"), patient("p2")),
        )

        assertEquals("p2", manager.activePatient.first { it != null }?.patientId)
    }

    @Test
    fun `a selection that is no longer available falls back to the first profile`() = runTest {
        // Archived, deleted, or removed on another device: whatever the reason,
        // a stale id must not leave the app with no patient in context.
        val manager = manager(
            storedId = "deleted-on-another-device",
            available = listOf(patient("p1"), patient("p2")),
        )

        assertEquals("p1", manager.activePatient.first { it != null }?.patientId)
    }

    @Test
    fun `no stored selection falls back to the first profile`() = runTest {
        val manager = manager(storedId = null, available = listOf(patient("p1")))

        assertEquals("p1", manager.activePatient.first { it != null }?.patientId)
    }

    @Test
    fun `no profiles means no active patient`() = runTest {
        val manager = manager(storedId = "p1", available = emptyList())

        assertNull(manager.activePatient.first())
    }

    @Test
    fun `signed out sessions expose no patients`() = runTest {
        val manager = manager(
            storedId = "p1",
            available = listOf(patient("p1")),
            session = AuthSession.SignedOut,
        )

        assertEquals(emptyList<Patient>(), manager.patients.first())
        assertNull(manager.activePatient.first())
    }

    @Test
    fun `a session pending consent exposes no patients`() = runTest {
        // Patient data must not be readable before consent is on file (9.5).
        val manager = manager(
            storedId = "p1",
            available = listOf(patient("p1")),
            session = AuthSession.PendingConsent(USER_ID),
        )

        assertEquals(emptyList<Patient>(), manager.patients.first())
    }

    private fun TestScope.manager(
        storedId: String?,
        available: List<Patient>,
        session: AuthSession = AuthSession.Authenticated(USER_ID),
    ): ActivePatientManager {
        val sessionManager = mockk<SessionManager>()
        every { sessionManager.session } returns MutableStateFlow(session)

        val patientRepository = mockk<PatientRepository>()
        every { patientRepository.observeActivePatients(any()) } returns flowOf(available)
        coEvery { patientRepository.refreshPatients(any()) } returns DataResult.Success(Unit)

        val store = mockk<ActivePatientStore>()
        every { store.observeActivePatientId(any()) } returns flowOf(storedId)

        return ActivePatientManager(
            sessionManager = sessionManager,
            patientRepository = patientRepository,
            activePatientStore = store,
            scope = CoroutineScope(UnconfinedTestDispatcher(testScheduler)),
        )
    }

    private fun patient(id: String) = Patient(
        patientId = id,
        userId = USER_ID,
        name = "Patient $id",
        relationship = Relationship.SELF,
        createdAt = 0L,
        updatedAt = 0L,
    )

    private companion object {
        const val USER_ID = "user-1"
    }
}
