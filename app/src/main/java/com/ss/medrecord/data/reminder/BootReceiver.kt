package com.ss.medrecord.data.reminder

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.ss.medrecord.domain.reminder.ReminderScheduler
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

private const val TAG = "BootReceiver"

/**
 * Re-arms the reminder chain after the events that silently break it.
 *
 * Android drops every alarm an app has set when the device reboots, and it does
 * the same when the app is updated or its package is otherwise replaced. Both
 * are invisible to the user, so without this a phone restart would quietly stop
 * every medication reminder and nothing would say so.
 *
 * The rebuild is delegated to [ReminderScheduler] rather than done here: boot
 * is exactly when the device is busiest, and a receiver that starts scanning
 * tables during it is a receiver that gets killed halfway through.
 */
@AndroidEntryPoint
class BootReceiver : BroadcastReceiver() {

    @Inject
    lateinit var reminderScheduler: ReminderScheduler

    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            Intent.ACTION_BOOT_COMPLETED,
            Intent.ACTION_MY_PACKAGE_REPLACED,
            ACTION_QUICKBOOT_POWERON,
            -> {
                Log.d(TAG, "Rearming reminders after ${intent.action}")
                reminderScheduler.requestRebuild()
            }

            else -> Unit
        }
    }

    private companion object {
        /** Some HTC and older OEM builds send this instead of BOOT_COMPLETED. */
        const val ACTION_QUICKBOOT_POWERON = "android.intent.action.QUICKBOOT_POWERON"
    }
}
