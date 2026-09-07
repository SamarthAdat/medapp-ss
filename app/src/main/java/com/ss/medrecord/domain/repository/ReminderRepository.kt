package com.ss.medrecord.domain.repository

import com.ss.medrecord.domain.model.Reminder
import kotlinx.coroutines.flow.Flow

/**
 * The device-local reminder schedule. No DataResult here, unlike every other
 * repository: nothing in this one talks to the network, so there is no error a
 * caller could act on that is not already a bug.
 */
interface ReminderRepository {

    /**
     * Re-derives the schedule for the next horizon from active medicines and
     * upcoming visits, and prunes what no longer applies. Idempotent: running
     * it twice produces the same rows and re-posts nothing.
     *
     * Returns the number of newly scheduled reminders, for logging.
     */
    suspend fun rebuild(userId: String): Int

    /** Everything due at or before [nowMillis] that has not yet been posted. */
    suspend fun due(nowMillis: Long): List<Reminder>

    /** The next thing to arm an alarm for, if any. */
    suspend fun next(afterMillis: Long): Reminder?

    /** Today's schedule, for the dashboard. */
    fun observeToday(userId: String): Flow<List<Reminder>>

    suspend fun getReminder(reminderId: Long): Reminder?

    suspend fun markFired(reminderIds: List<Long>)

    suspend fun dismiss(reminderId: Long)

    /** Sign-out: this device's schedule belongs to the session that made it. */
    suspend fun clear()
}
