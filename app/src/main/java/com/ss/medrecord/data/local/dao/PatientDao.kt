package com.ss.medrecord.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.ss.medrecord.data.local.entity.PatientEntity
import com.ss.medrecord.domain.model.SyncStatus
import kotlinx.coroutines.flow.Flow

@Dao
interface PatientDao {

    /**
     * The switcher list: active profiles only. Self sorts first because it is
     * the one most people open, then alphabetically.
     */
    @Query(
        """
        SELECT * FROM patients
        WHERE user_id = :userId AND deleted_at IS NULL AND is_archived = 0
        ORDER BY CASE WHEN relationship = 'SELF' THEN 0 ELSE 1 END, name COLLATE NOCASE
        """,
    )
    fun observeActivePatients(userId: String): Flow<List<PatientEntity>>

    @Query(
        """
        SELECT * FROM patients
        WHERE user_id = :userId AND deleted_at IS NULL AND is_archived = 1
        ORDER BY name COLLATE NOCASE
        """,
    )
    fun observeArchivedPatients(userId: String): Flow<List<PatientEntity>>

    /**
     * Deleted rows are excluded: a patient in the grace period must not be
     * reachable, even though the row is still on disk for retention.
     */
    @Query("SELECT * FROM patients WHERE patient_id = :patientId AND deleted_at IS NULL")
    fun observePatient(patientId: String): Flow<PatientEntity?>

    @Query("SELECT * FROM patients WHERE patient_id = :patientId AND deleted_at IS NULL")
    suspend fun getPatient(patientId: String): PatientEntity?

    @Query(
        """
        SELECT COUNT(*) FROM patients
        WHERE user_id = :userId AND deleted_at IS NULL AND is_archived = 0
        """,
    )
    suspend fun countActivePatients(userId: String): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(patient: PatientEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(patients: List<PatientEntity>)

    @Query(
        """
        UPDATE patients
        SET is_archived = :archived, updated_at = :updatedAt, sync_status = :syncStatus
        WHERE patient_id = :patientId
        """,
    )
    suspend fun setArchived(
        patientId: String,
        archived: Boolean,
        updatedAt: Long,
        syncStatus: SyncStatus = SyncStatus.PENDING,
    )

    /**
     * Soft delete (spec section 9.6). The row is kept so the deletion itself can
     * be synced and so the hard-delete sweep has something to act on once the
     * grace period expires.
     */
    @Query(
        """
        UPDATE patients
        SET deleted_at = :deletedAt, updated_at = :deletedAt, sync_status = :syncStatus
        WHERE patient_id = :patientId
        """,
    )
    suspend fun softDelete(
        patientId: String,
        deletedAt: Long,
        syncStatus: SyncStatus = SyncStatus.PENDING,
    )

    /** Rows whose grace period has expired and that have been pushed already. */
    @Query(
        """
        SELECT * FROM patients
        WHERE deleted_at IS NOT NULL AND deleted_at < :cutoff AND sync_status = 'SYNCED'
        """,
    )
    suspend fun getPurgeablePatients(cutoff: Long): List<PatientEntity>

    @Query("DELETE FROM patients WHERE patient_id = :patientId")
    suspend fun hardDelete(patientId: String)

    @Query("SELECT * FROM patients WHERE sync_status IN ('PENDING', 'FAILED')")
    suspend fun getPendingPatients(): List<PatientEntity>

    @Query("DELETE FROM patients")
    suspend fun deleteAll()
}
