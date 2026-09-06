package com.ss.medrecord.data.upload

import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Enqueues the report upload worker.
 *
 * Separate from the sync worker because the two have different costs and
 * therefore different constraints. Metadata is a few hundred bytes and can go
 * over anything; report files are up to 2 MB each, so uploads also wait for the
 * battery not to be low. They are not restricted to unmetered networks: someone
 * photographing a prescription at a clinic wants it saved before they leave,
 * and 2 MB is a price worth paying for that.
 */
@Singleton
class UploadScheduler @Inject constructor(
    private val workManager: WorkManager,
) {

    fun enqueue() {
        val request = OneTimeWorkRequestBuilder<UploadWorker>()
            .setConstraints(
                Constraints.Builder()
                    .setRequiredNetworkType(NetworkType.CONNECTED)
                    .setRequiresBatteryNotLow(true)
                    .build(),
            )
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, BACKOFF_SECONDS, TimeUnit.SECONDS)
            .build()

        workManager.enqueueUniqueWork(
            UploadWorker.WORK_NAME,
            // APPEND_OR_REPLACE, not KEEP: a report added while an upload pass
            // is already running must not be dropped on the floor, and the
            // worker drains the whole queue each time it runs anyway.
            ExistingWorkPolicy.APPEND_OR_REPLACE,
            request,
        )
    }

    private companion object {
        const val BACKOFF_SECONDS = 30L
    }
}
