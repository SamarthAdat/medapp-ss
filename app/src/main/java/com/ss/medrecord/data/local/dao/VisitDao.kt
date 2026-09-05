package com.ss.medrecord.data.local.dao

import androidx.room.Dao
import androidx.room.Embedded
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.ss.medrecord.data.local.entity.FacilityEntity
import com.ss.medrecord.data.local.entity.VisitEntity
import com.ss.medrecord.domain.model.SyncStatus
import kotlinx.coroutines.flow.Flow

/** A visit row joined to its facility, so the list needs one query, not N+1. */
data class VisitWithFacilityRow(
    @Embedded val visit: VisitEntity,
    @Embedded(prefix = "fac_") val facility: FacilityEntity?,
)

@Dao
interface VisitDao {

    /**
     * The visit list for one patient, newest first. LEFT JOIN rather than INNER:
     * a visit whose facility row has not arrived from sync yet must still be
     * listed, or records would silently disappear mid-sync.
     */
    @Query(
        """
        SELECT v.*,
               f.facility_id AS fac_facility_id,
               f.user_id AS fac_user_id,
               f.name AS fac_name,
               f.type AS fac_type,
               f.address AS fac_address,
               f.latitude AS fac_latitude,
               f.longitude AS fac_longitude,
               f.phone AS fac_phone,
               f.notes AS fac_notes,
               f.created_at AS fac_created_at,
               f.updated_at AS fac_updated_at,
               f.deleted_at AS fac_deleted_at,
               f.sync_status AS fac_sync_status
        FROM visits v
        LEFT JOIN facilities f ON v.facility_id = f.facility_id
        WHERE v.patient_id = :patientId AND v.deleted_at IS NULL
        ORDER BY v.visit_date_epoch_day DESC, v.created_at DESC
        """,
    )
    fun observeVisitsForPatient(patientId: String): Flow<List<VisitWithFacilityRow>>

    @Query(
        """
        SELECT v.*,
               f.facility_id AS fac_facility_id,
               f.user_id AS fac_user_id,
               f.name AS fac_name,
               f.type AS fac_type,
               f.address AS fac_address,
               f.latitude AS fac_latitude,
               f.longitude AS fac_longitude,
               f.phone AS fac_phone,
               f.notes AS fac_notes,
               f.created_at AS fac_created_at,
               f.updated_at AS fac_updated_at,
               f.deleted_at AS fac_deleted_at,
               f.sync_status AS fac_sync_status
        FROM visits v
        LEFT JOIN facilities f ON v.facility_id = f.facility_id
        WHERE v.visit_id = :visitId AND v.deleted_at IS NULL
        """,
    )
    fun observeVisit(visitId: String): Flow<VisitWithFacilityRow?>

    /** Visit history at one facility (spec 5.7), across every patient. */
    @Query(
        """
        SELECT * FROM visits
        WHERE facility_id = :facilityId AND deleted_at IS NULL
        ORDER BY visit_date_epoch_day DESC
        """,
    )
    fun observeVisitsAtFacility(facilityId: String): Flow<List<VisitEntity>>

    @Query("SELECT * FROM visits WHERE visit_id = :visitId AND deleted_at IS NULL")
    suspend fun getVisit(visitId: String): VisitEntity?

    /** Includes deleted rows, for pull-merge. */
    @Query("SELECT * FROM visits WHERE visit_id = :visitId")
    suspend fun getVisitIncludingDeleted(visitId: String): VisitEntity?

    @Query("SELECT COUNT(*) FROM visits WHERE patient_id = :patientId AND deleted_at IS NULL")
    fun observeVisitCount(patientId: String): Flow<Int>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(visit: VisitEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(visits: List<VisitEntity>)

    @Query(
        """
        UPDATE visits
        SET deleted_at = :deletedAt, updated_at = :deletedAt, sync_status = :syncStatus
        WHERE visit_id = :visitId
        """,
    )
    suspend fun softDelete(
        visitId: String,
        deletedAt: Long,
        syncStatus: SyncStatus = SyncStatus.PENDING,
    )

    @Query("SELECT * FROM visits WHERE sync_status IN ('PENDING', 'FAILED')")
    suspend fun getPending(): List<VisitEntity>

    @Query("UPDATE visits SET sync_status = :status WHERE visit_id IN (:visitIds)")
    suspend fun markSyncStatus(visitIds: List<String>, status: SyncStatus)

    @Query("SELECT COUNT(*) FROM visits WHERE sync_status IN ('PENDING', 'FAILED')")
    fun observePendingCount(): Flow<Int>

    @Query("SELECT COUNT(*) FROM visits WHERE sync_status = 'CONFLICT'")
    fun observeConflictCount(): Flow<Int>
}
