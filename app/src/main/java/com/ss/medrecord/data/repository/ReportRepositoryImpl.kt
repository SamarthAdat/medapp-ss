package com.ss.medrecord.data.repository

import android.net.Uri
import android.util.Log
import com.ss.medrecord.core.common.AppError
import com.ss.medrecord.core.common.AppConstants
import com.ss.medrecord.core.common.DataResult
import com.ss.medrecord.core.common.DispatcherProvider
import com.ss.medrecord.core.file.EncryptedFileStore
import com.ss.medrecord.core.file.PickedFileReader
import com.ss.medrecord.core.file.ReportCompressor
import com.ss.medrecord.data.local.dao.ReportDao
import com.ss.medrecord.data.local.dao.ReportWithContextRow
import com.ss.medrecord.data.local.entity.toDomain
import com.ss.medrecord.data.local.entity.toEntity
import com.ss.medrecord.data.remote.FirebaseAuthDataSource
import com.ss.medrecord.data.remote.ReportRemoteDataSource
import com.ss.medrecord.data.remote.ReportStorageDataSource
import com.ss.medrecord.data.remote.UserRemoteDataSource
import com.ss.medrecord.data.upload.UploadScheduler
import com.ss.medrecord.domain.audit.AuditLogger
import com.ss.medrecord.domain.model.AuditAction
import com.ss.medrecord.domain.model.AuditEntityType
import com.ss.medrecord.domain.model.Report
import com.ss.medrecord.domain.model.ReportFileType
import com.ss.medrecord.domain.model.ReportWithContext
import com.ss.medrecord.domain.model.SyncStatus
import com.ss.medrecord.domain.model.UploadStatus
import com.ss.medrecord.domain.model.formatFileSize
import com.ss.medrecord.domain.repository.ReportImportResult
import com.ss.medrecord.domain.repository.ReportRepository
import com.ss.medrecord.domain.validation.ReportValidator
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

private const val TAG = "ReportRepository"

