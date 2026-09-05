package com.ss.medrecord.domain.session

import com.ss.medrecord.data.local.datastore.ActivePatientStore
import com.ss.medrecord.di.ApplicationScope
import com.ss.medrecord.domain.model.AuthSession
import com.ss.medrecord.domain.model.Patient
import com.ss.medrecord.domain.repository.PatientRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

/**
 * The active patient context that every patient-scoped screen reads from
 * (spec section 5.2).
 *
 * The selection is resolved against the live patient list rather than trusted
 * as a stored id, so a profile that was archived, deleted, or removed on
 * another device cannot remain selected. When the stored selection is not
 * usable the manager falls back to the first available profile, which keeps the
 * app in a workable state instead of silently showing nothing.
 */
@Singleton
class ActivePatientManager @Inject constructor(
    private val sessionManager: SessionManager,
    private val patientRepository: PatientRepository,
    private val activePatientStore: ActivePatientStore,
    @param:ApplicationScope private val scope: CoroutineScope,
) {

    init {
        // One pull per sign-in so a fresh device or reinstall shows existing
        // profiles. Ongoing convergence is the Phase 3 sync worker's job.
        scope.launch {
            sessionManager.session
                .filterIsInstance<AuthSession.Authenticated>()
                .map { it.userId }
                .distinctUntilChanged()
                .collect { userId -> patientRepository.refreshPatients(userId) }
        }
    }

    /** Active profiles for the signed-in account; empty when signed out. */
    @OptIn(ExperimentalCoroutinesApi::class)
    val patients: StateFlow<List<Patient>> = sessionManager.session
        .flatMapLatest { session ->
            when (session) {
                is AuthSession.Authenticated ->
                    patientRepository.observeActivePatients(session.userId)

                // Patient data is not readable before consent is on file.
                else -> flowOf(emptyList())
            }
        }
        .stateIn(scope, SharingStarted.Eagerly, emptyList())

    @OptIn(ExperimentalCoroutinesApi::class)
    val activePatient: StateFlow<Patient?> = sessionManager.session
        .flatMapLatest { session ->
            when (session) {
                is AuthSession.Authenticated -> resolveActive(session.userId)
                else -> flowOf(null)
            }
        }
        .distinctUntilChanged()
        .stateIn(scope, SharingStarted.Eagerly, null)

    fun setActivePatient(patientId: String?) {
        scope.launch {
            val userId = (sessionManager.session.value as? AuthSession.Authenticated)?.userId
                ?: return@launch
            activePatientStore.setActivePatient(userId, patientId)
        }
    }

    /** Called on sign-out so a second account starts with no selection. */
    suspend fun clear() = activePatientStore.clear()

    private fun resolveActive(userId: String): Flow<Patient?> = combine(
        activePatientStore.observeActivePatientId(userId),
        patientRepository.observeActivePatients(userId),
    ) { storedId, available ->
        available.firstOrNull { it.patientId == storedId } ?: available.firstOrNull()
    }
}
