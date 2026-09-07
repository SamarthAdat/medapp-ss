package com.ss.medrecord.domain.usecase

import com.ss.medrecord.domain.model.Medicine
import com.ss.medrecord.domain.model.MedicineWithContext
import com.ss.medrecord.domain.model.ReportWithContext
import com.ss.medrecord.domain.model.TimelineEntry
import com.ss.medrecord.domain.model.TimelineKind
import com.ss.medrecord.domain.model.UpcomingAppointment
import com.ss.medrecord.domain.model.VisitWithContext
import com.ss.medrecord.domain.model.formatFileSize
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/**
 * Flattens the three record types into one chronological list.
 *
 * Pure functions on already-loaded lists rather than a class with injected
 * repositories: the merging and the date arithmetic are the parts worth
 * testing, and they should be testable without a database, a dispatcher or a
 * Flow around them. The callers that do the observing live in
 * [ObserveDashboard] and [ObservePatientTimeline].
 */
internal object BuildTimeline {

    fun from(
        visits: List<VisitWithContext>,
        reports: List<ReportWithContext>,
        medicines: List<MedicineWithContext>,
        zone: ZoneId = ZoneId.systemDefault(),
    ): List<TimelineEntry> =
        (visitEntries(visits) + reportEntries(reports, zone) + medicineEntries(medicines))
            // Newest first, and never ambiguous: two things on the same day fall
            // back to when they were written, so the order does not shuffle
            // between recompositions.
            .sortedWith(compareByDescending<TimelineEntry> { it.onEpochDay }
                .thenByDescending { it.recordedAtMillis })

    private fun visitEntries(visits: List<VisitWithContext>) = visits.map { entry ->
        TimelineEntry(
            id = "${TimelineKind.VISIT.name}:${entry.visit.visitId}",
            kind = TimelineKind.VISIT,
            targetId = entry.visit.visitId,
            patientId = entry.visit.patientId,
            patientName = entry.patientName,
            title = entry.facilityName ?: "Unknown facility",
            subtitle = entry.visit.doctorName?.takeIf { it.isNotBlank() },
            onEpochDay = entry.visit.visitDateEpochDay,
            recordedAtMillis = entry.visit.createdAt,
        )
    }

    private fun reportEntries(reports: List<ReportWithContext>, zone: ZoneId) =
        reports.map { entry ->
            TimelineEntry(
                id = "${TimelineKind.REPORT.name}:${entry.report.reportId}",
                kind = TimelineKind.REPORT,
                targetId = entry.report.reportId,
                patientId = entry.report.patientId,
                patientName = entry.patientName,
                title = entry.report.fileName,
                subtitle = listOfNotNull(
                    entry.facilityName,
                    formatFileSize(entry.report.fileSizeBytes),
                ).joinToString(" · "),
                // A report belongs to the visit it documents, not to the evening
                // someone got round to photographing it. Only when the visit
                // date is unknown does it fall back to when it was added.
                onEpochDay = entry.visitDateEpochDay
                    ?: epochDayOf(entry.report.createdAt, zone),
                recordedAtMillis = entry.report.createdAt,
            )
        }

    private fun medicineEntries(medicines: List<MedicineWithContext>) = medicines.map { entry ->
        TimelineEntry(
            id = "${TimelineKind.MEDICINE.name}:${entry.medicine.medicineId}",
            kind = TimelineKind.MEDICINE,
            targetId = entry.medicine.medicineId,
            patientId = entry.medicine.patientId,
            patientName = entry.patientName,
            title = entry.medicine.name,
            subtitle = medicineSubtitle(entry.medicine),
            // The day the course began, which is what a history is asking about -
            // not the day the row happened to be typed in.
            onEpochDay = entry.medicine.startDateEpochDay,
            recordedAtMillis = entry.medicine.createdAt,
        )
    }

    private fun medicineSubtitle(medicine: Medicine): String = buildString {
        medicine.dosage?.takeIf { it.isNotBlank() }?.let { append(it).append(" · ") }
        append(medicine.frequency.label)
        if (!medicine.isActive) append(" · stopped")
    }

    private fun epochDayOf(millis: Long, zone: ZoneId): Long =
        Instant.ofEpochMilli(millis).atZone(zone).toLocalDate().toEpochDay()

    /**
     * Follow-ups between today and [withinDays] ahead.
     *
     * Bounded at both ends. One already past is a missed appointment, and
     * putting it on a dashboard under "upcoming" would be a lie; one months out
     * is not something to act on today.
     */
    fun upcomingAppointments(
        visits: List<VisitWithContext>,
        withinDays: Int,
        today: LocalDate = LocalDate.now(),
    ) = visits
        .mapNotNull { entry ->
            val next = entry.visit.nextVisitDateEpochDay ?: return@mapNotNull null
            val daysAway = next - today.toEpochDay()
            if (daysAway < 0 || daysAway > withinDays) return@mapNotNull null
            UpcomingAppointment(
                visitId = entry.visit.visitId,
                patientId = entry.visit.patientId,
                patientName = entry.patientName,
                facilityName = entry.facilityName,
                doctorName = entry.visit.doctorName?.takeIf { it.isNotBlank() },
                onEpochDay = next,
            )
        }
        .sortedBy { it.onEpochDay }
}
