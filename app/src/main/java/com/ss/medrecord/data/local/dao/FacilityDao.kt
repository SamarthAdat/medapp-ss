package com.ss.medrecord.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
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

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(facility: FacilityEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
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
}
