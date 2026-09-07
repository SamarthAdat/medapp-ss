package com.ss.medrecord.data.reminder

import androidx.work.BackoffPolicy
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.ss.medrecord.domain.reminder.ReminderScheduler
import com.ss.medrecord.domain.repository.ReminderRepository
import java.time.Duration
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

/**
 * The [ReminderScheduler] the rest of the app talks to.
 *
 * Deliberately thin: it enqueues [ReminderWorker] and nothing else. Keeping the
 * real work behind WorkManager is what lets a repository ask for a rebuild
 * mid-transaction without blocking on four table scans, and what makes the
 * rebuild survive the process being killed a moment later.
 */
@Singleton
class WorkReminderScheduler @Inject constructor(
    private val workManager: WorkManager,
    private val reminderRepository: ReminderRepository,
    private val alarmScheduler: AlarmScheduler,
) : ReminderScheduler {

    override fun requestRebuild() {
        val request = OneTimeWorkRequestBuilder<ReminderWorker>()
            .setBackoffCriteria(BackoffPolicy.LINEAR, BACKOFF_SECONDS, TimeUnit.SECONDS)
            .build()

        workManager.enqueueUniqueWork(
            ReminderWorker.WORK_NAME_ONE_SHOT,
            // REPLACE, not KEEP: a rebuild already queued was computed against
            // the schedule before this change, so finishing it would arm the
            // times the user just edited away.
            ExistingWorkPolicy.REPLACE,
            request,
        )
    }

    override fun initialize() {
        val request = PeriodicWorkRequestBuilder<ReminderWorker>(SWEEP_INTERVAL)
            .setBackoffCriteria(BackoffPolicy.LINEAR, BACKOFF_SECONDS, TimeUnit.SECONDS)
            .build()

        workManager.enqueueUniquePeriodicWork(
            ReminderWorker.WORK_NAME_PERIODIC,
            // KEEP so an app restart does not reset the interval; on a device
            // opened often that would starve the sweep entirely.
            ExistingPeriodicWorkPolicy.KEEP,
            request,
        )
    }

    override suspend fun cancelAll() {
        alarmScheduler.cancel()
        workManager.cancelUniqueWork(ReminderWorker.WORK_NAME_ONE_SHOT)
        reminderRepository.clear()
    }

    private companion object {
        const val BACKOFF_SECONDS = 30L

        /**
         * Twice a day. The horizon is a week, so this is not about extending it -
         * it is the repair pass that catches a dropped alarm, and twice a day
         * bounds how long a broken chain can go unnoticed to half a day.
         */
        val SWEEP_INTERVAL: Duration = Duration.ofHours(12)
    }
}
