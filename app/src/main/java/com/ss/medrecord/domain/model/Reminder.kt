package com.ss.medrecord.domain.model

/**
 * One scheduled notification: a dose due, or a follow-up appointment coming up
 * (spec section 4.6).
 *
 * Reminders are deliberately **device-local and never synced**, which is the
 * one place this app stores something the cloud does not see.
 *
 * The reasoning: a reminder is not a record, it is a derivation. Every row here
 * is recomputed from a medicine's schedule or a visit's next-visit date, both
 * of which do sync. Pushing the derivation as well would mean two
 * representations of the same fact that can disagree, an outbox and a conflict
 * path for rows the user never edits, and - worst - a dose reminder that fails
 * to fire because its row had not synced down yet. An alarm has to be armed on
 * this device with this device's AlarmManager regardless, so the row that
 * tracks it belongs on this device too.
 *
 * The visible consequence is that two devices signed into the same account both
 * notify, and dismissing on one does not dismiss on the other. For a medication
 * reminder that is the better failure: a dose reminded about twice is a
 * nuisance, a dose reminded about nowhere is a missed dose.
 *
 * [reminderId] is assigned by Room rather than composed from the source, so it
 * can be used directly as a PendingIntent request code without hashing - and
 * therefore without the collision that would let one medicine's alarm silently
 * cancel another's.
 */
data class Reminder(
    val reminderId: Long = 0L,
    val userId: String,
    val patientId: String,
    val type: ReminderType,
    /** The medicine or visit this was generated from. */
    val sourceId: String,
    /** Wall-clock instant the notification is due. */
    val triggerAtMillis: Long,
    val title: String,
    val message: String,
    val status: ReminderStatus = ReminderStatus.SCHEDULED,
) {
    val isPending: Boolean get() = status == ReminderStatus.SCHEDULED

    fun isDueBy(nowMillis: Long): Boolean = isPending && triggerAtMillis <= nowMillis
}

enum class ReminderType {
    MEDICINE,
    VISIT,
}

enum class ReminderStatus {
    /** Armed, or waiting for the next alarm sweep to arm it. */
    SCHEDULED,

    /** The notification has been posted. Kept so a sweep cannot re-post it. */
    FIRED,

    /** The user acted on it, or its source changed and it no longer applies. */
    DISMISSED,
}
