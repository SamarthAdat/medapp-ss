package com.ss.medrecord.data.sync

import com.ss.medrecord.data.local.dao.ConsentDao
import com.ss.medrecord.data.local.entity.ConsentEntity
import com.ss.medrecord.data.remote.UserRemoteDataSource
import com.ss.medrecord.domain.model.ConsentRecord
import com.ss.medrecord.domain.model.ConsentType
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
 * Consent documents are immutable server-side, so a pending row that already
 * exists remotely can never be pushed again - the rules reject the second
 * write. These tests cover the reconciliation that keeps such a row from
 * failing forever.
 */
class ConsentSyncerTest {

    private val dao = mockk<ConsentDao>(relaxed = true)
    private val remote = mockk<UserRemoteDataSource>(relaxed = true)
    private val syncer = ConsentSyncer(dao, remote)

    @Test
    fun `a pending row that already exists remotely is marked synced, not re-sent`() = runTest {
        // The exact state that produced a permanent PERMISSION_DENIED loop:
        // the record reached Firestore but the local row was never stamped.
        coEvery { dao.getPendingConsents() } returns listOf(entity("c1"))
        coEvery { remote.getConsents(USER_ID) } returns listOf(record("c1"))

        val counts = syncer.push(USER_ID)

        assertEquals(1, counts.pushed)
        assertEquals(0, counts.failed)
        coVerify { dao.markSyncStatus(listOf("c1"), SyncStatus.SYNCED) }
        coVerify(exactly = 0) { remote.insertConsents(any()) }
    }

    @Test
    fun `only genuinely missing records are sent`() = runTest {
        coEvery { dao.getPendingConsents() } returns listOf(entity("c1"), entity("c2"))
        coEvery { remote.getConsents(USER_ID) } returns listOf(record("c1"))
        coEvery { remote.insertConsents(any()) } just Runs

        val counts = syncer.push(USER_ID)

        assertEquals(2, counts.pushed)
        coVerify { remote.insertConsents(match { it.size == 1 && it.first().consentId == "c2" }) }
    }

    @Test
    fun `an unreadable remote defers the push rather than guessing`() = runTest {
        // Without the remote id list there is no way to tell "already there"
        // from "never sent", and guessing wrong either loses the record or
        // burns a permanent rejection.
        coEvery { dao.getPendingConsents() } returns listOf(entity("c1"))
        coEvery { remote.getConsents(USER_ID) } throws IOException("offline")

        val counts = syncer.push(USER_ID)

        assertEquals(0, counts.pushed)
        assertEquals(1, counts.failed)
        coVerify(exactly = 0) { remote.insertConsents(any()) }
        coVerify(exactly = 0) { dao.markSyncStatus(any(), SyncStatus.SYNCED) }
    }

    @Test
    fun `pull inserts only records missing locally`() = runTest {
        coEvery { remote.getConsents(USER_ID) } returns listOf(record("c1"), record("c2"))
        coEvery { dao.getConsentIds(USER_ID) } returns listOf("c1")

        val counts = syncer.pull(USER_ID)

        assertEquals(1, counts.pulled)
        coVerify { dao.insertAll(match { it.size == 1 && it.first().consentId == "c2" }) }
    }

    @Test
    fun `pull never rewrites an existing consent row`() = runTest {
        coEvery { remote.getConsents(USER_ID) } returns listOf(record("c1"))
        coEvery { dao.getConsentIds(USER_ID) } returns listOf("c1")

        val counts = syncer.pull(USER_ID)

        assertEquals(0, counts.pulled)
        coVerify(exactly = 0) { dao.insertAll(any()) }
    }

    private fun entity(id: String) = ConsentEntity(
        consentId = id,
        userId = USER_ID,
        consentType = ConsentType.DATA_PROCESSING,
        version = 1,
        acceptedAt = 1_000L,
        syncStatus = SyncStatus.PENDING,
    )

    private fun record(id: String) = ConsentRecord(
        consentId = id,
        userId = USER_ID,
        consentType = ConsentType.DATA_PROCESSING,
        version = 1,
        acceptedAt = 1_000L,
    )

    private companion object {
        const val USER_ID = "user-1"
    }
}
