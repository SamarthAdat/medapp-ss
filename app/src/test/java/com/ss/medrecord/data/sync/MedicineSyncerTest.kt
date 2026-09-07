package com.ss.medrecord.data.sync

import com.ss.medrecord.data.local.dao.MedicineDao
import com.ss.medrecord.data.local.entity.MedicineEntity
import com.ss.medrecord.data.remote.MedicineRemoteDataSource
import com.ss.medrecord.domain.model.Medicine
import com.ss.medrecord.domain.model.MedicineFrequency
import com.ss.medrecord.domain.model.SyncStatus
import com.ss.medrecord.domain.reminder.ReminderScheduler
import io.mockk.Runs
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.just
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test
import java.io.IOException

/**
 * Medicines sync like any other record, with one obligation of their own: a
 * change pulled from another device is a change to this device's alarm
 * schedule, and the schedule has to be told.
 */
class MedicineSyncerTest {

    private val dao = mockk<MedicineDao>(relaxed = true)
    private val remote = mockk<MedicineRemoteDataSource>(relaxed = true)
    private val scheduler = mockk<ReminderScheduler>(relaxed = true)
    private val syncer = MedicineSyncer(dao, remote, scheduler)

    @Test
    fun `push marks successfully sent rows as synced`() = runTest {
        coEvery { dao.getPending() } returns listOf(entity("m1"), entity("m2"))
        coEvery { remote.upsert(any()) } just Runs

        val counts = syncer.push(USER_ID)

        assertEquals(2, counts.pushed)
        coVerify { dao.markSyncStatus(listOf("m1", "m2"), SyncStatus.SYNCED) }
    }

    @Test
    fun `a failed push does not block the rest of the batch`() = runTest {
        coEvery { dao.getPending() } returns listOf(entity("m1"), entity("m2"))
        coEvery { remote.upsert(match { it.medicineId == "m1" }) } throws IOException("offline")
        coEvery { remote.upsert(match { it.medicineId == "m2" }) } just Runs

        val counts = syncer.push(USER_ID)

        assertEquals(1, counts.pushed)
        assertEquals(1, counts.failed)
        coVerify { dao.markSyncStatus(listOf("m1"), SyncStatus.FAILED) }
    }

    @Test
    fun `push ignores rows belonging to another account`() = runTest {
        coEvery { dao.getPending() } returns listOf(entity("m1", userId = "someone-else"))

        assertEquals(0, syncer.push(USER_ID).pushed)
    }

    @Test
    fun `pull inserts a medicine this device has never seen`() = runTest {
        coEvery { remote.getAll(USER_ID) } returns listOf(domain("m1", updatedAt = 10L))
        coEvery { dao.getMedicineIncludingDeleted("m1") } returns null

        assertEquals(1, syncer.pull(USER_ID).pulled)
    }

    @Test
    fun `a newer remote copy over a locally edited row is a conflict`() = runTest {
        coEvery { remote.getAll(USER_ID) } returns listOf(domain("m1", updatedAt = 20L))
        coEvery { dao.getMedicineIncludingDeleted("m1") } returns
            entity("m1", updatedAt = 10L, syncStatus = SyncStatus.PENDING)

        val counts = syncer.pull(USER_ID)

        assertEquals(1, counts.conflicts)
        assertEquals(0, counts.pulled)
        coVerify { dao.markSyncStatus(listOf("m1"), SyncStatus.CONFLICT) }
    }

    @Test
    fun `an older remote copy is ignored`() = runTest {
        coEvery { remote.getAll(USER_ID) } returns listOf(domain("m1", updatedAt = 5L))
        coEvery { dao.getMedicineIncludingDeleted("m1") } returns
            entity("m1", updatedAt = 10L, syncStatus = SyncStatus.SYNCED)

        val counts = syncer.pull(USER_ID)

        assertEquals(0, counts.pulled)
        assertEquals(0, counts.conflicts)
    }

    @Test
    fun `pulling a change rebuilds the reminder schedule`() = runTest {
        // Without this, a medicine whose times were edited on another device
        // would keep reminding at the old times until the next daily sweep.
        coEvery { remote.getAll(USER_ID) } returns listOf(domain("m1", updatedAt = 10L))
        coEvery { dao.getMedicineIncludingDeleted("m1") } returns null

        syncer.pull(USER_ID)

        verify { scheduler.requestRebuild() }
    }

    @Test
    fun `a pull that changes nothing does not rebuild the schedule`() = runTest {
        coEvery { remote.getAll(USER_ID) } returns listOf(domain("m1", updatedAt = 5L))
        coEvery { dao.getMedicineIncludingDeleted("m1") } returns
            entity("m1", updatedAt = 10L, syncStatus = SyncStatus.SYNCED)

        syncer.pull(USER_ID)

        verify(exactly = 0) { scheduler.requestRebuild() }
    }

    private fun entity(
        id: String,
        userId: String = USER_ID,
        updatedAt: Long = 0L,
        syncStatus: SyncStatus = SyncStatus.PENDING,
    ) = MedicineEntity(
        medicineId = id,
        userId = userId,
        patientId = "p1",
        name = "Metformin",
        frequency = MedicineFrequency.DAILY,
        reminderTimes = "480",
        startDateEpochDay = 20_000L,
        createdAt = 0L,
        updatedAt = updatedAt,
        syncStatus = syncStatus,
    )

    private fun domain(id: String, updatedAt: Long) = Medicine(
        medicineId = id,
        userId = USER_ID,
        patientId = "p1",
        name = "Metformin",
        frequency = MedicineFrequency.DAILY,
        reminderTimes = listOf(480),
        startDateEpochDay = 20_000L,
        createdAt = 0L,
        updatedAt = updatedAt,
    )

    private companion object {
        const val USER_ID = "u1"
    }
}
