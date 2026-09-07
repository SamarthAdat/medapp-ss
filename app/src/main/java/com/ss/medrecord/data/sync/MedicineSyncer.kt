package com.ss.medrecord.data.sync

import android.util.Log
import com.ss.medrecord.data.local.dao.MedicineDao
import com.ss.medrecord.data.local.entity.toDomain
import com.ss.medrecord.data.local.entity.toEntity
import com.ss.medrecord.data.remote.MedicineRemoteDataSource
import com.ss.medrecord.domain.model.SyncStatus
import com.ss.medrecord.domain.reminder.ReminderScheduler
import com.ss.medrecord.domain.sync.EntitySyncer
import com.ss.medrecord.domain.sync.SyncCounts
import javax.inject.Inject
import javax.inject.Singleton

private const val TAG = "MedicineSyncer"

/**
 * Runs after visits, which a medicine may reference.
 *
 * The one thing this syncer does that the others do not: when a pull actually
 * changes something, it asks for the reminder schedule to be rebuilt. A
 * medicine edited on another device is a schedule change on this one, and
 * without this the new times would not be armed until the next daily sweep -
 * up to a day of doses reminded at the old times.
 */
@Singleton
class MedicineSyncer @Inject constructor(
    private val medicineDao: MedicineDao,
    private val medicineRemote: MedicineRemoteDataSource,
    private val reminderScheduler: ReminderScheduler,
) : EntitySyncer {

    override val name = "medicines"

    override val order = 60

    override suspend fun push(userId: String): SyncCounts {
        val pending = medicineDao.getPending().filter { it.userId == userId }
        if (pending.isEmpty()) return SyncCounts.NONE

        val pushed = mutableListOf<String>()
        val failed = mutableListOf<String>()

        pending.forEach { entity ->
            runCatching { medicineRemote.upsert(entity.toDomain()) }
                .onSuccess { pushed += entity.medicineId }
                .onFailure { error ->
                    Log.w(TAG, "Push failed for medicine ${entity.medicineId}", error)
                    failed += entity.medicineId
                }
        }

        if (pushed.isNotEmpty()) medicineDao.markSyncStatus(pushed, SyncStatus.SYNCED)
        if (failed.isNotEmpty()) medicineDao.markSyncStatus(failed, SyncStatus.FAILED)

        return SyncCounts(pushed = pushed.size, failed = failed.size)
    }

    override suspend fun pull(userId: String): SyncCounts {
        val remote = medicineRemote.getAll(userId)
        if (remote.isEmpty()) return SyncCounts.NONE

        var pulled = 0
        var conflicts = 0

        remote.forEach { candidate ->
            val local = medicineDao.getMedicineIncludingDeleted(candidate.medicineId)
            when {
                local == null -> {
                    medicineDao.upsert(candidate.toEntity(syncStatus = SyncStatus.SYNCED))
                    pulled++
                }

                local.syncStatus != SyncStatus.SYNCED && candidate.updatedAt > local.updatedAt -> {
                    medicineDao.markSyncStatus(listOf(local.medicineId), SyncStatus.CONFLICT)
                    conflicts++
                }

                local.syncStatus == SyncStatus.SYNCED && candidate.updatedAt > local.updatedAt -> {
                    medicineDao.upsert(candidate.toEntity(syncStatus = SyncStatus.SYNCED))
                    pulled++
                }

                else -> Unit
            }
        }

        if (pulled > 0) reminderScheduler.requestRebuild()

        return SyncCounts(pulled = pulled, conflicts = conflicts)
    }
}
