package com.ss.medrecord.data.local.dao

import androidx.room.ColumnInfo
import androidx.room.Dao
import androidx.room.Embedded
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Upsert
import com.ss.medrecord.data.local.entity.FacilityEntity
import com.ss.medrecord.data.local.entity.VisitEntity
import com.ss.medrecord.domain.model.SyncStatus
import kotlinx.coroutines.flow.Flow

/** A visit row joined to its facility, so the list needs one query, not N+1. */
data class VisitWithFacilityRow(
    @Embedded val visit: VisitEntity,
    @Embedded(prefix = "fac_") val facility: FacilityEntity?,
)

/**
 * A visit with only the two names an account-wide view needs, rather than the
 * whole facility row. The dashboard and the timeline list visits across every
 * patient, where "whose visit was this" matters and the clinic's phone number
 * does not.
 */
data class VisitWithContextRow(
    @Embedded val visit: VisitEntity,
    @ColumnInfo(name = "ctx_facility_name") val facilityName: String?,
    @ColumnInfo(name = "ctx_patient_name") val patientName: String?,
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

    /**
     * Every visit on the account with the names a cross-patient view needs.
     *
     * Account-wide and unbounded, like the reports and medicines equivalents:
     * one query feeds the dashboard's recent activity, its upcoming
     * appointments and the timeline, and the alternative - a query per section
     * per patient - would be several round trips that can disagree with each
     * other mid-sync.
     */
    @Query(
        """
        SELECT v.*,
               f.name AS ctx_facility_name,
               p.name AS ctx_patient_name
        FROM visits v
        LEFT JOIN facilities f ON v.facility_id = f.facility_id
        LEFT JOIN patients p ON v.patient_id = p.patient_id
        WHERE v.user_id = :userId AND v.deleted_at IS NULL
        ORDER BY v.visit_date_epoch_day DESC, v.created_at DESC
        """,
    )
    fun observeVisitsWithContext(userId: String): Flow<List<VisitWithContextRow>>

    /**
     * Visits with a follow-up falling inside the reminder horizon (spec 4.6).
     * Bounded at both ends: a next-visit date already in the past is a missed
     * appointment, not something to remind about, and one years out does not
     * need an alarm armed today.
     */
    @Query(
        """
        SELECT * FROM visits
        WHERE user_id = :userId
          AND deleted_at IS NULL
          AND next_visit_date_epoch_day IS NOT NULL
          AND next_visit_date_epoch_day BETWEEN :fromEpochDay AND :toEpochDay
        ORDER BY next_visit_date_epoch_day ASC
        """,
    )
    suspend fun getUpcomingFollowUps(
        userId: String,
        fromEpochDay: Long,
        toEpochDay: Long,
    ): List<VisitEntity>

    /**
     * Room's @Upsert, not @Insert(REPLACE). REPLACE is implemented as DELETE
     * followed by INSERT, and a delete fires ON DELETE CASCADE - so re-applying
     * a parent row during sync silently destroyed every child row hanging off
     * it, including records created on this device that had never reached
     * Firestore. @Upsert updates the row in place and nothing cascades.
     */
    @Upsert
    suspend fun upsert(visit: VisitEntity)

    @Upsert
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

    /**
     * Rows parked by the sync engine because both sides changed. Excluded from
     * the outbox, so nothing moves them until the user resolves them.
     */
    @Query("SELECT * FROM visits WHERE sync_status = 'CONFLICT'")
    suspend fun getConflicts(): List<VisitEntity>

    /**
     * Soft-deleted rows whose grace period has expired and whose deletion has
     * already reached the server (spec section 9.6).
     *
     * The sync_status condition is the important half. Purging a row that was
     * deleted offline and never pushed would erase it here while the server
     * still holds it live, and the next pull would bring it straight back -
     * for a report, after its encrypted file had already been destroyed.
     *
     * Read rather than deleted in bulk so the caller can clean up whatever
     * hangs off the row - a cached file, a scheduled alarm - that no SQL
     * cascade will reach.
     */
    @Query(
        """
        SELECT * FROM visits
        WHERE deleted_at IS NOT NULL AND deleted_at < :cutoff AND sync_status = 'SYNCED'
        """,
    )
    suspend fun getPurgeable(cutoff: Long): List<VisitEntity>

    @Query("DELETE FROM visits WHERE visit_id = :visitId")
    suspend fun hardDelete(visitId: String)
}
