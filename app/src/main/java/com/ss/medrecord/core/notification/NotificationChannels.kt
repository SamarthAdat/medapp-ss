package com.ss.medrecord.core.notification

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import androidx.core.app.NotificationManagerCompat
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * The three notification channels (spec section 7.3).
 *
 * Separate channels rather than one, because the user must be able to keep the
 * dose reminder while silencing the sync chatter. On Android the channel is the
 * unit of user control: importance, sound and Do Not Disturb are all set per
 * channel and cannot be changed by the app afterwards, so folding them together
 * would mean muting one is muting all three.
 *
 * Nothing here puts clinical detail in a channel name or description. Channel
 * metadata is visible in system settings and in some launchers, and "Blood
 * pressure medication" sitting in a settings list is a disclosure the user
 * never agreed to.
 */
@Singleton
class NotificationChannels @Inject constructor(
    @param:ApplicationContext private val context: Context,
) {

    /**
     * Safe to call repeatedly: creating a channel that exists updates its name
     * and description and leaves the user's own importance and sound choices
     * alone, which is exactly the behaviour wanted on every app start.
     */
    fun ensureCreated() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = NotificationManagerCompat.from(context)

        manager.createNotificationChannel(
            NotificationChannel(
                MEDICINE,
                "Medicine reminders",
                NotificationManager.IMPORTANCE_HIGH,
            ).apply {
                description = "Reminders for doses you have scheduled."
                enableVibration(true)
                // A dose reminder is the one thing here worth a heads-up
                // notification; it is useless if it is only found later.
                setShowBadge(true)
            },
        )

        manager.createNotificationChannel(
            NotificationChannel(
                VISIT,
                "Appointment reminders",
                NotificationManager.IMPORTANCE_DEFAULT,
            ).apply {
                description = "Reminders about upcoming follow-up visits."
                setShowBadge(true)
            },
        )

        manager.createNotificationChannel(
            NotificationChannel(
                SYNC,
                "Sync and backup",
                NotificationManager.IMPORTANCE_LOW,
            ).apply {
                description = "Quiet status messages about syncing and uploads."
                setShowBadge(false)
            },
        )
    }

    companion object {
        const val MEDICINE = "medrecord_medicine"
        const val VISIT = "medrecord_visit"
        const val SYNC = "medrecord_sync"
    }
}
