package com.ss.medrecord.data.reminder

import android.app.NotificationManager
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.ss.medrecord.core.notification.NotificationChannels
import com.ss.medrecord.domain.model.Reminder
import com.ss.medrecord.domain.model.ReminderType
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * The notification path against the real framework.
 *
 * Everything this covers compiles cleanly and fails at runtime: a missing
 * drawable resource, a PendingIntent built without FLAG_IMMUTABLE (which throws
 * outright from API 31), a channel id that was never created. None of that is
 * reachable from a JVM test, and all of it means a dose reminder that silently
 * never appears.
 */
@RunWith(AndroidJUnit4::class)
class ReminderNotifierTest {

    private lateinit var context: Context
    private lateinit var manager: NotificationManager
    private lateinit var notifier: ReminderNotifier
    private lateinit var alarmScheduler: AlarmScheduler

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        manager = context.getSystemService(NotificationManager::class.java)
        notifier = ReminderNotifier(context, NotificationChannels(context))
        alarmScheduler = AlarmScheduler(context)
        manager.cancelAll()
    }

    @After
    fun tearDown() {
        manager.cancelAll()
    }

    @Test
    fun everyChannelIsCreated() {
        NotificationChannels(context).ensureCreated()

        assertNotNull(manager.getNotificationChannel(NotificationChannels.MEDICINE))
        assertNotNull(manager.getNotificationChannel(NotificationChannels.VISIT))
        assertNotNull(manager.getNotificationChannel(NotificationChannels.SYNC))
    }

    @Test
    fun creatingChannelsTwiceIsHarmless() {
        // Called on every app start, so this has to be a no-op rather than a
        // reset of whatever importance the user has since chosen.
        NotificationChannels(context).ensureCreated()
        NotificationChannels(context).ensureCreated()

        assertNotNull(manager.getNotificationChannel(NotificationChannels.MEDICINE))
    }

    @Test
    fun aMedicineReminderPosts() {
        // The permission is granted by the test runner's manifest on API 33+;
        // if the device withholds it there is nothing to assert.
        assumeTrue(notifier.canPost())

        notifier.post(reminder(id = 1L, type = ReminderType.MEDICINE))

        val posted = manager.activeNotifications.singleOrNull { it.id == 1 }
        assertNotNull("expected a posted notification", posted)
        assertEquals(NotificationChannels.MEDICINE, posted?.notification?.channelId)
    }

    @Test
    fun anAppointmentReminderUsesItsOwnChannel() {
        assumeTrue(notifier.canPost())

        notifier.post(reminder(id = 2L, type = ReminderType.VISIT))

        val posted = manager.activeNotifications.singleOrNull { it.id == 2 }
        assertEquals(NotificationChannels.VISIT, posted?.notification?.channelId)
    }

    @Test
    fun twoDosesDueAtOnceAppearAsTwoNotifications() {
        // Not one overwriting the other: a person taking two things at 08:00
        // needs to be told about both.
        assumeTrue(notifier.canPost())

        notifier.post(reminder(id = 3L, title = "Time for Metformin"))
        notifier.post(reminder(id = 4L, title = "Time for Ramipril"))

        assertEquals(2, manager.activeNotifications.count { it.id == 3 || it.id == 4 })
    }

    @Test
    fun cancellingRemovesTheNotification() {
        assumeTrue(notifier.canPost())
        val target = reminder(id = 5L)
        notifier.post(target)

        notifier.cancel(target)

        // NotificationManager is a system service reached over Binder, so a
        // cancel is not visible the instant the call returns. Polling here
        // rather than asserting immediately - the alternative is a test that
        // passes or fails on scheduler luck.
        assertTrue(
            "notification 5 was still posted",
            waitUntil { manager.activeNotifications.none { it.id == 5 } },
        )
    }

    @Test
    fun armingAnAlarmDoesNotThrow() {
        // Covers the PendingIntent flags and the exact/inexact fallback. A
        // mutable PendingIntent throws outright from API 31.
        alarmScheduler.arm(System.currentTimeMillis() + 60_000L)
        alarmScheduler.cancel()
    }

    /** True if [condition] becomes true within a second. */
    private fun waitUntil(condition: () -> Boolean): Boolean {
        val deadline = System.currentTimeMillis() + 1_000L
        while (System.currentTimeMillis() < deadline) {
            if (condition()) return true
            Thread.sleep(25L)
        }
        return condition()
    }

    private fun reminder(
        id: Long,
        type: ReminderType = ReminderType.MEDICINE,
        title: String = "Time for Metformin",
    ) = Reminder(
        reminderId = id,
        userId = "u1",
        patientId = "p1",
        type = type,
        sourceId = "m1",
        triggerAtMillis = System.currentTimeMillis(),
        title = title,
        message = "Asha · 08:00 · 500 mg",
    )
}
