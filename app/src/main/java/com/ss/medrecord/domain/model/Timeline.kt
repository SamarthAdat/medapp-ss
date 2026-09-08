package com.ss.medrecord.domain.model

import java.time.LocalDate

/**
 * One thing that happened, flattened out of whichever table it came from
 * (spec section 5.9).
 *
 * Visits, reports and medicines are separate records with separate lifecycles,
 * but a person recalling their own history does not think in tables - they
 * think "March, the clinic, the blood test, the tablets they started me on".
 * This is the shape that lets those be shown together in one list.
 *
 * The flattening is deliberately lossy. An entry carries what a row needs to
 * render and the id to open the real record, and nothing else: duplicating
 * clinical detail into a second representation would give it somewhere else to
 * go stale.
 */
data class TimelineEntry(
    /** Unique across kinds, so a merged list has stable keys. */
    val id: String,
    val kind: TimelineKind,
    /** The record to open: a visitId, reportId or medicineId. */
    val targetId: String,
    val patientId: String,
    val patientName: String?,
    val title: String,
    val subtitle: String?,
    /**
     * The day this belongs under, which is not always when the row was written.
     * A report photographed weeks later belongs to the visit it documents, not
     * to the evening someone got round to scanning it.
     */
    val onEpochDay: Long,
    /** Tie-break within a day, and the only thing ordering same-day entries. */
    val recordedAtMillis: Long,
) {
    val date: LocalDate get() = LocalDate.ofEpochDay(onEpochDay)
}

enum class TimelineKind(val label: String) {
    VISIT("Visit"),
    REPORT("Report"),
    MEDICINE("Medicine"),
}

/**
 * A follow-up appointment close enough to act on (spec section 5.3).
 *
 * Distinct from a [Reminder] of type VISIT: that is a notification this device
 * has armed, which depends on permissions and can be dismissed. This is the
 * appointment itself, and it belongs on the dashboard whether or not anything
 * was ever allowed to ring.
 */
data class UpcomingAppointment(
    val visitId: String,
    val patientId: String,
    val patientName: String?,
    val facilityName: String?,
    val doctorName: String?,
    val onEpochDay: Long,
) {
    val date: LocalDate get() = LocalDate.ofEpochDay(onEpochDay)

    /** Negative once it has passed, which the dashboard does not show. */
    fun daysAway(today: LocalDate = LocalDate.now()): Long = onEpochDay - today.toEpochDay()
}

/** What one patient has on file, for the dashboard's at-a-glance row. */
data class RecordCounts(
    val visits: Int = 0,
    val reports: Int = 0,
    val activeMedicines: Int = 0,
) {
    val isEmpty: Boolean get() = visits == 0 && reports == 0 && activeMedicines == 0
}

/**
 * Everything the dashboard renders, resolved in one place.
 *
 * Assembled by a use case rather than by the ViewModel: it spans four
 * repositories, and the arithmetic that turns them into upcoming appointments
 * and recent activity is worth testing without a ViewModel or Android around
 * it.
 */
data class DashboardSnapshot(
    val activePatient: Patient? = null,
    /**
     * Every profile on the account, so the dashboard can offer the switch
     * inline instead of sending the user to another screen to make it.
     */
    val patients: List<Patient> = emptyList(),
    val counts: RecordCounts = RecordCounts(),
    val upcomingAppointments: List<UpcomingAppointment> = emptyList(),
    /**
     * Every medicine reminder scheduled for today, whatever state it is in -
     * not only the ones still pending. The dashboard needs the denominator as
     * well as the numerator to say how far through the day the schedule is.
     */
    val dosesToday: List<Reminder> = emptyList(),
    val recentActivity: List<TimelineEntry> = emptyList(),
    /**
     * Visits per month across the current calendar year, January first.
     *
     * Always twelve entries, including the months still ahead. A chart that
     * grew a column each month would rescale under the reader every few weeks;
     * a fixed year that fills up left to right does not.
     */
    val visitsByMonth: List<Int> = List(MONTHS_IN_YEAR) { 0 },
) {
    val patientCount: Int get() = patients.size

    val needsFirstPatient: Boolean get() = patients.isEmpty()

    val visitsThisYear: Int get() = visitsByMonth.sum()

    /** True when there is something time-sensitive worth leading the screen with. */
    val hasUpcoming: Boolean
        get() = upcomingAppointments.isNotEmpty() || dosesDueToday.isNotEmpty()

    /** Today's doses that have not gone off yet, soonest first. */
    val dosesDueToday: List<Reminder>
        get() = dosesToday.filter { it.isPending }.sortedBy { it.triggerAtMillis }

    val dosesScheduledToday: Int get() = dosesToday.size

    /**
     * Doses whose time has passed, out of everything scheduled for today.
     *
     * Note what this is *not*: it is not adherence. The app arms reminders and
     * records that they fired or were dismissed; it has never had any way to
     * know whether a tablet was actually swallowed. Calling this "taken" would
     * put a number on screen that looks like a clinical fact and is not, so
     * both the field and the label on the dashboard say "left" instead.
     */
    val dosesElapsedToday: Int get() = dosesToday.count { !it.isPending }
}

/** Twelve, but named, because the sparkline reads it as a contract. */
const val MONTHS_IN_YEAR = 12
