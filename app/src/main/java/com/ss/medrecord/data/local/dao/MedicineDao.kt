package com.ss.medrecord.data.local.dao

import androidx.room.ColumnInfo
import androidx.room.Dao
import androidx.room.Embedded
import androidx.room.Query
import androidx.room.Upsert
import com.ss.medrecord.data.local.entity.MedicineEntity
import com.ss.medrecord.domain.model.SyncStatus
import kotlinx.coroutines.flow.Flow

/**
 * A medicine row with the names its list needs. Nullable throughout: a medicine
 * need not belong to a visit, and a visit's facility may not have synced down
 * yet - neither is a reason to hide the medicine.
 */
data class MedicineWithContextRow(
    @Embedded val medicine: MedicineEntity,
    @ColumnInfo(name = "ctx_patient_name") val patientName: String?,
    @ColumnInfo(name = "ctx_facility_name") val facilityName: String?,
    @ColumnInfo(name = "ctx_visit_date") val visitDateEpochDay: Long?,
)

@Dao
interface MedicineDao {

    /**
     * Every medicine on the account, newest course first, with the patient and
     * (where there is one) the visit it came from.
     */
    @Query(
        """
        SELECT m.*,
               p.name AS ctx_patient_name,
               f.name AS ctx_facility_name,
               v.visit_date_epoch_day AS ctx_visit_date
        FROM medicines m
        LEFT JOIN patients p ON m.patient_id = p.patient_id
        LEFT JOIN visits v ON m.visit_id = v.visit_id
        LEFT JOIN facilities f ON v.facility_id = f.facility_id
        WHERE m.user_id = :userId AND m.deleted_at IS NULL
        ORDER BY m.is_active DESC, m.name COLLATE NOCASE ASC
        """,
    )
    fun observeMedicinesWithContext(userId: String): Flow<List<MedicineWithContextRow>>

    @Query(
        """
        SELECT * FROM medicines
        WHERE patient_id = :patientId AND deleted_at IS NULL
        ORDER BY is_active DESC, name COLLATE NOCASE ASC
        """,
    )
    fun observeMedicinesForPatient(patientId: String): Flow<List<MedicineEntity>>

    @Query(
        """
        SELECT * FROM medicines
        WHERE visit_id = :visitId AND deleted_at IS NULL
        ORDER BY name COLLATE NOCASE ASC
        """,
    )
    fun observeMedicinesForVisit(visitId: String): Flow<List<MedicineEntity>>

    @Query("SELECT * FROM medicines WHERE medicine_id = :medicineId AND deleted_at IS NULL")
    fun observeMedicine(medicineId: String): Flow<MedicineEntity?>

    @Query("SELECT * FROM medicines WHERE medicine_id = :medicineId AND deleted_at IS NULL")
    suspend fun getMedicine(medicineId: String): MedicineEntity?

    /** Includes deleted rows, for pull-merge. */
    @Query("SELECT * FROM medicines WHERE medicine_id = :medicineId")
    suspend fun getMedicineIncludingDeleted(medicineId: String): MedicineEntity?

    /**
     * Everything the reminder generator needs: active, undeleted courses that
     * have not already ended. The end-date filter is in SQL rather than in
     * Kotlin so a long-finished course costs nothing on every sweep.
     */
    @Query(
        """
        SELECT * FROM medicines
        WHERE user_id = :userId
          AND deleted_at IS NULL
          AND is_active = 1
          AND (end_date_epoch_day IS NULL OR end_date_epoch_day >= :onOrAfterEpochDay)
        """,
    )
    suspend fun getSchedulable(userId: String, onOrAfterEpochDay: Long): List<MedicineEntity>

    @Query(
        """
        SELECT COUNT(*) FROM medicines
        WHERE patient_id = :patientId AND deleted_at IS NULL AND is_active = 1
        """,
    )
    fun observeActiveCountForPatient(patientId: String): Flow<Int>

    @Query("SELECT COUNT(*) FROM medicines WHERE visit_id = :visitId AND deleted_at IS NULL")
    fun observeCountForVisit(visitId: String): Flow<Int>

    /**
     * Room's @Upsert, not @Insert(REPLACE). REPLACE is implemented as DELETE
     * followed by INSERT, and a delete fires ON DELETE CASCADE - so re-applying
     * a parent row during sync silently destroyed every child row hanging off
     * it, including records created on this device that had never reached
     * Firestore. @Upsert updates the row in place and nothing cascades.
     */
    @Upsert
    suspend fun upsert(medicine: MedicineEntity)

    @Upsert
    suspend fun upsertAll(medicines: List<MedicineEntity>)

    @Query(
        """
        UPDATE medicines
        SET is_active = :isActive, updated_at = :updatedAt, sync_status = :syncStatus
        WHERE medicine_id = :medicineId
        """,
    )
    suspend fun setActive(
        medicineId: String,
        isActive: Boolean,
        updatedAt: Long,
        syncStatus: SyncStatus = SyncStatus.PENDING,
    )

    @Query(
        """
        UPDATE medicines
        SET deleted_at = :deletedAt, updated_at = :deletedAt, sync_status = :syncStatus
        WHERE medicine_id = :medicineId
        """,
    )
    suspend fun softDelete(
        medicineId: String,
        deletedAt: Long,
        syncStatus: SyncStatus = SyncStatus.PENDING,
    )

    @Query("SELECT * FROM medicines WHERE sync_status IN ('PENDING', 'FAILED')")
    suspend fun getPending(): List<MedicineEntity>

    @Query("UPDATE medicines SET sync_status = :status WHERE medicine_id IN (:medicineIds)")
    suspend fun markSyncStatus(medicineIds: List<String>, status: SyncStatus)

    @Query("SELECT COUNT(*) FROM medicines WHERE sync_status IN ('PENDING', 'FAILED')")
    fun observePendingCount(): Flow<Int>

    @Query("SELECT COUNT(*) FROM medicines WHERE sync_status = 'CONFLICT'")
    fun observeConflictCount(): Flow<Int>
}
