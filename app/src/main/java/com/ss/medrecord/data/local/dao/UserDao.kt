package com.ss.medrecord.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Upsert
import com.ss.medrecord.data.local.entity.UserEntity
import com.ss.medrecord.domain.model.SyncStatus
import kotlinx.coroutines.flow.Flow

@Dao
interface UserDao {

    /** Emits null until the signed-in user has been written locally. */
    @Query("SELECT * FROM users WHERE user_id = :userId")
    fun observeUser(userId: String): Flow<UserEntity?>

    @Query("SELECT * FROM users WHERE user_id = :userId")
    suspend fun getUser(userId: String): UserEntity?

    /**
     * Room's @Upsert, not @Insert(REPLACE). REPLACE is implemented as DELETE
     * followed by INSERT, and a delete fires ON DELETE CASCADE - so re-applying
     * a parent row during sync silently destroyed every child row hanging off
     * it, including records created on this device that had never reached
     * Firestore. @Upsert updates the row in place and nothing cascades.
     */
    @Upsert
    suspend fun upsert(user: UserEntity)

    @Query(
        """
        UPDATE users
        SET consent_version = :version,
            consent_accepted_at = :acceptedAt,
            updated_at = :acceptedAt,
            sync_status = :syncStatus
        WHERE user_id = :userId
        """,
    )
    suspend fun markConsentAccepted(
        userId: String,
        version: Int,
        acceptedAt: Long,
        syncStatus: SyncStatus = SyncStatus.PENDING,
    )

    /** Sync outbox: everything still waiting to be pushed. */
    @Query("SELECT * FROM users WHERE sync_status IN ('PENDING', 'FAILED')")
    suspend fun getPendingUsers(): List<UserEntity>

    @Query("UPDATE users SET sync_status = :status WHERE user_id = :userId")
    suspend fun markSyncStatus(userId: String, status: SyncStatus)

    @Query("SELECT COUNT(*) FROM users WHERE sync_status IN ('PENDING', 'FAILED')")
    fun observePendingCount(): Flow<Int>

    /**
     * Clears every local row. Used on sign-out so that a second account on the
     * same device never sees the previous holder's cached data.
     */
    @Query("DELETE FROM users")
    suspend fun deleteAll()
}
