package com.ss.medrecord.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import com.ss.medrecord.domain.model.Report
import com.ss.medrecord.domain.model.ReportFileType
import com.ss.medrecord.domain.model.SyncStatus
import com.ss.medrecord.domain.model.UploadStatus

/**
 * Local metadata for a report file (spec section 4.5). The bytes live in the
 * encrypted file store, not in this row.
 *
 * Both foreign keys cascade: a report has no meaning without the visit it
 * documents or the patient it belongs to, so a hard purge of either has to
 * reach it (spec 9.6). The cascade does not fire on the ordinary soft delete,
 * which is an update.
 *
 * Two independent status columns, because a report has two journeys and they
 * fail separately. [uploadStatus] tracks the bytes on their way to Cloud
 * Storage; [syncStatus] tracks this row on its way to Firestore. A metadata row
 * can be safely synced while its file is still queued for upload, and that is
 * the normal state of a report created offline.
 */
@Entity(
    tableName = "reports",
    foreignKeys = [
        ForeignKey(
            entity = PatientEntity::class,
            parentColumns = ["patient_id"],
            childColumns = ["patient_id"],
            onDelete = ForeignKey.CASCADE,
        ),
        ForeignKey(
            entity = VisitEntity::class,
            parentColumns = ["visit_id"],
            childColumns = ["visit_id"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [
        Index("user_id"),
        Index("visit_id"),
        // The reports grid filters by patient and deletion, newest first.
        Index(value = ["patient_id", "deleted_at", "created_at"]),
        // The upload worker's queue query.
        Index("upload_status"),
    ],
)
data class ReportEntity(
    @PrimaryKey
    @ColumnInfo(name = "report_id")
    val reportId: String,
    @ColumnInfo(name = "user_id")
    val userId: String,
    @ColumnInfo(name = "patient_id")
    val patientId: String,
    @ColumnInfo(name = "visit_id")
    val visitId: String,
    @ColumnInfo(name = "file_name")
    val fileName: String,
    @ColumnInfo(name = "file_type")
    val fileType: ReportFileType,
    @ColumnInfo(name = "file_size_bytes")
    val fileSizeBytes: Long,
    @ColumnInfo(name = "local_file_path")
    val localFilePath: String? = null,
    @ColumnInfo(name = "remote_storage_url")
    val remoteStorageUrl: String? = null,
    @ColumnInfo(name = "upload_status")
    val uploadStatus: UploadStatus = UploadStatus.PENDING,
    @ColumnInfo(name = "created_at")
    val createdAt: Long,
    @ColumnInfo(name = "updated_at")
    val updatedAt: Long,
    @ColumnInfo(name = "deleted_at")
    val deletedAt: Long? = null,
    @ColumnInfo(name = "sync_status")
    val syncStatus: SyncStatus = SyncStatus.PENDING,
)

fun ReportEntity.toDomain(): Report = Report(
    reportId = reportId,
    userId = userId,
    patientId = patientId,
    visitId = visitId,
    fileName = fileName,
    fileType = fileType,
    fileSizeBytes = fileSizeBytes,
    localFilePath = localFilePath,
    remoteStorageUrl = remoteStorageUrl,
    uploadStatus = uploadStatus,
    createdAt = createdAt,
    updatedAt = updatedAt,
    deletedAt = deletedAt,
)

fun Report.toEntity(syncStatus: SyncStatus = SyncStatus.PENDING): ReportEntity = ReportEntity(
    reportId = reportId,
    userId = userId,
    patientId = patientId,
    visitId = visitId,
    fileName = fileName,
    fileType = fileType,
    fileSizeBytes = fileSizeBytes,
    localFilePath = localFilePath,
    remoteStorageUrl = remoteStorageUrl,
    uploadStatus = uploadStatus,
    createdAt = createdAt,
    updatedAt = updatedAt,
    deletedAt = deletedAt,
    syncStatus = syncStatus,
)
