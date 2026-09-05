package com.ss.medrecord.data.sync

import android.util.Log
import com.ss.medrecord.data.local.dao.PatientDao
import com.ss.medrecord.data.local.entity.toDomain
import com.ss.medrecord.data.local.entity.toEntity
import com.ss.medrecord.data.remote.PatientRemoteDataSource
import com.ss.medrecord.domain.model.SyncStatus
import com.ss.medrecord.domain.sync.EntitySyncer
import com.ss.medrecord.domain.sync.SyncCounts
import javax.inject.Inject
import javax.inject.Singleton

private const val TAG = "PatientSyncer"

/**
 * Two-way sync for patient profiles.
 *
 * Conflicts are real here in a way they are not for append-only data: the same
 * profile can be edited on two devices while both are offline. The rule is
 * last-write-wins on updatedAt (spec 6.5), with one exception - a local row
 * that still has unpushed edits and is *older* than the remote copy is a true
 * conflict, and is flagged rather than silently overwritten.
 */
@Singleton
class PatientSyncer @Inject constructor(
    private val patientDao: PatientDao,
    private val patientRemote: PatientRemoteDataSource,
) : EntitySyncer {

    override val name = "patients"

    // After users: a patient document is rejected by the rules unless the
    // owning user document exists with a consent version on it.
    override val order = 20

    override suspend fun push(userId: String): SyncCounts {
        val pending = patientDao.getPendingPatients().filter { it.userId == userId }
        if (pending.isEmpty()) return SyncCounts.NONE

        val pushed = mutableListOf<String>()
        val failed = mutableListOf<String>()

        pending.forEach { entity ->
            runCatching { patientRemote.upsert(entity.toDomain()) }
                .onSuccess { pushed += entity.patientId }
                .onFailure { error ->
                    Log.w(TAG, "Push failed for patient ${entity.patientId}", error)
                    failed += entity.patientId
                }
        }

        if (pushed.isNotEmpty()) patientDao.markSyncStatus(pushed, SyncStatus.SYNCED)
        // FAILED rather than left PENDING, so a run of failures is visible in
        // the UI instead of looking like work that has not been attempted.
        if (failed.isNotEmpty()) patientDao.markSyncStatus(failed, SyncStatus.FAILED)

        return SyncCounts(pushed = pushed.size, failed = failed.size)
    }

    override suspend fun pull(userId: String): SyncCounts {
        val remote = patientRemote.getAll(userId)
        if (remote.isEmpty()) return SyncCounts.NONE

        var pulled = 0
        var conflicts = 0

        remote.forEach { candidate ->
            val local = patientDao.getPatientIncludingDeleted(candidate.patientId)

            when {
                local == null -> {
                    patientDao.upsert(candidate.toEntity(syncStatus = SyncStatus.SYNCED))
                    pulled++
                }

                // Local edits not yet pushed, and the server moved on after we
                // last had it. Neither side can be discarded automatically.
                local.syncStatus != SyncStatus.SYNCED &&
                    candidate.updatedAt > local.updatedAt -> {
                    patientDao.markSyncStatus(listOf(local.patientId), SyncStatus.CONFLICT)
                    conflicts++
                }

                // Clean local copy that the server has moved past: take theirs.
                local.syncStatus == SyncStatus.SYNCED && candidate.updatedAt > local.updatedAt -> {
                    patientDao.upsert(candidate.toEntity(syncStatus = SyncStatus.SYNCED))
                    pulled++
                }

                // Local is same or newer: leave it for the next push.
                else -> Unit
            }
        }

        return SyncCounts(pulled = pulled, conflicts = conflicts)
    }
}
