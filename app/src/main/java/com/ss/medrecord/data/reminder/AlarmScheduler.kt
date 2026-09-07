package com.ss.medrecord.data.reminder

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.provider.Settings
import android.util.Log
import androidx.core.content.ContextCompat
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

private const val TAG = "AlarmScheduler"

/**
 * Arms exactly one alarm at a time: the next reminder due on this device.
 *
 * The obvious design is one alarm per reminder. It is also the wrong one -
 * exact alarms are a rationed resource, and a family with several medicines
 * would be holding a hundred of them for a week's schedule. Instead the next
 * due reminder is armed, and when it fires the receiver posts everything due at
 * that moment and arms the one after. The table is the schedule; AlarmManager
 * only ever holds the pointer to its head.
 *
 * The cost is that the chain breaks if an alarm is never delivered - a force
 * stop, a reboot, an aggressive OEM battery manager. That is what the boot
 * receiver and the daily sweep are for: both re-arm from the table, so a broken
 * chain costs at most one day of reminders rather than all of them.
 *
 * Exactness degrades rather than failing. Without SCHEDULE_EXACT_ALARM the
 * alarm is set inexact-but-doze-tolerant, so a dose reminder may arrive some
 * minutes late instead of not at all.
 */
@Singleton
class AlarmScheduler @Inject constructor(
    @param:ApplicationContext private val context: Context,
) {

    private val alarmManager: AlarmManager?
        get() = ContextCompat.getSystemService(context, AlarmManager::class.java)

    /**
     * True when the system will honour an exact alarm. Always true below
     * API 31, where the permission does not exist; user-revocable above it.
     */
    fun canScheduleExact(): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return true
        return alarmManager?.canScheduleExactAlarms() == true
    }

    /** The system screen where the user can grant exact alarms, if there is one. */
    fun exactAlarmSettingsIntent(): Intent? {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return null
        return Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM)
            .setData(android.net.Uri.parse("package:${context.packageName}"))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }

    fun arm(triggerAtMillis: Long) {
        val manager = alarmManager ?: return
        val pending = alarmPendingIntent()

        // setAlarmClock is deliberately not used even though it survives doze
        // best: it puts a standing alarm icon in the status bar and exposes the
        // next trigger time to any app that asks. A medication schedule visible
        // to every installed app is not a trade worth making for punctuality.
        runCatching {
            if (canScheduleExact()) {
                manager.setExactAndAllowWhileIdle(
                    AlarmManager.RTC_WAKEUP,
                    triggerAtMillis,
                    pending,
                )
            } else {
                manager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAtMillis, pending)
            }
        }.onFailure {
            // canScheduleExactAlarms can go stale between the check and the
            // call if the user revokes it in that window.
            Log.w(TAG, "Could not arm alarm for $triggerAtMillis", it)
            runCatching {
                manager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAtMillis, pending)
            }
        }
    }

    fun cancel() {
        alarmManager?.cancel(alarmPendingIntent())
    }

    /**
     * One request code for the whole app, which is what makes arming idempotent:
     * a second arm replaces the first rather than stacking a duplicate.
     */
    private fun alarmPendingIntent(): PendingIntent = PendingIntent.getBroadcast(
        context,
        REQUEST_CODE,
        Intent(context, ReminderReceiver::class.java).setAction(ReminderReceiver.ACTION_FIRE),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )

    private companion object {
        const val REQUEST_CODE = 7301
    }
}
