package com.ss.medrecord.domain.reminder

/**
 * The one thing the rest of the app is allowed to say about reminders: "the
 * schedule may be out of date, sort it out."
 *
 * Deliberately fire-and-forget and deliberately tiny. Rebuilding the schedule
 * touches four tables and talks to AlarmManager, and every caller that triggers
 * it - saving a medicine, finishing a sync pull, booting the device - is in the
 * middle of something else that must not wait for it or fail with it. So the
 * implementation only enqueues work, and the actual rebuild happens where it
 * can be retried.
 *
 * Keeping this in the domain layer is what stops the repositories from
 * depending on WorkManager or AlarmManager directly.
 */
interface ReminderScheduler {

    /** Regenerate the horizon and re-arm the next alarm, soon. */
    fun requestRebuild()

    /** Registers the periodic sweep that keeps the horizon rolling forward. */
    fun initialize()

    /** Drops every armed alarm and the rows behind them. Used on sign-out. */
    suspend fun cancelAll()
}