@Singleton
class ReportRepositoryImpl @Inject constructor(
    private val reportDao: ReportDao,
    private val reportRemote: ReportRemoteDataSource,
    private val reportStorage: ReportStorageDataSource,
    private val pickedFileReader: PickedFileReader,
    private val compressor: ReportCompressor,
    private val fileStore: EncryptedFileStore,
    private val uploadScheduler: UploadScheduler,
    private val authDataSource: FirebaseAuthDataSource,
    private val auditLogger: AuditLogger,
    private val dispatchers: DispatcherProvider,
) : ReportRepository {

    override fun observeReportsWithContext(userId: String): Flow<List<ReportWithContext>> =
        reportDao.observeReportsWithContext(userId).map { rows -> rows.map { it.toDomain() } }

    override fun observeReportsForVisit(visitId: String): Flow<List<Report>> =
        reportDao.observeReportsForVisit(visitId).map { rows -> rows.map { it.toDomain() } }

    override fun observeReport(reportId: String): Flow<Report?> =
        reportDao.observeReport(reportId).map { it?.toDomain() }

    override fun observeReportCountForVisit(visitId: String): Flow<Int> =
        reportDao.observeReportCountForVisit(visitId)

    override fun observeReportCountForPatient(patientId: String): Flow<Int> =
        reportDao.observeReportCountForPatient(patientId)

    override suspend fun getReport(reportId: String): Report? =
        withContext(dispatchers.io) { reportDao.getReport(reportId)?.toDomain() }

    /**
     * The import pipeline, in the order the checks have to happen: identify the
     * file, read it, try to make it fit, and only then commit anything. Nothing
     * is written until the bytes are known to be storable, so a refused file
     * leaves no half-made record behind - except the deliberate one that
     * records the refusal.
     */
    override suspend fun importReport(
        uri: Uri,
        patientId: String,
        visitId: String,
    ): DataResult<ReportImportResult> = withContext(dispatchers.io) {
        val userId = authDataSource.currentUserId
            ?: return@withContext DataResult.Error(
                AppError.Auth(AppError.AuthReason.NOT_AUTHENTICATED),
            )

        DataResult.catching(::importError) {
            val picked = pickedFileReader.describe(uri)
                ?: throw IllegalStateException("Could not read the selected file")

            val fileType = picked.fileType
                ?: return@catching rejectUnsupported(picked.mimeType)

            val original = pickedFileReader.readBytes(uri)
                ?: throw IllegalStateException("Could not read the selected file")

            val fileName = ReportValidator.sanitiseFileName(picked.displayName)
            val maxBytes = AppConstants.MAX_REPORT_FILE_SIZE_BYTES
            val stored = compressor.compressToFit(original, fileType, maxBytes)

            if (stored == null) {
                return@catching recordRejection(
                    userId = userId,
                    patientId = patientId,
                    visitId = visitId,
                    fileName = fileName,
                    fileType = fileType,
                    sizeBytes = original.size.toLong(),
                )
            }

            // compressToFit hands back the very array it was given when the
            // file already fitted, so identity is the honest test for whether
            // anything was actually re-encoded.
            val wasCompressed = stored !== original
            val reportId = UUID.randomUUID().toString()
            val now = System.currentTimeMillis()
            val path = fileStore.write(reportId, stored)

            val report = Report(
                reportId = reportId,
                userId = userId,
                patientId = patientId,
                visitId = visitId,
                fileName = fileName,
                fileType = fileType,
                fileSizeBytes = stored.size.toLong(),
                localFilePath = path,
                uploadStatus = UploadStatus.PENDING,
                createdAt = now,
                updatedAt = now,
            )
            reportDao.upsert(report.toEntity(syncStatus = SyncStatus.PENDING))
            audit(AuditAction.CREATE, reportId, patientId)
            uploadScheduler.enqueue()
            pushMetadataBestEffort(report)

            ReportImportResult.Stored(reportId = reportId, wasCompressed = wasCompressed)
        }
    }

    override suspend fun ensureLocalCopy(reportId: String): DataResult<String> =
        withContext(dispatchers.io) {
            DataResult.catching(::importError) {
                val row = reportDao.getReport(reportId)
                    ?: throw IllegalStateException("Report $reportId no longer exists")

                row.localFilePath?.takeIf { fileStore.exists(it) }?.let { return@catching it }

                val remoteUrl = row.remoteStorageUrl
                    ?: throw IllegalStateException(
                        "This report has not finished uploading from the device that added it",
                    )
                Log.d(TAG, "Fetching report $reportId from storage ($remoteUrl)")

                val bytes = reportStorage.download(row.userId, row.patientId, reportId)
                val path = fileStore.write(reportId, bytes)
                reportDao.setLocalFilePath(reportId, path)
                path
            }
        }

    override suspend fun retryUpload(reportId: String): DataResult<Unit> =
        withContext(dispatchers.io) {
            DataResult.catching({ AppError.Storage(it) }) {
                reportDao.setUploadStatus(reportId, UploadStatus.PENDING)
                uploadScheduler.enqueue()
            }
        }

    /**
     * Soft delete, matching every other record (spec 9.6). The Cloud Storage
     * object stays until the server-side purge runs, because deleting it now
     * would make the 30-day grace period meaningless for the one part of a
     * report that cannot be reconstructed.
     *
     * The local encrypted copy is dropped only when the bytes are known to be
     * in Cloud Storage. For a report that never finished uploading, that copy
     * is the only one there is, and deleting it would turn a recoverable
     * deletion into a permanent one.
     */
    override suspend fun deleteReport(reportId: String): DataResult<Unit> =
        withContext(dispatchers.io) {
            DataResult.catching(UserRemoteDataSource::mapFirestoreError) {
                val row = reportDao.getReport(reportId)
                val now = System.currentTimeMillis()
                reportDao.softDelete(reportId, now)
                audit(AuditAction.DELETE, reportId, row?.patientId)

                if (row != null) {
                    if (row.uploadStatus == UploadStatus.UPLOADED) {
                        fileStore.delete(row.localFilePath)
                        reportDao.setLocalFilePath(reportId, null)
                    }
                    pushMetadataBestEffort(
                        row.toDomain().copy(deletedAt = now, updatedAt = now),
                    )
                }
                Unit
            }
        }

    override suspend fun refreshReports(userId: String): DataResult<Unit> =
        withContext(dispatchers.io) {
            DataResult.catching(UserRemoteDataSource::mapFirestoreError) {
                val remote = reportRemote.getAll(userId)
                if (remote.isEmpty()) return@catching
                val toApply = remote.mapNotNull { candidate ->
                    val local = reportDao.getReportIncludingDeleted(candidate.reportId)
                    when {
                        local == null -> candidate.toEntity(syncStatus = SyncStatus.SYNCED)
                        candidate.updatedAt >= local.updatedAt ->
                            // The local file path and upload state belong to
                            // this device and survive the merge.
                            candidate
                                .copy(
                                    localFilePath = local.localFilePath,
                                    uploadStatus = local.uploadStatus,
                                )
                                .toEntity(syncStatus = SyncStatus.SYNCED)

                        else -> null
                    }
                }
                reportDao.upsertAll(toApply)
            }
        }

    /**
     * A refusal the app decided on - wrong type, unreadable file, a report
     * whose bytes are not here yet - carries its own sentence for the user, so
     * it must not be flattened into a generic transfer failure. Anything else
     * is a real storage or network fault and maps the usual way.
     */
    private fun importError(throwable: Throwable): AppError = when (throwable) {
        is IllegalStateException ->
            AppError.Validation(throwable.message ?: "Could not attach that file")

        else -> ReportStorageDataSource.mapStorageError(throwable)
    }

    private fun rejectUnsupported(mimeType: String?): Nothing {
        val message = ReportValidator.validateFileType(mimeType).errorOrNull
            ?: "That file type cannot be attached"
        throw IllegalStateException(message)
    }

    /**
     * Keeps a row for a file that was refused, so the user sees what happened
     * to the thing they picked instead of watching it silently not appear. No
     * bytes are stored and the row never syncs - see [ReportDao.getPending].
     */
    private suspend fun recordRejection(
        userId: String,
        patientId: String,
        visitId: String,
        fileName: String,
        fileType: ReportFileType,
        sizeBytes: Long,
    ): ReportImportResult.Rejected {
        val reportId = UUID.randomUUID().toString()
        val now = System.currentTimeMillis()
        val report = Report(
            reportId = reportId,
            userId = userId,
            patientId = patientId,
            visitId = visitId,
            fileName = fileName,
            fileType = fileType,
            fileSizeBytes = sizeBytes,
            localFilePath = null,
            uploadStatus = UploadStatus.REJECTED_SIZE_LIMIT,
            createdAt = now,
            updatedAt = now,
        )
        reportDao.upsert(report.toEntity(syncStatus = SyncStatus.PENDING))

        val message = when (fileType) {
            ReportFileType.PDF ->
                "PDFs cannot be compressed without damaging them. This one is " +
                    "${formatFileSize(sizeBytes)}; the limit is " +
                    "${ReportValidator.maxSizeLabel}. Try splitting it or " +
                    "attaching the pages you need."

            ReportFileType.IMAGE ->
                "File exceeds ${ReportValidator.maxSizeLabel} limit even after " +
                    "compression (${formatFileSize(sizeBytes)}). Try a lower " +
                    "camera resolution."
        }
        return ReportImportResult.Rejected(reportId = reportId, message = message)
    }

    private suspend fun audit(action: AuditAction, reportId: String, patientId: String?) {
        auditLogger.log(
            action = action,
            entityType = AuditEntityType.REPORT,
            entityId = reportId,
            patientId = patientId,
        )
    }

    private suspend fun pushMetadataBestEffort(report: Report) {
        runCatching { reportRemote.upsert(report) }
            .onSuccess {
                reportDao.markSyncStatus(listOf(report.reportId), SyncStatus.SYNCED)
            }
            .onFailure { Log.d(TAG, "Metadata push deferred for ${report.reportId}") }
    }
}

private fun ReportWithContextRow.toDomain(): ReportWithContext = ReportWithContext(
    report = report.toDomain(),
    visitDateEpochDay = visitDateEpochDay,
    facilityName = facilityName,
    patientName = patientName,
)
