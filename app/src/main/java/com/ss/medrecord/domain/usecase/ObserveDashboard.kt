package com.ss.medrecord.domain.usecase

import com.ss.medrecord.core.common.AppConstants
import com.ss.medrecord.domain.model.AuthSession
import com.ss.medrecord.domain.model.DashboardSnapshot
import com.ss.medrecord.domain.model.MedicineWithContext
import com.ss.medrecord.domain.model.Patient
import com.ss.medrecord.domain.model.RecordCounts
import com.ss.medrecord.domain.model.Reminder
import com.ss.medrecord.domain.model.ReportWithContext
import com.ss.medrecord.domain.model.VisitWithContext
import com.ss.medrecord.domain.repository.MedicineRepository
import com.ss.medrecord.domain.repository.ReminderRepository
import com.ss.medrecord.domain.repository.ReportRepository
import com.ss.medrecord.domain.repository.VisitRepository
import com.ss.medrecord.domain.session.ActivePatientManager
import com.ss.medrecord.domain.session.SessionManager
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Everything the dashboard shows, from four repositories at once.
 *
 * The whole account is observed and then scoped in memory, rather than issuing
 * a query per section per patient. At the scale this app operates at - one
 * family's records - that is a handful of small queries instead of a dozen, and
 * it removes a class of bug the per-section approach invites: sections
 * disagreeing with each other because they were read at different moments
 * during a sync.
 *
 * It also means the at-a-glance counts and the recent activity are computed
 * from the same rows, so a count can never claim three visits next to a list
 * showing two.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@Singleton
class ObserveDashboard @Inject constructor(
    private val sessionManager: SessionManager,
    private val activePatientManager: ActivePatientManager,
    private val visitRepository: VisitRepository,
    private val reportRepository: ReportRepository,
    private val medicineRepository: MedicineRepository,
    private val reminderRepository: ReminderRepository,
) {

    operator fun invoke(): Flow<DashboardSnapshot> = sessionManager.session
        .flatMapLatest { session ->
            when (session) {
                is AuthSession.Authenticated -> forUser(session.userId)
                // Signed out or awaiting consent: an empty dashboard, not the
                // previous session's records left on screen.
                else -> flowOf(DashboardSnapshot())
            }
        }

    private fun forUser(userId: String): Flow<DashboardSnapshot> = combine(
        records(userId),
        activePatientManager.activePatient,
        activePatientManager.patients,
        reminderRepository.observeToday(userId),
    ) { records, activePatient, patients, reminders ->
        snapshot(records, activePatient, patients.size, reminders)
    }

    private fun records(userId: String): Flow<AccountRecords> = combine(
        visitRepository.observeVisitsWithContext(userId),
        reportRepository.observeReportsWithContext(userId),
        medicineRepository.observeMedicines(userId),
    ) { visits, reports, medicines -> AccountRecords(visits, reports, medicines) }

    private fun snapshot(
        records: AccountRecords,
        activePatient: Patient?,
        patientCount: Int,
        reminders: List<Reminder>,
    ): DashboardSnapshot {
        val patientId = activePatient?.patientId

        return DashboardSnapshot(
            activePatient = activePatient,
            patientCount = patientCount,
            counts = RecordCounts(
                visits = records.visits.count { it.visit.patientId == patientId },
                reports = records.reports.count { it.report.patientId == patientId },
                activeMedicines = records.medicines.count {
                    it.medicine.patientId == patientId && it.medicine.isActive
                },
            ),
            // Appointments and doses stay account-wide even though the counts
            // above are per-patient. Someone who has to take a child to a clinic
            // on Thursday needs to see that without switching profile first;
            // "what do I owe today" is a household question.
            upcomingAppointments = BuildTimeline.upcomingAppointments(
                visits = records.visits,
                withinDays = AppConstants.UPCOMING_VISITS_WINDOW_DAYS,
            ),
            dosesDueToday = reminders.filter { it.isPending }.sortedBy { it.triggerAtMillis },
            recentActivity = BuildTimeline
                .from(records.visits, records.reports, records.medicines)
                .take(RECENT_ACTIVITY_LIMIT),
        )
    }

    private data class AccountRecords(
        val visits: List<VisitWithContext>,
        val reports: List<ReportWithContext>,
        val medicines: List<MedicineWithContext>,
    )

    private companion object {
        /** Enough to show a pattern, few enough that the dashboard stays a summary. */
        const val RECENT_ACTIVITY_LIMIT = 6
    }
}
