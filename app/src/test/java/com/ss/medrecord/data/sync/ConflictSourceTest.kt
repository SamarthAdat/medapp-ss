package com.ss.medrecord.data.sync

import com.ss.medrecord.data.local.dao.PatientDao
import com.ss.medrecord.data.local.entity.PatientEntity
import com.ss.medrecord.data.remote.PatientRemoteDataSource
import com.ss.medrecord.domain.model.Patient
import com.ss.medrecord.domain.model.Relationship
import com.ss.medrecord.domain.model.SyncStatus
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException

/**
 * Conflict resolution, which until this phase had no consumer at all: five
 * syncers parked rows in CONFLICT and nothing could ever move them again.
 *
 * The property worth pinning is the one invisible from the screen. A CONFLICT
 * row is excluded from the outbox - `getPending` selects only PENDING and
 * FAILED - so a resolution that fails to change the status leaves the record
 * exactly as stuck as it was, while telling the user it is fixed.
 */
class ConflictSourceTest {

    private val dao = mockk<PatientDao>(relaxed = true)
    private val remote = mockk<PatientRemoteDataSource>(relaxed = true)
    private val source = PatientConflictSource(dao, remote)

    @Test
    fun `a parked row is listed as a conflict`() = runTest {
        coEvery { dao.getConflicts() } returns listOf(entity())
        coEvery { remote.getAll(USER_ID) } returns emptyList()

        val conflicts = source.conflicts(USER_ID)

        assertEquals(1, conflicts.size)
        assertEquals("Asha", conflicts.single().label)
        assertEquals(PATIENT_ID, conflicts.single().entityId)
    }

    @Test
    fun `rows belonging to another account are not listed`() = runTest {
        coEvery { dao.getConflicts() } returns listOf(entity(userId = "someone-else"))

        assertTrue(source.conflicts(USER_ID).isEmpty())
    }

    @Test
    fun `no conflicts means the server is never contacted`() = runTest {
        // The common case by far. Reading a collection on every settings visit
        // to discover there is nothing to report would be a wasted round trip.
        coEvery { dao.getConflicts() } returns emptyList()

        source.conflicts(USER_ID)

        coVerify(exactly = 0) { remote.getAll(any()) }
    }

    @Test
    fun `the remote timestamp is reported when the server can be read`() = runTest {
        coEvery { dao.getConflicts() } returns listOf(entity(updatedAt = 100L))
        coEvery { remote.getAll(USER_ID) } returns listOf(domain(updatedAt = 900L))

        val conflict = source.conflicts(USER_ID).single()

        assertEquals(900L, conflict.remoteUpdatedAt)
        assertTrue(conflict.remoteIsNewer)
    }

    @Test
    fun `an unreachable server leaves the remote timestamp unknown`() = runTest {
        // Not substituted with the local time, which would put a number on
        // screen that looks like a fact about the server and is not - and the
        // user is being asked to choose between the two copies on that basis.
        coEvery { dao.getConflicts() } returns listOf(entity(updatedAt = 100L))
        coEvery { remote.getAll(USER_ID) } throws IOException("offline")

        val conflict = source.conflicts(USER_ID).single()

        assertNull(conflict.remoteUpdatedAt)
        assertTrue(conflict.isRemoteUnknown)
        assertTrue(!conflict.remoteIsNewer)
    }

    @Test
    fun `keeping the local copy returns the row to the outbox`() = runTest {
        coEvery { dao.getPatientIncludingDeleted(PATIENT_ID) } returns
            entity(syncStatus = SyncStatus.CONFLICT)
        val saved = slot<PatientEntity>()

        source.keepLocal(PATIENT_ID)

        coVerify { dao.upsert(capture(saved)) }
        assertEquals(SyncStatus.PENDING, saved.captured.syncStatus)
    }

    @Test
    fun `keeping the local copy bumps the timestamp so it does not re-conflict`() = runTest {
        // Without this the next pull sees a newer server copy against an
        // unchanged local one and parks the row again immediately - the user
        // resolves it, and it comes straight back.
        coEvery { dao.getPatientIncludingDeleted(PATIENT_ID) } returns entity(updatedAt = 1_000L)
        val saved = slot<PatientEntity>()

        source.keepLocal(PATIENT_ID)

        coVerify { dao.upsert(capture(saved)) }
        assertTrue(saved.captured.updatedAt > 1_000L)
    }

    @Test
    fun `keeping the local copy does not change the data`() = runTest {
        coEvery { dao.getPatientIncludingDeleted(PATIENT_ID) } returns
            entity(name = "Locally edited")
        val saved = slot<PatientEntity>()

        source.keepLocal(PATIENT_ID)

        coVerify { dao.upsert(capture(saved)) }
        assertEquals("Locally edited", saved.captured.name)
    }

    @Test
    fun `keeping the remote copy overwrites local and marks it synced`() = runTest {
        coEvery { remote.getAll(USER_ID) } returns listOf(domain(name = "Server version"))
        val saved = slot<PatientEntity>()

        source.keepRemote(USER_ID, PATIENT_ID)

        coVerify { dao.upsert(capture(saved)) }
        assertEquals("Server version", saved.captured.name)
        assertEquals(SyncStatus.SYNCED, saved.captured.syncStatus)
    }

    @Test
    fun `keeping the remote copy of something the server no longer has is a no-op`() = runTest {
        // Better to leave the row parked than to wipe a local medical record
        // because a read came back empty.
        coEvery { remote.getAll(USER_ID) } returns emptyList()

        source.keepRemote(USER_ID, PATIENT_ID)

        coVerify(exactly = 0) { dao.upsert(any<PatientEntity>()) }
    }

    private fun entity(
        userId: String = USER_ID,
        name: String = "Asha",
        updatedAt: Long = 0L,
        syncStatus: SyncStatus = SyncStatus.CONFLICT,
    ) = PatientEntity(
        patientId = PATIENT_ID,
        userId = userId,
        name = name,
        relationship = Relationship.SELF,
        createdAt = 0L,
        updatedAt = updatedAt,
        syncStatus = syncStatus,
    )

    private fun domain(name: String = "Asha", updatedAt: Long = 0L) = Patient(
        patientId = PATIENT_ID,
        userId = USER_ID,
        name = name,
        relationship = Relationship.SELF,
        createdAt = 0L,
        updatedAt = updatedAt,
    )

    private companion object {
        const val USER_ID = "u1"
        const val PATIENT_ID = "p1"
    }
}
