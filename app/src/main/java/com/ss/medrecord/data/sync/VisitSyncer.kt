package com.ss.medrecord.data.sync

import android.util.Log
import com.ss.medrecord.data.local.dao.VisitDao
import com.ss.medrecord.data.local.entity.toDomain
import com.ss.medrecord.data.local.entity.toEntity
import com.ss.medrecord.data.remote.VisitRemoteDataSource
import com.ss.medrecord.domain.model.SyncStatus
import com.ss.medrecord.domain.sync.EntitySyncer
import com.ss.medrecord.domain.sync.SyncCounts
import javax.inject.Inject
import javax.inject.Singleton

private const val TAG = "VisitSyncer"

/**
 * Runs after patients and facilities, both of which a visit references.
 * Pushing a visit whose patient has not reached Firestore yet would be
 * rejected by the rules.
 */
@Singleton
class VisitSyncer @Inject constructor(
    private val visitDao: VisitDao,
    private val visitRemote: VisitRemoteDataSource,
) : EntitySyncer {

    override val name = "visits"

    override val order = 40

    override suspend fun push(userId: String): SyncCounts {
        val pending = visitDao.getPending().filter { it.userId == userId }
        if (pending.isEmpty()) return SyncCounts.NONE

        val pushed = mutableListOf<String>()
        val failed = mutableListOf<String>()

        pending.forEach { entity ->
            runCatching { visitRemote.upsert(entity.toDomain()) }
                .onSuccess { pushed += entity.visitId }
                .onFailure { error ->
                    Log.w(TAG, "Push failed for visit ${entity.visitId}", error)
                    failed += entity.visitId
                }
        }

        if (pushed.isNotEmpty()) visitDao.markSyncStatus(pushed, SyncStatus.SYNCED)
        if (failed.isNotEmpty()) visitDao.markSyncStatus(failed, SyncStatus.FAILED)

        return SyncCounts(pushed = pushed.size, failed = failed.size)
    }

    override suspend fun pull(userId: String): SyncCounts {
        val remote = visitRemote.getAll(userId)
        if (remote.isEmpty()) return SyncCounts.NONE

        var pulled = 0
        var conflicts = 0

        remote.forEach { candidate ->
            val local = visitDao.getVisitIncludingDeleted(candidate.visitId)
            when {
                local == null -> {
                    visitDao.upsert(candidate.toEntity(syncStatus = SyncStatus.SYNCED))
                    pulled++
                }

                local.syncStatus != SyncStatus.SYNCED && candidate.updatedAt > local.updatedAt -> {
                    visitDao.markSyncStatus(listOf(local.visitId), SyncStatus.CONFLICT)
                    conflicts++
                }

                local.syncStatus == SyncStatus.SYNCED && candidate.updatedAt > local.updatedAt -> {
                    visitDao.upsert(candidate.toEntity(syncStatus = SyncStatus.SYNCED))
                    pulled++
                }

                else -> Unit
            }
        }

        return SyncCounts(pulled = pulled, conflicts = conflicts)
    }
}
