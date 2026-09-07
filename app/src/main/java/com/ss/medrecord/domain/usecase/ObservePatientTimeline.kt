package com.ss.medrecord.domain.usecase

import com.ss.medrecord.domain.model.AuthSession
import com.ss.medrecord.domain.model.TimelineEntry
import com.ss.medrecord.domain.repository.MedicineRepository
import com.ss.medrecord.domain.repository.ReportRepository
import com.ss.medrecord.domain.repository.VisitRepository
import com.ss.medrecord.domain.session.SessionManager
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import javax.inject.Inject
import javax.inject.Singleton

/**
 * The merged history for one patient, or for the whole account.
 *
 * The same account-wide queries the dashboard uses, filtered here rather than
 * re-queried per patient. Room shares one observer per query, so a screen
 * opened alongside the dashboard costs nothing extra, and the two can never
 * show a different set of records for the same moment.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@Singleton
class ObservePatientTimeline @Inject constructor(
    private val sessionManager: SessionManager,
    private val visitRepository: VisitRepository,
    private val reportRepository: ReportRepository,
    private val medicineRepository: MedicineRepository,
) {

    /** [patientId] null means every patient on the account. */
    operator fun invoke(patientId: String?): Flow<List<TimelineEntry>> =
        sessionManager.session.flatMapLatest { session ->
            when (session) {
                is AuthSession.Authenticated -> forUser(session.userId, patientId)
                else -> flowOf(emptyList())
            }
        }

    private fun forUser(userId: String, patientId: String?): Flow<List<TimelineEntry>> = combine(
        visitRepository.observeVisitsWithContext(userId),
        reportRepository.observeReportsWithContext(userId),
        medicineRepository.observeMedicines(userId),
    ) { visits, reports, medicines ->
        BuildTimeline.from(visits, reports, medicines)
            .filter { patientId == null || it.patientId == patientId }
    }
}
