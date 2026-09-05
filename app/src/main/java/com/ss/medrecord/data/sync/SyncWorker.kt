package com.ss.medrecord.data.sync

import android.content.Context
import android.util.Log
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.ss.medrecord.core.common.DispatcherProvider
import com.ss.medrecord.data.remote.FirebaseAuthDataSource
import com.ss.medrecord.domain.sync.EntitySyncer
import com.ss.medrecord.domain.sync.SyncCounts
import com.ss.medrecord.domain.sync.SyncReport
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.withContext

private const val TAG = "SyncWorker"

/**
 * Drains the outbox and merges remote changes (spec section 6.2).
 *
 * Push runs before pull for every entity type. Doing it the other way round
 * would let a remote copy overwrite a local edit that had not been sent yet,
 * which is the one outcome an offline-first app must never produce.
 *
 * A syncer that throws does not abort the pass. Each type is independent, and a
 * failure in one - a rules rejection on patients, say - should not stop the
 * audit trail from reaching the server.
 */
@HiltWorker
class SyncWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted workerParams: WorkerParameters,
    private val syncers: Set<@JvmSuppressWildcards EntitySyncer>,
    private val authDataSource: FirebaseAuthDataSource,
    private val syncStateHolder: SyncStateHolder,
    private val dispatchers: DispatcherProvider,
) : CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result = withContext(dispatchers.io) {
        val userId = authDataSource.currentUserId
        if (userId == null) {
            // Signed out between scheduling and running. Nothing to sync, and
            // retrying would not change that.
            Log.d(TAG, "No session; skipping sync")
            return@withContext Result.success()
        }

        if (!syncStateHolder.tryBeginSync()) {
            // Another trigger is mid-pass. Its run covers this one's work.
            Log.d(TAG, "Sync already in progress; skipping")
            return@withContext Result.success()
        }

        var report = SyncReport()
        try {
            report = runSync(userId)
        } finally {
            // Must release even if the worker is cancelled, or the guard latches
            // on and no sync ever runs again in this process.
            syncStateHolder.onSyncFinished(report)
        }

        Log.i(TAG, "Sync finished: ${report.counts} across ${report.perEntity.keys}")

        when {
            // Retried with the backoff policy set at enqueue time.
            report.hasFailures && runAttemptCount < MAX_ATTEMPTS -> Result.retry()
            report.hasFailures -> Result.failure()
            else -> Result.success()
        }
    }

    private suspend fun runSync(userId: String): SyncReport {
        var total = SyncCounts.NONE
        val perEntity = mutableMapOf<String, SyncCounts>()

        syncers.sortedBy { it.order }.forEach { syncer ->
            val counts = runOne(syncer, userId)
            perEntity[syncer.name] = counts
            total += counts
        }

        return SyncReport(counts = total, perEntity = perEntity)
    }

    private suspend fun runOne(syncer: EntitySyncer, userId: String): SyncCounts {
        val pushed = try {
            syncer.push(userId)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w(TAG, "Push stage failed for ${syncer.name}", e)
            SyncCounts(failed = 1)
        }

        val pulled = try {
            syncer.pull(userId)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w(TAG, "Pull stage failed for ${syncer.name}", e)
            SyncCounts(failed = 1)
        }

        return pushed + pulled
    }

    companion object {
        const val WORK_NAME_PERIODIC = "medrecord_sync_periodic"
        const val WORK_NAME_ONE_SHOT = "medrecord_sync_now"

        /** Beyond this the failure is not transient and backoff stops helping. */
        const val MAX_ATTEMPTS = 4
    }
}
