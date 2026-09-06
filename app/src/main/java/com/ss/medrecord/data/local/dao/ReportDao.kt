package com.ss.medrecord.data.local.dao

import androidx.room.ColumnInfo
import androidx.room.Dao
import androidx.room.Embedded
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Upsert
import com.ss.medrecord.data.local.entity.ReportEntity
import com.ss.medrecord.domain.model.SyncStatus
import com.ss.medrecord.domain.model.UploadStatus
import kotlinx.coroutines.flow.Flow

/**
 * A report joined to the things the grid has to name it by: when the visit
 * happened, where, and for whom (spec 5.8). One query rather than a lookup per
 * tile, and every join is LEFT - a report whose visit or facility has not
 * synced down yet must still be listed, not silently dropped.
 */
data class ReportWithContextRow(
    @Embedded val report: ReportEntity,
    @ColumnInfo(name = "visit_date") val visitDateEpochDay: Long?,
    @ColumnInfo(name = "facility_name") val facilityName: String?,
    @ColumnInfo(name = "patient_name") val patientName: String?,
)

@Dao
interface ReportDao {

    @Query(
        """
        SELECT r.*,
               v.visit_date_epoch_day AS visit_date,
               f.name AS facility_name,
               p.name AS patient_name
        FROM reports r
        LEFT JOIN visits v ON r.visit_id = v.visit_id
        LEFT JOIN facilities f ON v.facility_id = f.facility_id
        LEFT JOIN patients p ON r.patient_id = p.patient_id
        WHERE r.user_id = :userId AND r.deleted_at IS NULL
        ORDER BY r.created_at DESC
        """,
    )
    fun observeReportsWithContext(userId: String): Flow<List<ReportWithContextRow>>

    @Query(
        """
        SELECT * FROM reports
        WHERE visit_id = :visitId AND deleted_at IS NULL
        ORDER BY created_at DESC
        """,
    )
    fun observeReportsForVisit(visitId: String): Flow<List<ReportEntity>>

    @Query("SELECT * FROM reports WHERE report_id = :reportId AND deleted_at IS NULL")
    fun observeReport(reportId: String): Flow<ReportEntity?>

    @Query("SELECT * FROM reports WHERE report_id = :reportId AND deleted_at IS NULL")
    suspend fun getReport(reportId: String): ReportEntity?

    /** Includes deleted rows, for pull-merge. */
    @Query("SELECT * FROM reports WHERE report_id = :reportId")
    suspend fun getReportIncludingDeleted(reportId: String): ReportEntity?

    @Query("SELECT COUNT(*) FROM reports WHERE visit_id = :visitId AND deleted_at IS NULL")
    fun observeReportCountForVisit(visitId: String): Flow<Int>

    @Query("SELECT COUNT(*) FROM reports WHERE patient_id = :patientId AND deleted_at IS NULL")
    fun observeReportCountForPatient(patientId: String): Flow<Int>

    /**
     * The upload queue. FAILED is included so a retry - manual or the next
     * worker pass - picks the file back up, and rows with no local file are
     * excluded because there is nothing left on this device to send.
     */
    @Query(
        """
        SELECT * FROM reports
        WHERE user_id = :userId
          AND deleted_at IS NULL
          AND local_file_path IS NOT NULL
          AND upload_status IN ('PENDING', 'FAILED')
        ORDER BY created_at
        """,
    )
    suspend fun getUploadQueue(userId: String): List<ReportEntity>

    @Query(
        """
        SELECT COUNT(*) FROM reports
        WHERE deleted_at IS NULL AND upload_status IN ('PENDING', 'UPLOADING', 'FAILED')
        """,
    )
    fun observeUploadQueueCount(): Flow<Int>

