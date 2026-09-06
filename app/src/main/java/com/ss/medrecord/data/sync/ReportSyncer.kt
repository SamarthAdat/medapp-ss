package com.ss.medrecord.data.sync

import android.util.Log
import com.ss.medrecord.data.local.dao.ReportDao
import com.ss.medrecord.data.local.entity.toDomain
import com.ss.medrecord.data.local.entity.toEntity
import com.ss.medrecord.data.remote.ReportRemoteDataSource
import com.ss.medrecord.domain.model.SyncStatus
import com.ss.medrecord.domain.sync.EntitySyncer
import com.ss.medrecord.domain.sync.SyncCounts
import javax.inject.Inject
import javax.inject.Singleton

private const val TAG = "ReportSyncer"

/**
 * Report *metadata*. The files themselves are the upload worker's job, and the
 * two are deliberately independent: a report row reaching Firestore does not
 * wait on 2 MB of bytes, and a failed upload does not hold up the record of
 * what was attached.
 *
 * Runs after visits, which every report references. A report pushed before its
 * visit would be an orphan on any other device that pulled it.
 */
@Singleton
class ReportSyncer @Inject constructor(
    private val reportDao: ReportDao,
    private val reportRemote: ReportRemoteDataSource,
) : EntitySyncer {

    override val name = "reports"

    override val order = 50

    override suspend fun push(userId: String): SyncCounts {
        val pending = reportDao.getPending().filter { it.userId == userId }
        if (pending.isEmpty()) return SyncCounts.NONE

        val pushed = mutableListOf<String>()
        val failed = mutableListOf<String>()

        pending.forEach { entity ->
            runCatching { reportRemote.upsert(entity.toDomain()) }
                .onSuccess { pushed += entity.reportId }
                .onFailure { error ->
                    Log.w(TAG, "Push failed for report ${entity.reportId}", error)
                    failed += entity.reportId
                }
        }

        if (pushed.isNotEmpty()) reportDao.markSyncStatus(pushed, SyncStatus.SYNCED)
        if (failed.isNotEmpty()) reportDao.markSyncStatus(failed, SyncStatus.FAILED)

        return SyncCounts(pushed = pushed.size, failed = failed.size)
    }

    override suspend fun pull(userId: String): SyncCounts {
        val remote = reportRemote.getAll(userId)
        if (remote.isEmpty()) return SyncCounts.NONE

        var pulled = 0
        var conflicts = 0

        remote.forEach { candidate ->
            val local = reportDao.getReportIncludingDeleted(candidate.reportId)
            when {
                local == null -> {
                    // Arrives with no local file: the bytes are fetched on
                    // first open rather than eagerly, so pulling a year of
                    // another device's reports does not cost a download each.
                    reportDao.upsert(candidate.toEntity(syncStatus = SyncStatus.SYNCED))
                    pulled++
                }

                local.syncStatus != SyncStatus.SYNCED && candidate.updatedAt > local.updatedAt -> {
                    reportDao.markSyncStatus(listOf(local.reportId), SyncStatus.CONFLICT)
                    conflicts++
                }

                local.syncStatus == SyncStatus.SYNCED && candidate.updatedAt > local.updatedAt -> {
                    // The cached file and this device's upload state are not
                    // the server's to overwrite.
                    reportDao.upsert(
                        candidate
                            .copy(
                                localFilePath = local.localFilePath,
                                uploadStatus = local.uploadStatus,
                            )
                            .toEntity(syncStatus = SyncStatus.SYNCED),
                    )
                    pulled++
                }

                else -> Unit
            }
        }

        return SyncCounts(pulled = pulled, conflicts = conflicts)
    }
}
