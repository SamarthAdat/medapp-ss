package com.ss.medrecord.domain.usecase

import com.ss.medrecord.domain.model.Medicine
import com.ss.medrecord.domain.model.MedicineFrequency
import com.ss.medrecord.domain.model.MedicineWithContext
import com.ss.medrecord.domain.model.Report
import com.ss.medrecord.domain.model.ReportFileType
import com.ss.medrecord.domain.model.ReportWithContext
import com.ss.medrecord.domain.model.TimelineKind
import com.ss.medrecord.domain.model.Visit
import com.ss.medrecord.domain.model.VisitWithContext
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId

/**
 * The merge and the date arithmetic behind the dashboard and the timeline.
 *
 * Kept as pure functions precisely so this file needs no database, no
 * dispatcher and no Android: these are the parts that decide what a person sees
 * when they ask what happened in March.
 */
class BuildTimelineTest {

    @Test
    fun `entries are ordered newest first`() {
        val entries = BuildTimeline.from(
            visits = listOf(visit("v1", on = DAY), visit("v2", on = DAY.plusDays(3))),
            reports = emptyList(),
            medicines = emptyList(),
        )

        assertEquals(listOf("v2", "v1"), entries.map { it.targetId })
    }

    @Test
    fun `same-day entries fall back to when they were recorded`() {
        // Without a tie-break the order would depend on which list was
        // concatenated first, and would shuffle between recompositions.
        val entries = BuildTimeline.from(
            visits = listOf(visit("v1", on = DAY, createdAt = 100L)),
            reports = listOf(report("r1", visitDay = DAY, createdAt = 300L)),
            medicines = listOf(medicine("m1", start = DAY, createdAt = 200L)),
        )

        assertEquals(listOf("r1", "m1", "v1"), entries.map { it.targetId })
    }

    @Test
    fun `all three record types are merged into one list`() {
        val entries = BuildTimeline.from(
            visits = listOf(visit("v1", on = DAY)),
            reports = listOf(report("r1", visitDay = DAY)),
            medicines = listOf(medicine("m1", start = DAY)),
        )

        assertEquals(
            setOf(TimelineKind.VISIT, TimelineKind.REPORT, TimelineKind.MEDICINE),
            entries.map { it.kind }.toSet(),
        )
    }

    @Test
    fun `ids are unique across kinds`() {
        // Nothing stops a visit and a report sharing an id; the list keys must
        // still be distinct or Compose will reuse the wrong row.
        val entries = BuildTimeline.from(
            visits = listOf(visit("same", on = DAY)),
            reports = listOf(report("same", visitDay = DAY)),
            medicines = listOf(medicine("same", start = DAY)),
        )

        assertEquals(3, entries.map { it.id }.distinct().size)
    }

    @Test
    fun `a report is dated by its visit, not by when it was scanned`() {
        val scannedWeeksLater = DAY.plusDays(21)
            .atStartOfDay(ZoneId.systemDefault())
            .toInstant()
            .toEpochMilli()

        val entries = BuildTimeline.from(
            visits = emptyList(),
            reports = listOf(report("r1", visitDay = DAY, createdAt = scannedWeeksLater)),
            medicines = emptyList(),
        )

        assertEquals(DAY.toEpochDay(), entries.single().onEpochDay)
    }

    @Test
    fun `a report with no visit date falls back to when it was added`() {
        val added = DAY.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()

        val entries = BuildTimeline.from(
            visits = emptyList(),
            reports = listOf(report("r1", visitDay = null, createdAt = added)),
            medicines = emptyList(),
        )

        assertEquals(DAY.toEpochDay(), entries.single().onEpochDay)
    }

    @Test
    fun `a medicine is dated by when the course began`() {
        val typedInLater = DAY.plusDays(10)
            .atStartOfDay(ZoneId.systemDefault())
            .toInstant()
            .toEpochMilli()

        val entries = BuildTimeline.from(
            visits = emptyList(),
            reports = emptyList(),
            medicines = listOf(medicine("m1", start = DAY, createdAt = typedInLater)),
        )

        assertEquals(DAY.toEpochDay(), entries.single().onEpochDay)
    }

    @Test
    fun `a stopped medicine says so`() {
        val entries = BuildTimeline.from(
            visits = emptyList(),
            reports = emptyList(),
            medicines = listOf(medicine("m1", start = DAY, isActive = false)),
        )

        assertTrue(entries.single().subtitle.orEmpty().endsWith("stopped"))
    }

    @Test
    fun `a visit with no facility still appears`() {
        // A facility that has not synced down yet is a data gap, not a reason
        // to drop a medical record out of someone's history.
        val entries = BuildTimeline.from(
            visits = listOf(visit("v1", on = DAY, facilityName = null)),
            reports = emptyList(),
            medicines = emptyList(),
        )

        assertEquals("Unknown facility", entries.single().title)
    }