    /**
     * Room's @Upsert, not @Insert(REPLACE). REPLACE is implemented as DELETE
     * followed by INSERT, and a delete fires ON DELETE CASCADE - so re-applying
     * a parent row during sync silently destroyed every child row hanging off
     * it, including records created on this device that had never reached
     * Firestore. @Upsert updates the row in place and nothing cascades.
     */
    @Upsert
    suspend fun upsert(report: ReportEntity)

    @Upsert
    suspend fun upsertAll(reports: List<ReportEntity>)

    /**
     * Upload state is this device's business alone, so moving through it
     * deliberately leaves `updated_at` and `sync_status` untouched. Bumping
     * them would make a purely local transition - queued, retrying, failed -
     * look like an edit to the record and win a last-write-wins comparison
     * against a genuine change made on another device.
     */
    @Query("UPDATE reports SET upload_status = :uploadStatus WHERE report_id = :reportId")
    suspend fun setUploadStatus(reportId: String, uploadStatus: UploadStatus)

    /**
     * Frees rows left mid-flight by a process that was killed while uploading.
     * Without this an interrupted upload would sit in UPLOADING forever: it is
     * not in the queue query, so nothing would ever pick it up again. Safe to
     * run at the start of a pass because upload work is unique - no second
     * worker can be legitimately mid-upload when this runs.
     */
    @Query(
        """
        UPDATE reports SET upload_status = 'PENDING'
        WHERE user_id = :userId AND upload_status = 'UPLOADING'
        """,
    )
    suspend fun requeueStalledUploads(userId: String): Int

    /**
     * Records a finished upload. The remote URL is what makes the file
     * recoverable on another device, so it is written in the same statement
     * that marks the upload done - never in two.
     */
    @Query(
        """
        UPDATE reports
        SET upload_status = :uploadStatus,
            remote_storage_url = :remoteStorageUrl,
            file_size_bytes = :fileSizeBytes,
            updated_at = :updatedAt,
            sync_status = :syncStatus
        WHERE report_id = :reportId
        """,
    )
    suspend fun markUploaded(
        reportId: String,
        remoteStorageUrl: String,
        fileSizeBytes: Long,
        updatedAt: Long,
        uploadStatus: UploadStatus = UploadStatus.UPLOADED,
        syncStatus: SyncStatus = SyncStatus.PENDING,
    )

    /** Caches the path of a file just fetched back from Cloud Storage. */
    @Query("UPDATE reports SET local_file_path = :path WHERE report_id = :reportId")
    suspend fun setLocalFilePath(reportId: String, path: String?)

    @Query(
        """
        UPDATE reports
        SET deleted_at = :deletedAt, updated_at = :deletedAt, sync_status = :syncStatus
        WHERE report_id = :reportId
        """,
    )
    suspend fun softDelete(
        reportId: String,
        deletedAt: Long,
        syncStatus: SyncStatus = SyncStatus.PENDING,
    )

    /**
     * The metadata outbox. Rows refused for size are excluded: nothing was
     * stored behind them, so pushing one would give every other device a report
     * it could never open. The refusal is a local note to the person who made
     * it, not a record worth replicating.
     */
    @Query(
        """
        SELECT * FROM reports
        WHERE sync_status IN ('PENDING', 'FAILED')
          AND upload_status != 'REJECTED_SIZE_LIMIT'
        """,
    )
    suspend fun getPending(): List<ReportEntity>

    @Query("UPDATE reports SET sync_status = :status WHERE report_id IN (:reportIds)")
    suspend fun markSyncStatus(reportIds: List<String>, status: SyncStatus)

    /** Matches [getPending], so the indicator never counts work nobody will do. */
    @Query(
        """
        SELECT COUNT(*) FROM reports
        WHERE sync_status IN ('PENDING', 'FAILED')
          AND upload_status != 'REJECTED_SIZE_LIMIT'
        """,
    )
    fun observePendingCount(): Flow<Int>

    @Query("SELECT COUNT(*) FROM reports WHERE sync_status = 'CONFLICT'")
    fun observeConflictCount(): Flow<Int>
}
