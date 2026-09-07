package com.ss.medrecord.domain.model

import java.time.LocalDate
import java.time.LocalTime

/**
 * A prescribed medicine and its dosing schedule (spec section 4.5).
 *
 * A medicine may hang off a visit - "prescribed at this appointment" - or stand
 * alone, because plenty of what people take was never prescribed at a logged
 * visit: an over-the-counter painkiller, a supplement, something started years
 * before the app was installed. [visitId] is therefore nullable, and the visit
 * screen simply filters on it.
 *
 * The schedule is two independent things and is stored as two:
 *  - [frequency] says which days a dose falls on,
 *  - [reminderTimes] says at what times on those days.
 *
 * Collapsing them into a single "twice daily" enum reads well but cannot
 * express what people are actually told - "twice a day, 8am and 8pm" versus
 * "twice a day, with meals" - and would leave the reminder engine guessing at
 * the times. Here the times are the user's, and the count of them is the number
 * of doses a day.
 */
data class Medicine(
    val medicineId: String,
    val userId: String,
    val patientId: String,
    /** Null for a medicine not tied to a logged visit. */
    val visitId: String? = null,
    val name: String,
    /** Free text, e.g. "500 mg" or "1 tablet". Units vary too much to model. */
    val dosage: String? = null,
    val frequency: MedicineFrequency = MedicineFrequency.DAILY,
    /** Minutes from midnight, ascending. Empty means no reminders. */
    val reminderTimes: List<Int> = emptyList(),
    val startDateEpochDay: Long,
    /** Null means ongoing, which is normal for long-term medication. */
    val endDateEpochDay: Long? = null,
    val instructions: String? = null,
    /**
     * Whether the course is still being taken. Kept separate from the end date
     * so a medicine can be paused without destroying the schedule that would be
     * needed to resume it, and so stopping something early does not require
     * inventing an end date that never happened.
     */
    val isActive: Boolean = true,
    val createdAt: Long,
    val updatedAt: Long,
    val deletedAt: Long? = null,
) {
    val startDate: LocalDate get() = LocalDate.ofEpochDay(startDateEpochDay)

    val endDate: LocalDate? get() = endDateEpochDay?.let(LocalDate::ofEpochDay)

    val isDeleted: Boolean get() = deletedAt != null

    val times: List<LocalTime> get() = reminderTimes.sorted().map(::minutesToTime)

    /** How many doses fall on a day this medicine is taken. */
    val dosesPerDay: Int get() = reminderTimes.size

    /**
     * True when the course covers [date] and has not been stopped.
     *
     * An as-needed medicine is never "due" on a schedule, but it is still
     * current: it belongs in the list of what someone is taking, and it is the
     * reminder engine that skips it, not this.
     */
    fun isCurrentOn(date: LocalDate = LocalDate.now()): Boolean {
        if (!isActive || isDeleted) return false
        if (date.isBefore(startDate)) return false
        return endDate?.isBefore(date) != true
    }

    /**
     * Whether a dose falls on [date], counting from [startDate] so that an
     * alternate-day or weekly course stays anchored to the day it began rather
     * than drifting with the calendar.
     */
    fun fallsOn(date: LocalDate): Boolean {
        if (!isCurrentOn(date)) return false
        val interval = frequency.dayInterval ?: return false
        val elapsed = date.toEpochDay() - startDateEpochDay
        return elapsed >= 0 && elapsed % interval == 0L
    }

    /** A one-line schedule summary for list rows, e.g. "Every day, 08:00, 20:00". */
    fun scheduleSummary(): String = buildString {
        append(frequency.label)
        if (frequency.schedulesDoses && reminderTimes.isNotEmpty()) {
            append(" · ")
            append(times.joinToString(", ") { formatTime(it) })
        }
    }

    companion object {
        fun minutesToTime(minutes: Int): LocalTime =
            LocalTime.of((minutes / 60).coerceIn(0, 23), (minutes % 60).coerceIn(0, 59))

        fun timeToMinutes(time: LocalTime): Int = time.hour * 60 + time.minute

        /** 24-hour, and deliberately locale-independent: it is also an id component. */
        fun formatTime(time: LocalTime): String =
            "%02d:%02d".format(time.hour, time.minute)
    }
}

/**
 * Which days a dose falls on.
 *
 * [dayInterval] is null for as-needed, which is what tells the reminder engine
 * there is nothing to schedule: a PRN medicine has no predictable time, and
 * inventing one would train the user to dismiss the notification that matters.
 */
enum class MedicineFrequency(val label: String, val dayInterval: Int?) {
    DAILY("Every day", 1),
    ALTERNATE_DAYS("Every other day", 2),
    WEEKLY("Once a week", 7),
    AS_NEEDED("As needed", null);

    /** False for as-needed, the one case with no schedule to generate. */
    val schedulesDoses: Boolean get() = dayInterval != null
}

/** A medicine joined to the names its list row needs, so the query is one pass. */
data class MedicineWithContext(
    val medicine: Medicine,
    val patientName: String?,
    val facilityName: String?,
    val visitDateEpochDay: Long?,
) {
    val visitDate: LocalDate? get() = visitDateEpochDay?.let(LocalDate::ofEpochDay)
}
