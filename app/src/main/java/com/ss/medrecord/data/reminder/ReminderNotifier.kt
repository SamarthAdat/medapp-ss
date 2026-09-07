package com.ss.medrecord.data.reminder

import android.Manifest
import android.annotation.SuppressLint
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.ss.medrecord.MainActivity
import com.ss.medrecord.R
import com.ss.medrecord.core.notification.NotificationChannels
import com.ss.medrecord.domain.model.Reminder
import com.ss.medrecord.domain.model.ReminderType
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

private const val TAG = "ReminderNotifier"

/**
 * Posts a due reminder.
 *
 * The notification carries only what the user already put in: a medicine name
 * they typed and a patient name they chose. That is unavoidable - a reminder
 * that does not say what to take is not a reminder - but it is also the limit.
 * Dosage and instructions go in the collapsed body rather than the title, so
 * the lock screen shows the least it can while still being useful, and nothing
 * here reaches into diagnoses, notes or reports.
 */
@Singleton
class ReminderNotifier @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val channels: NotificationChannels,
) {

    /**
     * False when the user has not granted POST_NOTIFICATIONS, or has turned
     * notifications off entirely. Checked before scheduling work as well as
     * before posting, so the app can say so rather than silently doing nothing.
     */
    fun canPost(): Boolean {
        if (!NotificationManagerCompat.from(context).areNotificationsEnabled()) return false
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return true
        return ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.POST_NOTIFICATIONS,
        ) == PackageManager.PERMISSION_GRANTED
    }

    // POST_NOTIFICATIONS is checked by canPost() on the first line, and the
    // call is wrapped in runCatching besides. Lint cannot follow the check
    // through a helper, so it is suppressed here rather than duplicated inline.
    @SuppressLint("MissingPermission")
    fun post(reminder: Reminder) {
        if (!canPost()) {
            Log.d(TAG, "Notifications not permitted; dropping reminder ${reminder.reminderId}")
            return
        }
        channels.ensureCreated()

        val notification = NotificationCompat.Builder(context, channelFor(reminder.type))
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(reminder.title)
            .setContentText(reminder.message)
            .setStyle(NotificationCompat.BigTextStyle().bigText(reminder.message))
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setPriority(priorityFor(reminder.type))
            // Health data on a lock screen is the user's call, not the app's.
            // PRIVATE hides the content until the device is unlocked and shows
            // the public substitute below in its place.
            .setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
            .setPublicVersion(publicVersion(reminder))
            .setAutoCancel(true)
            .setContentIntent(openAppIntent(reminder))
            .addAction(
                0,
                if (reminder.type == ReminderType.MEDICINE) "Taken" else "Got it",
                dismissIntent(reminder),
            )
            .setDeleteIntent(dismissIntent(reminder))
            .build()

        // The row id doubles as the notification id, so two doses due at once
        // appear as two notifications rather than overwriting each other.
        runCatching { NotificationManagerCompat.from(context).notify(reminder.notificationId, notification) }
            .onFailure { Log.w(TAG, "Could not post reminder ${reminder.reminderId}", it) }
    }

    fun cancel(reminder: Reminder) {
        NotificationManagerCompat.from(context).cancel(reminder.notificationId)
    }

    /**
     * What a locked screen shows instead. Deliberately says nothing at all
     * about what the reminder is for.
     */
    private fun publicVersion(reminder: Reminder) =
        NotificationCompat.Builder(context, channelFor(reminder.type))
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(
                when (reminder.type) {
                    ReminderType.MEDICINE -> "Medicine reminder"
                    ReminderType.VISIT -> "Appointment reminder"
                },
            )
            .setContentText("Open MedRecord Keeper to see the details.")
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .build()

    private fun channelFor(type: ReminderType) = when (type) {
        ReminderType.MEDICINE -> NotificationChannels.MEDICINE
        ReminderType.VISIT -> NotificationChannels.VISIT
    }

    private fun priorityFor(type: ReminderType) = when (type) {
        ReminderType.MEDICINE -> NotificationCompat.PRIORITY_HIGH
        ReminderType.VISIT -> NotificationCompat.PRIORITY_DEFAULT
    }

    private fun openAppIntent(reminder: Reminder): PendingIntent {
        val intent = Intent(context, MainActivity::class.java)
            .setFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            .putExtra(MainActivity.EXTRA_REMINDER_TYPE, reminder.type.name)
        return PendingIntent.getActivity(
            context,
            reminder.notificationId,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    private fun dismissIntent(reminder: Reminder): PendingIntent {
        val intent = Intent(context, ReminderReceiver::class.java)
            .setAction(ReminderReceiver.ACTION_DISMISS)
            .putExtra(ReminderReceiver.EXTRA_REMINDER_ID, reminder.reminderId)
        return PendingIntent.getBroadcast(
            context,
            // Offset so the dismiss intent never collides with the open intent,
            // which would make one silently replace the other.
            reminder.notificationId + DISMISS_REQUEST_OFFSET,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    private companion object {
        const val DISMISS_REQUEST_OFFSET = 1_000_000
    }
}

/**
 * Row ids are Long and notification ids are Int. Truncating is safe here: ids
 * are sequential from 1 and a device would have to schedule two billion
 * reminders to wrap.
 */
private val Reminder.notificationId: Int get() = reminderId.toInt()
