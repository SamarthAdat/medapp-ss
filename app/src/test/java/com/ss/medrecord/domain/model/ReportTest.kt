package com.ss.medrecord.domain.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ReportTest {

    @Test
    fun `a report is viewable when its bytes are reachable either way`() {
        // On this device.
        assertTrue(report(localPath = "/data/r1.enc").isViewable)
        // Only in the cloud - fetched on open.
        assertTrue(report(remoteUrl = "https://storage/r1").isViewable)
        // Created offline on another device: the row synced, the file did not.
        assertFalse(report().isViewable)
    }

    @Test
    fun `a rejected report is never viewable`() {
        // Nothing was stored, so there is nothing to open even if a stale path
        // or URL were somehow present.
        val rejected = report(
            localPath = "/data/r1.enc",
            status = UploadStatus.REJECTED_SIZE_LIMIT,
        )

        assertTrue(rejected.isRejected)
        assertFalse(rejected.isViewable)
    }

    @Test
    fun `only a failed upload with local bytes can be retried`() {
        assertTrue(report(localPath = "/data/r1.enc", status = UploadStatus.FAILED).canRetryUpload)

        // Nothing left on this device to send.
        assertFalse(report(status = UploadStatus.FAILED).canRetryUpload)
        // Retrying a refusal would only be refused again.
        assertFalse(
            report(localPath = "/data/r1.enc", status = UploadStatus.REJECTED_SIZE_LIMIT)
                .canRetryUpload,
        )
        assertFalse(report(localPath = "/data/r1.enc", status = UploadStatus.UPLOADED).canRetryUpload)
    }

    @Test
    fun `pending and failed are both work the uploader still owes`() {
        assertTrue(report(status = UploadStatus.PENDING).needsUpload)
        assertTrue(report(status = UploadStatus.FAILED).needsUpload)
        assertFalse(report(status = UploadStatus.UPLOADED).needsUpload)
        assertFalse(report(status = UploadStatus.REJECTED_SIZE_LIMIT).needsUpload)
    }

    @Test
    fun `deletedAt drives isDeleted`() {
        assertFalse(report().isDeleted)
        assertTrue(report().copy(deletedAt = 1L).isDeleted)
    }

    @Test
    fun `mime types map to the two renderable kinds`() {
        assertEquals(ReportFileType.PDF, ReportFileType.fromMimeType("application/pdf"))
        assertEquals(ReportFileType.IMAGE, ReportFileType.fromMimeType("image/png"))
        assertNull(ReportFileType.fromMimeType("text/plain"))
        assertNull(ReportFileType.fromMimeType(null))
    }

    @Test
    fun `file sizes read the way the size limit is written`() {
        assertEquals("512 B", formatFileSize(512))
        assertEquals("1 KB", formatFileSize(1024))
        assertEquals("2.0 MB", formatFileSize(2L * 1024 * 1024))
        assertEquals("1.5 MB", formatFileSize(1024 * 1024 + 512 * 1024))
    }

    @Test
    fun `context falls back to a readable label when the visit has not synced`() {
        val orphan = ReportWithContext(report = report())

        assertEquals("Unknown facility", orphan.facilityLabel)
    }

    private fun report(
        localPath: String? = null,
        remoteUrl: String? = null,
        status: UploadStatus = UploadStatus.PENDING,
    ) = Report(
        reportId = "r1",
        userId = "u1",
        patientId = "p1",
        visitId = "v1",
        fileName = "scan.jpg",
        fileType = ReportFileType.IMAGE,
        fileSizeBytes = 1024,
        localFilePath = localPath,
        remoteStorageUrl = remoteUrl,
        uploadStatus = status,
        createdAt = 0L,
        updatedAt = 0L,
    )
}
