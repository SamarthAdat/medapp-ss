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
    val patientCount: Int = 0,
    val counts: RecordCounts = RecordCounts(),
    val upcomingAppointments: List<UpcomingAppointment> = emptyList(),
    val dosesDueToday: List<Reminder> = emptyList(),
    val recentActivity: List<TimelineEntry> = emptyList(),
) {
    val needsFirstPatient: Boolean get() = patientCount == 0

    /** True when there is something time-sensitive worth leading the screen with. */
    val hasUpcoming: Boolean
        get() = upcomingAppointments.isNotEmpty() || dosesDueToday.isNotEmpty()
}
