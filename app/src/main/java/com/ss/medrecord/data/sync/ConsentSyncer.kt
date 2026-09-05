package com.ss.medrecord.data.sync

import android.util.Log
import com.ss.medrecord.data.local.dao.ConsentDao
import com.ss.medrecord.data.local.entity.toDomain
import com.ss.medrecord.data.local.entity.toEntity
import com.ss.medrecord.data.remote.UserRemoteDataSource
import com.ss.medrecord.domain.model.SyncStatus
import com.ss.medrecord.domain.sync.EntitySyncer
import com.ss.medrecord.domain.sync.SyncCounts
import javax.inject.Inject
import javax.inject.Singleton

private const val TAG = "ConsentSyncer"

/**
 * Consent records are append-only evidence, so this syncer never updates or
 * deletes: it pushes what is missing remotely and inserts what is missing
 * locally. There is no conflict case, because no row is ever edited.
 *
 * The security rules enforce that append-only property by refusing any write to
 * a consent document that already exists. That makes a blind re-push of a
 * pending row fail permanently rather than transiently, so the push reconciles
 * against the remote ids first: a record that is already there is, by
 * definition of being immutable, already in sync.
 */
@Singleton
class ConsentSyncer @Inject constructor(
    private val consentDao: ConsentDao,
    private val userRemote: UserRemoteDataSource,
) : EntitySyncer {

    override val name = "consents"

    override val order = 10

    override suspend fun push(userId: String): SyncCounts {
        val pending = consentDao.getPendingConsents().filter { it.userId == userId }
        if (pending.isEmpty()) return SyncCounts.NONE

        val remoteIds = runCatching { userRemote.getConsents(userId).map { it.consentId }.toSet() }
            .getOrElse { error ->
                Log.w(TAG, "Could not read remote consents; deferring push", error)
                return SyncCounts(failed = pending.size)
            }

        // Already on the server. Nothing to send, and re-sending would be
        // rejected by the append-only rule; just correct the local bookkeeping.
        val (alreadyThere, missing) = pending.partition { it.consentId in remoteIds }
        if (alreadyThere.isNotEmpty()) {
            consentDao.markSyncStatus(alreadyThere.map { it.consentId }, SyncStatus.SYNCED)
        }
        if (missing.isEmpty()) return SyncCounts(pushed = alreadyThere.size)

        return runCatching { userRemote.insertConsents(missing.map { it.toDomain() }) }
            .fold(
                onSuccess = {
                    consentDao.markSyncStatus(missing.map { it.consentId }, SyncStatus.SYNCED)
                    SyncCounts(pushed = alreadyThere.size + missing.size)
                },
                onFailure = { error ->
                    Log.w(TAG, "Push failed for ${missing.size} consent records", error)
                    consentDao.markSyncStatus(missing.map { it.consentId }, SyncStatus.FAILED)
                    SyncCounts(pushed = alreadyThere.size, failed = missing.size)
                },
            )
    }

    override suspend fun pull(userId: String): SyncCounts {
        val remote = userRemote.getConsents(userId)
        if (remote.isEmpty()) return SyncCounts.NONE

        val known = consentDao.getConsentIds(userId).toSet()
        val missing = remote.filterNot { it.consentId in known }
        if (missing.isEmpty()) return SyncCounts.NONE

        consentDao.insertAll(missing.map { it.toEntity(syncStatus = SyncStatus.SYNCED) })
        return SyncCounts(pulled = missing.size)
    }
}
