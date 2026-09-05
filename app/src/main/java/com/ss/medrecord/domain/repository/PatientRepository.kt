package com.ss.medrecord.domain.repository

import com.ss.medrecord.core.common.DataResult
import com.ss.medrecord.domain.model.Patient
import kotlinx.coroutines.flow.Flow

/**
 * Patient profiles under the signed-in account.
 *
 * Reads observe Room and therefore work offline; writes land in Room first and
 * are pushed to Firestore best-effort, leaving the row PENDING for the Phase 3
 * sync worker when the push cannot complete.
 */
interface PatientRepository {

    fun observeActivePatients(userId: String): Flow<List<Patient>>

    fun observeArchivedPatients(userId: String): Flow<List<Patient>>

    fun observePatient(patientId: String): Flow<Patient?>

    suspend fun getPatient(patientId: String): Patient?

    /** Returns the new patientId. */
    suspend fun createPatient(patient: Patient): DataResult<String>

    suspend fun updatePatient(patient: Patient): DataResult<Unit>

    suspend fun setArchived(patientId: String, archived: Boolean): DataResult<Unit>

    /**
     * Soft delete. The profile disappears from the app immediately but the row
     * survives the configured grace period (spec section 9.6).
     */
    suspend fun deletePatient(patientId: String): DataResult<Unit>

    /** Pulls remote profiles into Room. No-op when offline. */
    suspend fun refreshPatients(userId: String): DataResult<Unit>
}
