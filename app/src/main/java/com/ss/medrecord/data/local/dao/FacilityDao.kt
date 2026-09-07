package com.ss.medrecord.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Upsert
import com.ss.medrecord.data.local.entity.FacilityEntity
import com.ss.medrecord.domain.model.SyncStatus
import kotlinx.coroutines.flow.Flow

@Dao
interface FacilityDao {

    @Query(
        """
        SELECT * FROM facilities
        WHERE user_id = :userId AND deleted_at IS NULL
        ORDER BY name COLLATE NOCASE
        """,
    )
    fun observeFacilities(userId: String): Flow<List<FacilityEntity>>

    @Query("SELECT * FROM facilities WHERE facility_id = :facilityId AND deleted_at IS NULL")
    fun observeFacility(facilityId: String): Flow<FacilityEntity?>

    @Query("SELECT * FROM facilities WHERE facility_id = :facilityId AND deleted_at IS NULL")
    suspend fun getFacility(facilityId: String): FacilityEntity?

    /** Includes deleted rows, for pull-merge. */
    @Query("SELECT * FROM facilities WHERE facility_id = :facilityId")
    suspend fun getFacilityIncludingDeleted(facilityId: String): FacilityEntity?

    /**
     * Case-insensitive name match, used to avoid creating a second row for a
     * clinic the user has already saved.
     */
    @Query(
        """
        SELECT * FROM facilities
        WHERE user_id = :userId AND deleted_at IS NULL AND name = :name COLLATE NOCASE
        LIMIT 1
        """,
    )
    suspend fun findByName(userId: String, name: String): FacilityEntity?

    /**
     * Room's @Upsert, not @Insert(REPLACE). REPLACE is implemented as DELETE
     * followed by INSERT, and a delete fires ON DELETE CASCADE - so re-applying
     * a parent row during sync silently destroyed every child row hanging off
     * it, including records created on this device that had never reached
     * Firestore. @Upsert updates the row in place and nothing cascades.
     */
    @Upsert
    suspend fun upsert(facility: FacilityEntity)

    @Upsert
    suspend fun upsertAll(facilities: List<FacilityEntity>)

    @Query(
        """
        UPDATE facilities
        SET deleted_at = :deletedAt, updated_at = :deletedAt, sync_status = :syncStatus
        WHERE facility_id = :facilityId
        """,
    )
    suspend fun softDelete(
        facilityId: String,
        deletedAt: Long,
        syncStatus: SyncStatus = SyncStatus.PENDING,
    )

    @Query("SELECT * FROM facilities WHERE sync_status IN ('PENDING', 'FAILED')")
    suspend fun getPending(): List<FacilityEntity>

    @Query("UPDATE facilities SET sync_status = :status WHERE facility_id IN (:facilityIds)")
    suspend fun markSyncStatus(facilityIds: List<String>, status: SyncStatus)

    @Query("SELECT COUNT(*) FROM facilities WHERE sync_status IN ('PENDING', 'FAILED')")
    fun observePendingCount(): Flow<Int>

    @Query("SELECT COUNT(*) FROM facilities WHERE sync_status = 'CONFLICT'")
    fun observeConflictCount(): Flow<Int>

    /**
     * Rows parked by the sync engine because both sides changed. Excluded from
     * the outbox, so nothing moves them until the user resolves them.
     */
    @Query("SELECT * FROM facilities WHERE sync_status = 'CONFLICT'")
    suspend fun getConflicts(): List<FacilityEntity>

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
        SELECT * FROM facilities
        WHERE deleted_at IS NOT NULL AND deleted_at < :cutoff AND sync_status = 'SYNCED'
        """,
    )
    suspend fun getPurgeable(cutoff: Long): List<FacilityEntity>

    @Query("DELETE FROM facilities WHERE facility_id = :facilityId")
    suspend fun hardDelete(facilityId: String)
}
