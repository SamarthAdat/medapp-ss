package com.ss.medrecord.data.sync

import android.util.Log
import com.ss.medrecord.data.local.dao.AuditLogDao
import com.ss.medrecord.data.local.entity.toDomain
import com.ss.medrecord.data.remote.AuditRemoteDataSource
import com.ss.medrecord.domain.model.SyncStatus
import com.ss.medrecord.domain.sync.EntitySyncer
import com.ss.medrecord.domain.sync.SyncCounts
import javax.inject.Inject
import javax.inject.Singleton

private const val TAG = "AuditSyncer"

/**
 * Push-only. The trail flows one way: entries are written locally, shipped to
 * Firestore, and never read back. Pulling them would mean caching another
 * device's activity log on this device for no purpose.
 *
 * Runs last, so an entry describing a record is pushed after the record itself.
 */
@Singleton
class AuditSyncer @Inject constructor(
    private val auditLogDao: AuditLogDao,
    private val auditRemote: AuditRemoteDataSource,
) : EntitySyncer {

    override val name = "auditLogs"

    override val order = 90

    override suspend fun push(userId: String): SyncCounts {
        val pending = auditLogDao.getPending(limit = AuditRemoteDataSource.BATCH_LIMIT)
            .filter { it.userId == userId }
        if (pending.isEmpty()) return SyncCounts.NONE

        return runCatching { auditRemote.insertAll(pending.map { it.toDomain() }) }
            .fold(
                onSuccess = {
                    auditLogDao.markSyncStatus(pending.map { it.logId }, SyncStatus.SYNCED)
                    SyncCounts(pushed = pending.size)
                },
                onFailure = { error ->
                    Log.w(TAG, "Push failed for ${pending.size} audit entries", error)
                    auditLogDao.markSyncStatus(pending.map { it.logId }, SyncStatus.FAILED)
                    SyncCounts(failed = pending.size)
                },
            )
    }

    override suspend fun pull(userId: String): SyncCounts = SyncCounts.NONE
}