    // --- upcoming appointments ---------------------------------------------

    @Test
    fun `an appointment inside the window is upcoming`() {
        val upcoming = BuildTimeline.upcomingAppointments(
            visits = listOf(visit("v1", on = TODAY, nextVisit = TODAY.plusDays(3))),
            withinDays = 7,
            today = TODAY,
        )

        assertEquals(listOf("v1"), upcoming.map { it.visitId })
        assertEquals(3L, upcoming.single().daysAway(TODAY))
    }

    @Test
    fun `an appointment today is upcoming`() {
        val upcoming = BuildTimeline.upcomingAppointments(
            visits = listOf(visit("v1", on = TODAY, nextVisit = TODAY)),
            withinDays = 7,
            today = TODAY,
        )

        assertEquals(1, upcoming.size)
    }

    @Test
    fun `an appointment already past is not upcoming`() {
        // It is a missed appointment. Listing it under "coming up" would be a
        // lie, and a dashboard that lies about dates stops being read.
        val upcoming = BuildTimeline.upcomingAppointments(
            visits = listOf(visit("v1", on = TODAY, nextVisit = TODAY.minusDays(1))),
            withinDays = 7,
            today = TODAY,
        )

        assertTrue(upcoming.isEmpty())
    }

    @Test
    fun `an appointment beyond the window is not upcoming`() {
        val upcoming = BuildTimeline.upcomingAppointments(
            visits = listOf(visit("v1", on = TODAY, nextVisit = TODAY.plusDays(30))),
            withinDays = 7,
            today = TODAY,
        )

        assertTrue(upcoming.isEmpty())
    }

    @Test
    fun `a visit with no follow-up date is not upcoming`() {
        val upcoming = BuildTimeline.upcomingAppointments(
            visits = listOf(visit("v1", on = TODAY, nextVisit = null)),
            withinDays = 7,
            today = TODAY,
        )

        assertTrue(upcoming.isEmpty())
    }

    @Test
    fun `upcoming appointments are ordered soonest first`() {
        val upcoming = BuildTimeline.upcomingAppointments(
            visits = listOf(
                visit("far", on = TODAY, nextVisit = TODAY.plusDays(6)),
                visit("near", on = TODAY, nextVisit = TODAY.plusDays(1)),
            ),
            withinDays = 7,
            today = TODAY,
        )

        assertEquals(listOf("near", "far"), upcoming.map { it.visitId })
    }

    private fun visit(
        id: String,
        on: LocalDate,
        createdAt: Long = 0L,
        facilityName: String? = "City Care Clinic",
        nextVisit: LocalDate? = null,
    ) = VisitWithContext(
        visit = Visit(
            visitId = id,
            userId = USER_ID,
            patientId = PATIENT_ID,
            facilityId = "f1",
            doctorName = "Dr Mehta",
            visitDateEpochDay = on.toEpochDay(),
            nextVisitDateEpochDay = nextVisit?.toEpochDay(),
            createdAt = createdAt,
            updatedAt = createdAt,
        ),
        facilityName = facilityName,
        patientName = "Asha",
    )

    private fun report(
        id: String,
        visitDay: LocalDate?,
        createdAt: Long = 0L,
    ) = ReportWithContext(
        report = Report(
            reportId = id,
            userId = USER_ID,
            patientId = PATIENT_ID,
            visitId = "v1",
            fileName = "blood-panel.pdf",
            fileType = ReportFileType.PDF,
            fileSizeBytes = 743_012,
            createdAt = createdAt,
            updatedAt = createdAt,
        ),
        visitDateEpochDay = visitDay?.toEpochDay(),
        facilityName = "City Care Clinic",
        patientName = "Asha",
    )

    private fun medicine(
        id: String,
        start: LocalDate,
        createdAt: Long = 0L,
        isActive: Boolean = true,
    ) = MedicineWithContext(
        medicine = Medicine(
            medicineId = id,
            userId = USER_ID,
            patientId = PATIENT_ID,
            name = "Metformin",
            dosage = "500 mg",
            frequency = MedicineFrequency.DAILY,
            reminderTimes = listOf(8 * 60),
            startDateEpochDay = start.toEpochDay(),
            isActive = isActive,
            createdAt = createdAt,
            updatedAt = createdAt,
        ),
        patientName = "Asha",
        facilityName = "City Care Clinic",
        visitDateEpochDay = start.toEpochDay(),
    )

    private companion object {
        const val USER_ID = "u1"
        const val PATIENT_ID = "p1"

        /** Fixed, so nothing here depends on the day the suite runs. */
        val DAY: LocalDate = LocalDate.of(2026, 3, 2)
        val TODAY: LocalDate = LocalDate.of(2026, 3, 10)
    }
}
