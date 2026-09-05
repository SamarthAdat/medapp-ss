package com.ss.medrecord.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.ss.medrecord.data.local.entity.AuditLogEntity
import com.ss.medrecord.domain.model.SyncStatus
import kotlinx.coroutines.flow.Flow

/**
 * Append-only. There is intentionally no update or delete for individual
 * entries: the only writes are inserts and a sync-status stamp, so a
 * compromised client cannot rewrite history.
 */
@Dao
interface AuditLogDao {

    @Query("SELECT * FROM audit_logs WHERE user_id = :userId ORDER BY timestamp DESC LIMIT :limit")
    fun observeRecent(userId: String, limit: Int = 200): Flow<List<AuditLogEntity>>

    @Query(
        """
        SELECT * FROM audit_logs
        WHERE user_id = :userId AND patient_id = :patientId
        ORDER BY timestamp DESC LIMIT :limit
        """,
    )
    fun observeForPatient(
        userId: String,
        patientId: String,
        limit: Int = 200,
    ): Flow<List<AuditLogEntity>>

    /**
     * IGNORE rather than REPLACE: an entry that somehow arrives twice must not
     * overwrite the original, since the original is the evidence.
     */
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(entry: AuditLogEntity)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertAll(entries: List<AuditLogEntity>)

    @Query("SELECT * FROM audit_logs WHERE sync_status IN ('PENDING', 'FAILED') ORDER BY timestamp LIMIT :limit")
    suspend fun getPending(limit: Int = 300): List<AuditLogEntity>

    @Query("UPDATE audit_logs SET sync_status = :status WHERE log_id IN (:logIds)")
    suspend fun markSyncStatus(logIds: List<String>, status: SyncStatus)

    @Query("SELECT COUNT(*) FROM audit_logs WHERE sync_status IN ('PENDING', 'FAILED')")
    fun observePendingCount(): Flow<Int>

    @Query("SELECT COUNT(*) FROM audit_logs WHERE user_id = :userId")
    suspend fun countForUser(userId: String): Int

    /**
     * Sign-out clears local caches, but the trail is not a cache. Entries that
     * have not reached Firestore yet are kept so they can still be pushed;
     * everything already synced is dropped from the device.
     */
    @Query("DELETE FROM audit_logs WHERE sync_status = 'SYNCED'")
    suspend fun deleteSynced()
}
