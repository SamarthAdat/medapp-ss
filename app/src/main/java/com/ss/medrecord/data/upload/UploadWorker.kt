package com.ss.medrecord.data.upload

import android.content.Context
import android.util.Log
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.ss.medrecord.core.common.DispatcherProvider
import com.ss.medrecord.core.file.EncryptedFileStore
import com.ss.medrecord.data.local.dao.ReportDao
import com.ss.medrecord.data.local.entity.ReportEntity
import com.ss.medrecord.data.local.entity.toDomain
import com.ss.medrecord.data.remote.FirebaseAuthDataSource
import com.ss.medrecord.data.remote.ReportRemoteDataSource
import com.ss.medrecord.data.remote.ReportStorageDataSource
import com.ss.medrecord.domain.model.SyncStatus
import com.ss.medrecord.domain.model.UploadStatus
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.withContext

private const val TAG = "UploadWorker"

/**
 * Sends queued report files to Cloud Storage (spec section 6.2, step 3).
 *
 * Separate from the sync worker on purpose. Metadata sync is small, frequent
 * and cheap; a file upload is up to 2 MB and can be interrupted halfway. Mixing
 * them would mean one stalled 2 MB upload holding up the audit trail, and a
 * retry policy tuned for one being wrong for the other.
 *
 * An interrupted upload resumes by construction rather than by bookkeeping: the
 * encrypted local copy is the queue, and a row only leaves PENDING once its
 * bytes are acknowledged. A process killed mid-upload therefore finds exactly
 * the same work waiting next time, and a partial object in Storage is
 * overwritten by the retry rather than left to be found.
 */
@HiltWorker
class UploadWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted workerParams: WorkerParameters,
    private val reportDao: ReportDao,
    private val reportStorage: ReportStorageDataSource,
    private val reportRemote: ReportRemoteDataSource,
    private val fileStore: EncryptedFileStore,
    private val authDataSource: FirebaseAuthDataSource,
    private val dispatchers: DispatcherProvider,
) : CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result = withContext(dispatchers.io) {
        val userId = authDataSource.currentUserId
        if (userId == null) {
            Log.d(TAG, "No session; skipping uploads")
            return@withContext Result.success()
        }

        // A previous pass may have been killed mid-upload. Those rows are
        // stranded in UPLOADING and invisible to the queue query, so they are
        // put back before the queue is read.
        val requeued = reportDao.requeueStalledUploads(userId)
        if (requeued > 0) Log.d(TAG, "Requeued $requeued interrupted upload(s)")

        val queue = reportDao.getUploadQueue(userId)
        if (queue.isEmpty()) return@withContext Result.success()

        Log.d(TAG, "Uploading ${queue.size} report(s)")
        var failures = 0
        queue.forEach { report -> if (!upload(report)) failures++ }

        when {
            failures == 0 -> Result.success()
            runAttemptCount < MAX_ATTEMPTS -> Result.retry()
            // Rows are already marked FAILED, so the reports screen offers a
            // manual retry rather than the work silently disappearing.
            else -> Result.failure()
        }
    }

    private suspend fun upload(report: ReportEntity): Boolean {
        val path = report.localFilePath ?: return true
        val bytes = fileStore.read(path)
        if (bytes == null) {
            // The encrypted copy is gone or no longer decryptable. Retrying
            // cannot help, and leaving it PENDING would spin the worker on
            // every pass forever.
            Log.w(TAG, "No readable local file for report ${report.reportId}; marking failed")
            reportDao.setUploadStatus(report.reportId, UploadStatus.FAILED)
            return false
        }

        reportDao.setUploadStatus(report.reportId, UploadStatus.UPLOADING)

        return try {
            val url = reportStorage.upload(
                userId = report.userId,
                patientId = report.patientId,
                reportId = report.reportId,
                fileName = report.fileName,
                fileType = report.fileType,
                bytes = bytes,
            )
            val now = System.currentTimeMillis()
            reportDao.markUploaded(
                reportId = report.reportId,
                remoteStorageUrl = url,
                fileSizeBytes = bytes.size.toLong(),
                updatedAt = now,
            )
            // Best effort: the row is PENDING again after markUploaded, so the
            // sync worker pushes the URL if this does not.
            runCatching {
                reportRemote.upsert(
                    report.toDomain().copy(
                        remoteStorageUrl = url,
                        uploadStatus = UploadStatus.UPLOADED,
                        fileSizeBytes = bytes.size.toLong(),
                        updatedAt = now,
                    ),
                )
            }.onSuccess {
                reportDao.markSyncStatus(listOf(report.reportId), SyncStatus.SYNCED)
            }
            true
        } catch (e: CancellationException) {
            // Work was cancelled, not rejected. Put the row back in the queue so
            // the next pass finds it rather than leaving it stuck UPLOADING.
            reportDao.setUploadStatus(report.reportId, UploadStatus.PENDING)
            throw e
        } catch (e: Exception) {
            Log.w(TAG, "Upload failed for report ${report.reportId}", e)
            reportDao.setUploadStatus(report.reportId, UploadStatus.FAILED)
            false
        }
    }

    companion object {
        const val WORK_NAME = "medrecord_report_upload"

        /** Past this the failure is not transient and backoff stops helping. */
        const val MAX_ATTEMPTS = 4
    }
}
