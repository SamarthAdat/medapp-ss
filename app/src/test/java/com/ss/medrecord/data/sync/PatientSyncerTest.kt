package com.ss.medrecord.data.sync

import com.ss.medrecord.data.local.dao.PatientDao
import com.ss.medrecord.data.local.entity.PatientEntity
import com.ss.medrecord.data.remote.PatientRemoteDataSource
import com.ss.medrecord.domain.model.Patient
import com.ss.medrecord.domain.model.Relationship
import com.ss.medrecord.domain.model.SyncStatus
import io.mockk.Runs
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.just
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test
import java.io.IOException

/**
 * Conflict handling is the part of sync that can silently lose a patient's
 * records, so the merge rules are pinned down here rather than trusted.
 */
class PatientSyncerTest {

    private val dao = mockk<PatientDao>(relaxed = true)
    private val remote = mockk<PatientRemoteDataSource>(relaxed = true)
    private val syncer = PatientSyncer(dao, remote)

    // --- push ---------------------------------------------------------------

    @Test
    fun `push marks successfully sent rows as synced`() = runTest {
        coEvery { dao.getPendingPatients() } returns listOf(entity("p1"), entity("p2"))
        coEvery { remote.upsert(any()) } just Runs

        val counts = syncer.push(USER_ID)

        assertEquals(2, counts.pushed)
        assertEquals(0, counts.failed)
        coVerify { dao.markSyncStatus(listOf("p1", "p2"), SyncStatus.SYNCED) }
    }

    @Test
    fun `a failed push does not block the rest of the batch`() = runTest {
        coEvery { dao.getPendingPatients() } returns listOf(entity("p1"), entity("p2"))
        coEvery { remote.upsert(match { it.patientId == "p1" }) } throws IOException("offline")
        coEvery { remote.upsert(match { it.patientId == "p2" }) } just Runs

        val counts = syncer.push(USER_ID)

        assertEquals(1, counts.pushed)
        assertEquals(1, counts.failed)
        coVerify { dao.markSyncStatus(listOf("p2"), SyncStatus.SYNCED) }
        coVerify { dao.markSyncStatus(listOf("p1"), SyncStatus.FAILED) }
    }

    @Test
    fun `push ignores rows belonging to another account`() = runTest {
        // Left behind by a previous sign-in on a shared device. Pushing them
        // under this session would be rejected, and attributing them here would
        // be wrong regardless.
        coEvery { dao.getPendingPatients() } returns listOf(entity("p1", userId = "someone-else"))

        val counts = syncer.push(USER_ID)

        assertEquals(0, counts.pushed)
        coVerify(exactly = 0) { remote.upsert(any()) }
    }

    // --- pull ---------------------------------------------------------------

    @Test
    fun `a record absent locally is inserted`() = runTest {
        coEvery { remote.getAll(USER_ID) } returns listOf(patient("p1", updatedAt = 100))
        coEvery { dao.getPatientIncludingDeleted("p1") } returns null

        val counts = syncer.pull(USER_ID)

        assertEquals(1, counts.pulled)
        coVerify { dao.upsert(match { it.patientId == "p1" && it.syncStatus == SyncStatus.SYNCED }) }
    }

    @Test
    fun `a newer remote copy overwrites a clean local one`() = runTest {
        coEvery { remote.getAll(USER_ID) } returns listOf(patient("p1", updatedAt = 200))
        coEvery { dao.getPatientIncludingDeleted("p1") } returns
            entity("p1", updatedAt = 100, syncStatus = SyncStatus.SYNCED)

        val counts = syncer.pull(USER_ID)

        assertEquals(1, counts.pulled)
        assertEquals(0, counts.conflicts)
    }

    @Test
    fun `unpushed local edits plus a newer remote copy is a conflict`() = runTest {
        // The case that matters: the same profile edited on two devices while
        // both were offline. Neither side may be discarded automatically.
        coEvery { remote.getAll(USER_ID) } returns listOf(patient("p1", updatedAt = 200))
        coEvery { dao.getPatientIncludingDeleted("p1") } returns
            entity("p1", updatedAt = 100, syncStatus = SyncStatus.PENDING)

        val counts = syncer.pull(USER_ID)

        assertEquals(1, counts.conflicts)
        assertEquals(0, counts.pulled)
        coVerify { dao.markSyncStatus(listOf("p1"), SyncStatus.CONFLICT) }
        // Crucially, the local row is not overwritten.
        coVerify(exactly = 0) { dao.upsert(any()) }
    }

    @Test
    fun `a newer local edit is left alone for the next push`() = runTest {
        coEvery { remote.getAll(USER_ID) } returns listOf(patient("p1", updatedAt = 100))
        coEvery { dao.getPatientIncludingDeleted("p1") } returns
            entity("p1", updatedAt = 200, syncStatus = SyncStatus.PENDING)

        val counts = syncer.pull(USER_ID)

        assertEquals(0, counts.pulled)
        assertEquals(0, counts.conflicts)
        coVerify(exactly = 0) { dao.upsert(any()) }
    }

    @Test
    fun `a soft-deleted local row is not resurrected by pull`() = runTest {
        // getPatientIncludingDeleted exists for exactly this: if pull compared
        // against the filtered query it would see nothing and re-insert the
        // record the user just deleted.
        coEvery { remote.getAll(USER_ID) } returns listOf(patient("p1", updatedAt = 100))
        coEvery { dao.getPatientIncludingDeleted("p1") } returns
            entity("p1", updatedAt = 300, syncStatus = SyncStatus.PENDING, deletedAt = 300)

        val counts = syncer.pull(USER_ID)

        assertEquals(0, counts.pulled)
        coVerify(exactly = 0) { dao.upsert(any()) }
    }

    @Test
    fun `equal timestamps do not churn the row`() = runTest {
        coEvery { remote.getAll(USER_ID) } returns listOf(patient("p1", updatedAt = 100))
        coEvery { dao.getPatientIncludingDeleted("p1") } returns
            entity("p1", updatedAt = 100, syncStatus = SyncStatus.SYNCED)

        val counts = syncer.pull(USER_ID)

        assertEquals(0, counts.pulled)
        assertEquals(0, counts.conflicts)
    }

    @Test
    fun `syncers run in dependency order`() {
        // Users carry the consent version the patient rules check, so they must
        // reach Firestore first; audit entries describe records and go last.
        val user = UserSyncer(mockk(relaxed = true), mockk(relaxed = true))
        val consent = ConsentSyncer(mockk(relaxed = true), mockk(relaxed = true))
        val audit = AuditSyncer(mockk(relaxed = true), mockk(relaxed = true))

        val ordered = listOf(syncer, audit, user, consent).sortedBy { it.order }.map { it.name }

        assertEquals(listOf("users", "consents", "patients", "auditLogs"), ordered)
    }

    private fun entity(
        id: String,
        userId: String = USER_ID,
        updatedAt: Long = 0L,
        syncStatus: SyncStatus = SyncStatus.PENDING,
        deletedAt: Long? = null,
    ) = PatientEntity(
        patientId = id,
        userId = userId,
        name = "Patient $id",
        relationship = Relationship.SELF,
        createdAt = 0L,
        updatedAt = updatedAt,
        deletedAt = deletedAt,
        syncStatus = syncStatus,
    )

    private fun patient(id: String, updatedAt: Long) = Patient(
        patientId = id,
        userId = USER_ID,
        name = "Patient $id",
        relationship = Relationship.SELF,
        createdAt = 0L,
        updatedAt = updatedAt,
    )

    private companion object {
        const val USER_ID = "user-1"
    }
}
