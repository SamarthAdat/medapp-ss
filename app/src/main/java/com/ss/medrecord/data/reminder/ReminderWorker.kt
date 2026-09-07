package com.ss.medrecord.data.reminder

import android.content.Context
import android.util.Log
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.ss.medrecord.core.common.DispatcherProvider
import com.ss.medrecord.core.notification.NotificationChannels
import com.ss.medrecord.data.remote.FirebaseAuthDataSource
import com.ss.medrecord.domain.repository.ReminderRepository
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.withContext

private const val TAG = "ReminderWorker"

/**
 * Rebuilds the reminder horizon and re-arms the next alarm.
 *
 * This is the repair mechanism for everything the alarm chain cannot survive on
 * its own: a reboot, a force stop, an OEM battery manager dropping the alarm, a
 * medicine edited on another device, or simply the horizon running out. Every
 * path that could invalidate the schedule ends here, and the work is
 * unconditional and idempotent, so running it needlessly costs one query.
 *
 * No network constraint, deliberately. Reminders are derived from data already
 * on the device, and the whole point is that they fire on a phone in aeroplane
 * mode in a hospital basement.
 */
@HiltWorker
class ReminderWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted workerParams: WorkerParameters,
    private val reminderRepository: ReminderRepository,
    private val alarmScheduler: AlarmScheduler,
    private val notifier: ReminderNotifier,
    private val channels: NotificationChannels,
    private val authDataSource: FirebaseAuthDataSource,
    private val dispatchers: DispatcherProvider,
) : CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result = withContext(dispatchers.io) {
        val userId = authDataSource.currentUserId
        if (userId == null) {
            // Signed out: cancel rather than reschedule. Leaving an alarm armed
            // would fire a reminder naming a patient nobody is signed in as.
            alarmScheduler.cancel()
            reminderRepository.clear()
            return@withContext Result.success()
        }

        channels.ensureCreated()

        val scheduled = runCatching { reminderRepository.rebuild(userId) }
            .onFailure { Log.w(TAG, "Reminder rebuild failed", it) }
            .getOrElse { return@withContext Result.retry() }

        val now = System.currentTimeMillis()

        // A reminder that came due while the app was not running - the device
        // was off, or the alarm was dropped - is posted here rather than being
        // silently skipped, subject to the staleness window in the repository.
        val due = reminderRepository.due(now)
        if (due.isNotEmpty() && notifier.canPost()) {
            Log.d(TAG, "Posting ${due.size} missed reminder(s)")
            due.forEach(notifier::post)
            reminderRepository.markFired(due.map { it.reminderId })
        }

        val next = reminderRepository.next(now)
        if (next != null) {
            alarmScheduler.arm(next.triggerAtMillis)
            Log.d(TAG, "Scheduled $scheduled reminder(s); next at ${next.triggerAtMillis}")
        } else {
            alarmScheduler.cancel()
        }

        Result.success()
    }

    companion object {
        const val WORK_NAME_ONE_SHOT = "medrecord_reminder_rebuild"
        const val WORK_NAME_PERIODIC = "medrecord_reminder_sweep"
    }
}
