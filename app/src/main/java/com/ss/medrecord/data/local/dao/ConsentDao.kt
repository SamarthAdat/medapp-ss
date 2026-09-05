package com.ss.medrecord.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.ss.medrecord.data.local.entity.ConsentEntity
import com.ss.medrecord.domain.model.SyncStatus
import kotlinx.coroutines.flow.Flow

@Dao
interface ConsentDao {

    /** Consent history for the settings screen, newest first (spec 5.13). */
    @Query("SELECT * FROM consents WHERE user_id = :userId ORDER BY accepted_at DESC")
    fun observeConsents(userId: String): Flow<List<ConsentEntity>>

    @Query("SELECT MAX(version) FROM consents WHERE user_id = :userId")
    suspend fun getLatestAcceptedVersion(userId: String): Int?

    /**
     * ABORT rather than REPLACE: the consent ledger is append-only, so a
     * duplicate consentId is a bug we want surfaced, not silently overwritten.
     */
    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertAll(consents: List<ConsentEntity>)

    @Query("SELECT * FROM consents WHERE sync_status IN ('PENDING', 'FAILED')")
    suspend fun getPendingConsents(): List<ConsentEntity>

    @Query("UPDATE consents SET sync_status = :status WHERE consent_id IN (:consentIds)")
    suspend fun markSyncStatus(consentIds: List<String>, status: SyncStatus)

    @Query("SELECT COUNT(*) FROM consents WHERE sync_status IN ('PENDING', 'FAILED')")
    fun observePendingCount(): Flow<Int>

    @Query("SELECT consent_id FROM consents WHERE user_id = :userId")
    suspend fun getConsentIds(userId: String): List<String>
}
