package com.ss.medrecord.data.remote

import com.google.firebase.storage.FirebaseStorage
import com.google.firebase.storage.StorageException
import com.google.firebase.storage.StorageMetadata
import com.ss.medrecord.core.common.AppConstants
import com.ss.medrecord.core.common.AppError
import com.ss.medrecord.domain.model.ReportFileType
import kotlinx.coroutines.tasks.await
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Report bytes in Cloud Storage, under the per-patient path the spec fixes in
 * section 6.2:
 *
 *   users/{userId}/patients/{patientId}/reports/{reportId}
 *
 * Path shape is the security boundary here (spec 9.1): the Storage rules match
 * on it, so no account can reach another's objects, and no patient's reports
 * can be written under a different patient's prefix.
 *
 * Bytes go up and come down in memory rather than through file handles. Reports
 * are capped at 2 MB, and the alternative - handing the SDK a file - would mean
 * decrypting a medical report onto disk first and hoping the cleanup runs.
 */
@Singleton
class ReportStorageDataSource @Inject constructor(
    private val storage: FirebaseStorage,
) {

    private fun reference(userId: String, patientId: String, reportId: String) =
        storage.reference.child(pathFor(userId, patientId, reportId))

    /** Uploads [bytes] and returns the download URL to record on the document. */
    suspend fun upload(
        userId: String,
        patientId: String,
        reportId: String,
        fileName: String,
        fileType: ReportFileType,
        bytes: ByteArray,
    ): String {
        val reference = reference(userId, patientId, reportId)
        val metadata = StorageMetadata.Builder()
            .setContentType(contentTypeFor(fileType))
            // The stored object is named after the report id, so the original
            // name rides along as metadata - enough to restore a meaningful
            // name on another device, and it is never used as a path.
            .setCustomMetadata(METADATA_FILE_NAME, fileName)
            .build()

        reference.putBytes(bytes, metadata).await()
        return reference.downloadUrl.await().toString()
    }

    /**
     * Fetches a report's bytes. The cap is passed to the SDK so a hostile or
     * corrupted object cannot be streamed into memory unbounded; it allows a
     * little headroom over the 2 MB rule so a file that was already stored
     * cannot become permanently unreadable if the cap is ever tightened.
     */
    suspend fun download(userId: String, patientId: String, reportId: String): ByteArray =
        reference(userId, patientId, reportId)
            .getBytes(AppConstants.MAX_REPORT_FILE_SIZE_BYTES * DOWNLOAD_HEADROOM)
            .await()

    suspend fun delete(userId: String, patientId: String, reportId: String) {
        reference(userId, patientId, reportId).delete().await()
    }

    companion object {
        private const val METADATA_FILE_NAME = "fileName"
        private const val DOWNLOAD_HEADROOM = 2L

        fun pathFor(userId: String, patientId: String, reportId: String): String =
            "users/$userId/patients/$patientId/reports/$reportId"

        private fun contentTypeFor(fileType: ReportFileType): String = when (fileType) {
            ReportFileType.PDF -> "application/pdf"
            // Everything stored as an image is either an original JPEG or one
            // the compressor re-encoded, so the type is known rather than
            // guessed from a file name.
            ReportFileType.IMAGE -> "image/jpeg"
        }

        fun mapStorageError(throwable: Throwable): AppError = when {
            throwable is StorageException -> when (throwable.errorCode) {
                StorageException.ERROR_NOT_AUTHORIZED,
                StorageException.ERROR_PROJECT_NOT_FOUND,
                -> AppError.PermissionDenied

                StorageException.ERROR_OBJECT_NOT_FOUND,
                StorageException.ERROR_BUCKET_NOT_FOUND,
                -> AppError.NotFound

                StorageException.ERROR_RETRY_LIMIT_EXCEEDED,
                StorageException.ERROR_CANCELED,
                -> AppError.Network(throwable)

                StorageException.ERROR_QUOTA_EXCEEDED -> AppError.Storage(throwable)
                else -> AppError.Storage(throwable)
            }

            throwable is IOException -> AppError.Network(throwable)
            else -> AppError.Unknown(throwable)
        }
    }
}
