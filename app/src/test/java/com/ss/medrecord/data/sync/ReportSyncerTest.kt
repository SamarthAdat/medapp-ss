package com.ss.medrecord.data.sync

import com.ss.medrecord.data.local.dao.ReportDao
import com.ss.medrecord.data.local.entity.ReportEntity
import com.ss.medrecord.domain.model.Report
import com.ss.medrecord.domain.model.ReportFileType
import com.ss.medrecord.domain.model.SyncStatus
import com.ss.medrecord.domain.model.UploadStatus
import io.mockk.Runs
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.just
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test
import java.io.IOException

/**
 * Report metadata syncs like any other record, with one rule of its own: the
 * local file and this device's upload state are not the server's to overwrite.
 * Getting that wrong would drop a cached file's path and silently re-download
 * it, or mark a report failed here because another device's upload failed.
 */
class ReportSyncerTest {

    private val dao = mockk<ReportDao>(relaxed = true)
    private val remote = mockk<com.ss.medrecord.data.remote.ReportRemoteDataSource>(relaxed = true)
    private val syncer = ReportSyncer(dao, remote)

    @Test
    fun `push marks successfully sent rows as synced`() = runTest {
        coEvery { dao.getPending() } returns listOf(entity("r1"), entity("r2"))
        coEvery { remote.upsert(any()) } just Runs

        val counts = syncer.push(USER_ID)

        assertEquals(2, counts.pushed)
        coVerify { dao.markSyncStatus(listOf("r1", "r2"), SyncStatus.SYNCED) }
    }

    @Test
    fun `a failed push does not block the rest of the batch`() = runTest {
        coEvery { dao.getPending() } returns listOf(entity("r1"), entity("r2"))
        coEvery { remote.upsert(match { it.reportId == "r1" }) } throws IOException("offline")
        coEvery { remote.upsert(match { it.reportId == "r2" }) } just Runs

        val counts = syncer.push(USER_ID)

        assertEquals(1, counts.pushed)
        assertEquals(1, counts.failed)
        coVerify { dao.markSyncStatus(listOf("r1"), SyncStatus.FAILED) }
    }

    @Test
    fun `push ignores rows belonging to another account`() = runTest {
        coEvery { dao.getPending() } returns listOf(entity("r1", userId = "someone-else"))

        assertEquals(0, syncer.push(USER_ID).pushed)
        coVerify(exactly = 0) { remote.upsert(any()) }
    }

    @Test
    fun `a report only on the server arrives without a local file`() = runTest {
        coEvery { remote.getAll(USER_ID) } returns listOf(
            domain("r1", remoteUrl = "https://storage/r1"),
        )
        coEvery { dao.getReportIncludingDeleted("r1") } returns null
        val stored = slot<ReportEntity>()
        coEvery { dao.upsert(capture(stored)) } just Runs

        val counts = syncer.pull(USER_ID)

        assertEquals(1, counts.pulled)
        // Bytes are fetched on first open, not eagerly at sync time.
        assertEquals(null, stored.captured.localFilePath)
        assertEquals(SyncStatus.SYNCED, stored.captured.syncStatus)
    }

    @Test
    fun `a newer remote copy keeps this device's cached file and upload state`() = runTest {
        coEvery { remote.getAll(USER_ID) } returns listOf(
            domain("r1", fileName = "renamed.pdf", updatedAt = 200L),
        )
        coEvery { dao.getReportIncludingDeleted("r1") } returns entity(
            reportId = "r1",
            syncStatus = SyncStatus.SYNCED,
            updatedAt = 100L,
            localFilePath = "/data/r1.enc",
            uploadStatus = UploadStatus.UPLOADED,
        )
        val stored = slot<ReportEntity>()
        coEvery { dao.upsert(capture(stored)) } just Runs

        assertEquals(1, syncer.pull(USER_ID).pulled)
        assertEquals("renamed.pdf", stored.captured.fileName)
        assertEquals("/data/r1.enc", stored.captured.localFilePath)
        assertEquals(UploadStatus.UPLOADED, stored.captured.uploadStatus)
    }

    @Test
    fun `an unpushed local edit the server has moved past becomes a conflict`() = runTest {
        coEvery { remote.getAll(USER_ID) } returns listOf(domain("r1", updatedAt = 200L))
        coEvery { dao.getReportIncludingDeleted("r1") } returns entity(
            reportId = "r1",
            syncStatus = SyncStatus.PENDING,
            updatedAt = 100L,
        )

        val counts = syncer.pull(USER_ID)

        assertEquals(1, counts.conflicts)
        assertEquals(0, counts.pulled)
        // Flagged, never overwritten: the local edit is still the only copy.
        coVerify(exactly = 0) { dao.upsert(any()) }
        coVerify { dao.markSyncStatus(listOf("r1"), SyncStatus.CONFLICT) }
    }

    @Test
    fun `a remote copy no newer than the local one is left alone`() = runTest {
        coEvery { remote.getAll(USER_ID) } returns listOf(domain("r1", updatedAt = 100L))
        coEvery { dao.getReportIncludingDeleted("r1") } returns entity(
            reportId = "r1",
            syncStatus = SyncStatus.SYNCED,
            updatedAt = 100L,
        )

        val counts = syncer.pull(USER_ID)

        assertEquals(0, counts.pulled)
        coVerify(exactly = 0) { dao.upsert(any()) }
    }

    private fun entity(
        reportId: String,
        userId: String = USER_ID,
        syncStatus: SyncStatus = SyncStatus.PENDING,
        updatedAt: Long = 0L,
        localFilePath: String? = null,
        uploadStatus: UploadStatus = UploadStatus.PENDING,
    ) = ReportEntity(
        reportId = reportId,
        userId = userId,
        patientId = "p1",
        visitId = "v1",
        fileName = "scan.jpg",
        fileType = ReportFileType.IMAGE,
        fileSizeBytes = 1024,
        localFilePath = localFilePath,
        uploadStatus = uploadStatus,
        createdAt = 0L,
        updatedAt = updatedAt,
        syncStatus = syncStatus,
    )

    private fun domain(
        reportId: String,
        fileName: String = "scan.jpg",
        remoteUrl: String? = null,
        updatedAt: Long = 0L,
    ) = Report(
        reportId = reportId,
        userId = USER_ID,
        patientId = "p1",
        visitId = "v1",
        fileName = fileName,
        fileType = ReportFileType.IMAGE,
        fileSizeBytes = 1024,
        remoteStorageUrl = remoteUrl,
        uploadStatus = UploadStatus.UPLOADED,
        createdAt = 0L,
        updatedAt = updatedAt,
    )

    private companion object {
        const val USER_ID = "user-1"
    }
}
