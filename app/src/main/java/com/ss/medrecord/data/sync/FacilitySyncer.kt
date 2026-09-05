package com.ss.medrecord.data.sync

import android.util.Log
import com.ss.medrecord.data.local.dao.FacilityDao
import com.ss.medrecord.data.local.entity.toDomain
import com.ss.medrecord.data.local.entity.toEntity
import com.ss.medrecord.data.remote.FacilityRemoteDataSource
import com.ss.medrecord.domain.model.SyncStatus
import com.ss.medrecord.domain.sync.EntitySyncer
import com.ss.medrecord.domain.sync.SyncCounts
import javax.inject.Inject
import javax.inject.Singleton

private const val TAG = "FacilitySyncer"

/**
 * Runs before visits: a visit document references a facility, so pulling
 * visits first would briefly list records whose clinic is unknown.
 */
@Singleton
class FacilitySyncer @Inject constructor(
    private val facilityDao: FacilityDao,
    private val facilityRemote: FacilityRemoteDataSource,
) : EntitySyncer {

    override val name = "facilities"

    override val order = 30

    override suspend fun push(userId: String): SyncCounts {
        val pending = facilityDao.getPending().filter { it.userId == userId }
        if (pending.isEmpty()) return SyncCounts.NONE

        val pushed = mutableListOf<String>()
        val failed = mutableListOf<String>()

        pending.forEach { entity ->
            runCatching { facilityRemote.upsert(entity.toDomain()) }
                .onSuccess { pushed += entity.facilityId }
                .onFailure { error ->
                    Log.w(TAG, "Push failed for facility ${entity.facilityId}", error)
                    failed += entity.facilityId
                }
        }

        if (pushed.isNotEmpty()) facilityDao.markSyncStatus(pushed, SyncStatus.SYNCED)
        if (failed.isNotEmpty()) facilityDao.markSyncStatus(failed, SyncStatus.FAILED)

        return SyncCounts(pushed = pushed.size, failed = failed.size)
    }

    override suspend fun pull(userId: String): SyncCounts {
        val remote = facilityRemote.getAll(userId)
        if (remote.isEmpty()) return SyncCounts.NONE

        var pulled = 0
        var conflicts = 0

        remote.forEach { candidate ->
            val local = facilityDao.getFacilityIncludingDeleted(candidate.facilityId)
            when {
                local == null -> {
                    facilityDao.upsert(candidate.toEntity(syncStatus = SyncStatus.SYNCED))
                    pulled++
                }

                local.syncStatus != SyncStatus.SYNCED && candidate.updatedAt > local.updatedAt -> {
                    facilityDao.markSyncStatus(listOf(local.facilityId), SyncStatus.CONFLICT)
                    conflicts++
                }

                local.syncStatus == SyncStatus.SYNCED && candidate.updatedAt > local.updatedAt -> {
                    facilityDao.upsert(candidate.toEntity(syncStatus = SyncStatus.SYNCED))
                    pulled++
                }

                else -> Unit
            }
        }

        return SyncCounts(pulled = pulled, conflicts = conflicts)
    }
}
