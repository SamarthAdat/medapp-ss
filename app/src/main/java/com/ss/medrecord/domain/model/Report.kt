package com.ss.medrecord.domain.model

import java.util.Locale

/**
 * A report file attached to a visit (spec section 4.5) - a scan, a lab result,
 * a discharge summary.
 *
 * The record and the bytes travel separately. This row is small, syncs through
 * the ordinary Firestore outbox, and reaches every device; the file itself is
 * an encrypted blob in app-private storage that is uploaded to Cloud Storage by
 * [com.ss.medrecord.data.upload.UploadWorker] and downloaded on demand. So a
 * report can exist on a device with no local copy of its file - the row arrived
 * by sync, the bytes have not been fetched yet - which is why [localFilePath]
 * and [remoteStorageUrl] are both nullable and mean different things.
 */
data class Report(
    val reportId: String,
    val userId: String,
    val patientId: String,
    val visitId: String,
    val fileName: String,
    val fileType: ReportFileType,
    val fileSizeBytes: Long,
    /**
     * Absolute path of the encrypted copy on *this* device, or null when the
     * bytes have not been fetched here. Deliberately never synced: another
     * device's path means nothing here and would only ever be wrong.
     */
    val localFilePath: String? = null,
    /** Set once the upload succeeds; until then the bytes exist only locally. */
    val remoteStorageUrl: String? = null,
    val uploadStatus: UploadStatus = UploadStatus.PENDING,
    val createdAt: Long,
    val updatedAt: Long,
    val deletedAt: Long? = null,
) {
    val isDeleted: Boolean get() = deletedAt != null

    /** The file can be opened without a network round trip. */
    val isAvailableOffline: Boolean get() = localFilePath != null

    /** Nothing was ever stored: the file was over the cap and never accepted. */
    val isRejected: Boolean get() = uploadStatus == UploadStatus.REJECTED_SIZE_LIMIT

    /** Waiting on the upload worker, or waiting for the user to retry. */
    val needsUpload: Boolean
        get() = uploadStatus == UploadStatus.PENDING || uploadStatus == UploadStatus.FAILED

    /**
     * A failed upload is retryable; a rejected one is not, because the bytes
     * were never kept. The user has to pick a smaller file instead.
     */
    val canRetryUpload: Boolean
        get() = uploadStatus == UploadStatus.FAILED && localFilePath != null

    /** Openable when the bytes are here, or can still be pulled down. */
    val isViewable: Boolean
        get() = !isRejected && (localFilePath != null || remoteStorageUrl != null)
}

/**
 * What kind of file a report holds. Only two, because these are the two the app
 * can render itself: anything it cannot display is not worth storing in a
 * medical record it promises to show back to the user.
 */
enum class ReportFileType(val label: String) {
    PDF("PDF"),
    IMAGE("Image"),
    ;

    companion object {
        /** Null for a type the app cannot render, which the picker then refuses. */
        fun fromMimeType(mimeType: String?): ReportFileType? = when {
            mimeType == null -> null
            mimeType == "application/pdf" -> PDF
            mimeType.startsWith("image/") -> IMAGE
            else -> null
        }
    }
}

/** Where a report's bytes have got to on their way to Cloud Storage (spec 4.5). */
enum class UploadStatus {
    /** Stored and encrypted locally, queued for upload. */
    PENDING,

    /** An upload attempt is in flight. */
    UPLOADING,

    /** Bytes are in Cloud Storage; the local copy is now a cache. */
    UPLOADED,

    /** An attempt failed. The local copy is intact and the user can retry. */
    FAILED,

    /**
     * Over the 2 MB cap even after compression, so the bytes were never stored
     * (spec 5.8). The row is kept so the user can see what was refused and why
     * rather than watching a file silently vanish.
     */
    REJECTED_SIZE_LIMIT,
}

/**
 * A report together with the clinical context that identifies it: the visit it
 * documents, where that visit happened, and which patient it belongs to.
 *
 * Every field but the report itself is nullable because the joins that supply
 * them are outer joins. A report can arrive by sync before its visit does, and
 * showing it with a missing label beats hiding a medical record until the rest
 * of the graph catches up.
 */
data class ReportWithContext(
    val report: Report,
    val visitDateEpochDay: Long? = null,
    val facilityName: String? = null,
    val patientName: String? = null,
) {
    val facilityLabel: String get() = facilityName ?: "Unknown facility"
}

/** Human-readable size, for a UI that has to explain a 2 MB rule. */
fun formatFileSize(bytes: Long): String = when {
    bytes < 1024 -> "$bytes B"
    bytes < 1024 * 1024 -> "${bytes / 1024} KB"
    // Explicit locale: this is read by a person, and a device that writes
    // decimals with a comma should see "1,4 MB".
    else -> String.format(Locale.getDefault(), "%.1f MB", bytes / (1024.0 * 1024.0))
}
