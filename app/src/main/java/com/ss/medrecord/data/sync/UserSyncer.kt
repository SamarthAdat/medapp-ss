package com.ss.medrecord.data.sync

import android.util.Log
import com.ss.medrecord.data.local.dao.UserDao
import com.ss.medrecord.data.local.entity.toDomain
import com.ss.medrecord.data.local.entity.toEntity
import com.ss.medrecord.data.remote.UserRemoteDataSource
import com.ss.medrecord.domain.model.SyncStatus
import com.ss.medrecord.domain.sync.EntitySyncer
import com.ss.medrecord.domain.sync.SyncCounts
import javax.inject.Inject
import javax.inject.Singleton

private const val TAG = "UserSyncer"

/**
 * The account holder document. Runs first: the security rules read the user
 * document to check for a consent version before allowing any patient write,
 * so a device that has not pushed its user row yet cannot push anything else.
 */
@Singleton
class UserSyncer @Inject constructor(
    private val userDao: UserDao,
    private val userRemote: UserRemoteDataSource,
) : EntitySyncer {

    override val name = "users"

    override val order = 0

    override suspend fun push(userId: String): SyncCounts {
        val pending = userDao.getPendingUsers().firstOrNull { it.userId == userId }
            ?: return SyncCounts.NONE

        return runCatching { userRemote.upsertUser(pending.toDomain()) }
            .fold(
                onSuccess = {
                    userDao.markSyncStatus(userId, SyncStatus.SYNCED)
                    SyncCounts(pushed = 1)
                },
                onFailure = { error ->
                    Log.w(TAG, "Push failed for user $userId", error)
                    userDao.markSyncStatus(userId, SyncStatus.FAILED)
                    SyncCounts(failed = 1)
                },
            )
    }

    override suspend fun pull(userId: String): SyncCounts {
        val remote = userRemote.getUser(userId) ?: return SyncCounts.NONE
        val local = userDao.getUser(userId)

        // A local row with unpushed edits is never overwritten from remote; the
        // next push resolves it. Otherwise last-write-wins on updatedAt.
        val localHasUnpushedEdits = local != null && local.syncStatus != SyncStatus.SYNCED
        if (localHasUnpushedEdits && remote.updatedAt <= local.updatedAt) return SyncCounts.NONE
        if (local != null && remote.updatedAt <= local.updatedAt) return SyncCounts.NONE

        userDao.upsert(remote.toEntity(syncStatus = SyncStatus.SYNCED))
        return SyncCounts(pulled = 1)
    }
}
