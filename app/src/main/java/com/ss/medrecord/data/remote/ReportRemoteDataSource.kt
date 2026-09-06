package com.ss.medrecord.data.remote

import com.google.firebase.firestore.FirebaseFirestore
import com.ss.medrecord.domain.model.Report
import com.ss.medrecord.domain.model.ReportFileType
import com.ss.medrecord.domain.model.UploadStatus
import kotlinx.coroutines.tasks.await
import javax.inject.Inject
import javax.inject.Singleton

/**
 * users/{userId}/reports/{reportId}
 *
 * Flat under the user, matching visits: the document carries patientId and
 * visitId as fields so the rules still scope per patient, and the reports grid
 * can answer "everything for this account" in one read.
 *
 * Two fields never leave the device. `localFilePath` is meaningless anywhere
 * else and would only ever be a wrong answer on another handset. `uploadStatus`
 * is per-device too: whether *this* phone has finished sending the bytes says
 * nothing about the record itself, and syncing it would let one device's failed
 * upload mark the report failed everywhere. What matters remotely is whether
 * the file arrived, and `remoteStorageUrl` already says that.
 */
@Singleton
class ReportRemoteDataSource @Inject constructor(
    private val firestore: FirebaseFirestore,
) {

    private fun collection(userId: String) =
        firestore.collection(UserRemoteDataSource.USERS).document(userId).collection(REPORTS)

    suspend fun upsert(report: Report) {
        collection(report.userId)
            .document(report.reportId)
            .set(report.toFirestoreMap())
            .await()
    }

    suspend fun getAll(userId: String): List<Report> =
        collection(userId).get().await().documents.mapNotNull { doc ->
            val patientId = doc.getString(FIELD_PATIENT_ID) ?: return@mapNotNull null
            val visitId = doc.getString(FIELD_VISIT_ID) ?: return@mapNotNull null
            val fileType = doc.getString(FIELD_FILE_TYPE)
                ?.let { raw -> runCatching { ReportFileType.valueOf(raw) }.getOrNull() }
                ?: return@mapNotNull null
            val remoteUrl = doc.getString(FIELD_REMOTE_URL)
            Report(
                reportId = doc.id,
                userId = userId,
                patientId = patientId,
                visitId = visitId,
                fileName = doc.getString(FIELD_FILE_NAME).orEmpty(),
                fileType = fileType,
                fileSizeBytes = doc.getLong(FIELD_FILE_SIZE) ?: 0L,
                // Local state, resolved by the caller against this device.
                localFilePath = null,
                remoteStorageUrl = remoteUrl,
                // A remote row with a URL has its bytes in Cloud Storage; one
                // without is still waiting on the device that created it.
                uploadStatus = if (remoteUrl != null) {
                    UploadStatus.UPLOADED
                } else {
                    UploadStatus.PENDING
                },
                createdAt = doc.getLong(FIELD_CREATED_AT) ?: 0L,
                updatedAt = doc.getLong(FIELD_UPDATED_AT) ?: 0L,
                deletedAt = doc.getLong(FIELD_DELETED_AT),
            )
        }

    private fun Report.toFirestoreMap(): Map<String, Any?> = mapOf(
        FIELD_REPORT_ID to reportId,
        FIELD_USER_ID to userId,
        FIELD_PATIENT_ID to patientId,
        FIELD_VISIT_ID to visitId,
        FIELD_FILE_NAME to fileName,
        FIELD_FILE_TYPE to fileType.name,
        FIELD_FILE_SIZE to fileSizeBytes,
        FIELD_REMOTE_URL to remoteStorageUrl,
        FIELD_CREATED_AT to createdAt,
        FIELD_UPDATED_AT to updatedAt,
        FIELD_DELETED_AT to deletedAt,
    )

    companion object {
        const val REPORTS = "reports"

        private const val FIELD_REPORT_ID = "reportId"
        private const val FIELD_USER_ID = "userId"
        private const val FIELD_PATIENT_ID = "patientId"
        private const val FIELD_VISIT_ID = "visitId"
        private const val FIELD_FILE_NAME = "fileName"
        private const val FIELD_FILE_TYPE = "fileType"
        private const val FIELD_FILE_SIZE = "fileSizeBytes"
        private const val FIELD_REMOTE_URL = "remoteStorageUrl"
        private const val FIELD_CREATED_AT = "createdAt"
        private const val FIELD_UPDATED_AT = "updatedAt"
        private const val FIELD_DELETED_AT = "deletedAt"
    }
}
