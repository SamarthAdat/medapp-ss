package com.ss.medrecord.data.reminder

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.ss.medrecord.domain.repository.ReminderRepository
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import javax.inject.Inject

private const val TAG = "ReminderReceiver"

/**
 * Where an armed alarm lands, and where a "Taken" tap lands.
 *
 * A BroadcastReceiver gets a few seconds on the main thread and is killed after
 * onReceive returns, so the work is wrapped in goAsync() - which keeps the
 * process alive until finish() is called. Both branches call it exactly once,
 * including on failure; leaking a pending result is how a receiver ends up
 * holding a wake lock until the system force-stops the app.
 *
 * The alarm branch does three things in order, and the order matters: post
 * what is due, mark it fired, then arm the next alarm. Arming last means a
 * crash while posting still leaves the chain to be repaired by the daily sweep
 * rather than leaving an alarm armed for a reminder that was never posted.
 */
@AndroidEntryPoint
class ReminderReceiver : BroadcastReceiver() {

    @Inject
    lateinit var reminderRepository: ReminderRepository

    @Inject
    lateinit var notifier: ReminderNotifier

    @Inject
    lateinit var alarmScheduler: AlarmScheduler

    override fun onReceive(context: Context, intent: Intent) {
        val pending = goAsync()
        CoroutineScope(Dispatchers.Default).launch {
            try {
                when (intent.action) {
                    ACTION_FIRE -> fireDue()
                    ACTION_DISMISS -> dismiss(intent.getLongExtra(EXTRA_REMINDER_ID, -1L))
                    else -> Log.d(TAG, "Ignoring ${intent.action}")
                }
            } catch (e: Exception) {
                Log.w(TAG, "Reminder broadcast failed", e)
            } finally {
                pending.finish()
            }
        }
    }

    private suspend fun fireDue() {
        val now = System.currentTimeMillis()
        val due = reminderRepository.due(now)
        Log.d(TAG, "Alarm fired with ${due.size} reminder(s) due")

        due.forEach(notifier::post)
        reminderRepository.markFired(due.map { it.reminderId })

        // Arm the next one from the table rather than from what was just
        // posted, so a reminder added since this alarm was set is not skipped.
        reminderRepository.next(now)?.let { alarmScheduler.arm(it.triggerAtMillis) }
    }

    private suspend fun dismiss(reminderId: Long) {
        if (reminderId <= 0L) return
        reminderRepository.dismiss(reminderId)
        reminderRepository.getReminder(reminderId)?.let(notifier::cancel)
    }

    companion object {
        const val ACTION_FIRE = "com.ss.medrecord.action.REMINDER_FIRE"
        const val ACTION_DISMISS = "com.ss.medrecord.action.REMINDER_DISMISS"
        const val EXTRA_REMINDER_ID = "reminder_id"
    }
}
