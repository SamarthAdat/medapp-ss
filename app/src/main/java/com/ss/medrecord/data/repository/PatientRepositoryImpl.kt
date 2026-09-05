package com.ss.medrecord.data.repository

import com.ss.medrecord.core.common.DataResult
import com.ss.medrecord.core.common.DispatcherProvider
import com.ss.medrecord.data.local.dao.PatientDao
import com.ss.medrecord.data.local.entity.toDomain
import com.ss.medrecord.data.local.entity.toEntity
import com.ss.medrecord.data.remote.PatientRemoteDataSource
import com.ss.medrecord.data.remote.UserRemoteDataSource
import com.ss.medrecord.domain.audit.AuditLogger
import com.ss.medrecord.domain.model.AuditAction
import com.ss.medrecord.domain.model.AuditEntityType
import com.ss.medrecord.domain.model.Patient
import com.ss.medrecord.domain.model.SyncStatus
import com.ss.medrecord.domain.repository.PatientRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PatientRepositoryImpl @Inject constructor(
    private val patientDao: PatientDao,
    private val patientRemote: PatientRemoteDataSource,
    private val auditLogger: AuditLogger,
    private val dispatchers: DispatcherProvider,
) : PatientRepository {

    override fun observeActivePatients(userId: String): Flow<List<Patient>> =
        patientDao.observeActivePatients(userId).map { rows -> rows.map { it.toDomain() } }

    override fun observeArchivedPatients(userId: String): Flow<List<Patient>> =
        patientDao.observeArchivedPatients(userId).map { rows -> rows.map { it.toDomain() } }

    override fun observePatient(patientId: String): Flow<Patient?> =
        patientDao.observePatient(patientId).map { it?.toDomain() }

    override suspend fun getPatient(patientId: String): Patient? =
        withContext(dispatchers.io) { patientDao.getPatient(patientId)?.toDomain() }

    override suspend fun createPatient(patient: Patient): DataResult<String> =
        withContext(dispatchers.io) {
            DataResult.catching(UserRemoteDataSource::mapFirestoreError) {
                val now = System.currentTimeMillis()
                val record = patient.copy(
                    // Generated on device rather than by Firestore, so a profile
                    // created offline already has its permanent id and anything
                    // referencing it later does not need rewriting on sync.
                    patientId = patient.patientId.ifBlank { UUID.randomUUID().toString() },
                    createdAt = now,
                    updatedAt = now,
                )
                patientDao.upsert(record.toEntity(syncStatus = SyncStatus.PENDING))
                audit(AuditAction.CREATE, record.patientId)
                pushBestEffort(record)
                record.patientId
            }
        }

    override suspend fun updatePatient(patient: Patient): DataResult<Unit> =
        withContext(dispatchers.io) {
            DataResult.catching(UserRemoteDataSource::mapFirestoreError) {
                val record = patient.copy(updatedAt = System.currentTimeMillis())
                patientDao.upsert(record.toEntity(syncStatus = SyncStatus.PENDING))
                audit(AuditAction.UPDATE, record.patientId)
                pushBestEffort(record)
            }
        }

    override suspend fun setArchived(patientId: String, archived: Boolean): DataResult<Unit> =
        withContext(dispatchers.io) {
            DataResult.catching(UserRemoteDataSource::mapFirestoreError) {
                val now = System.currentTimeMillis()
                patientDao.setArchived(patientId, archived, now)
                audit(AuditAction.UPDATE, patientId)
                patientDao.getPatient(patientId)?.toDomain()?.let { pushBestEffort(it) }
                Unit
            }
        }

    override suspend fun deletePatient(patientId: String): DataResult<Unit> =
        withContext(dispatchers.io) {
            DataResult.catching(UserRemoteDataSource::mapFirestoreError) {
                val now = System.currentTimeMillis()
                // The DAO read has to happen before the soft delete, because the
                // observe/get queries deliberately exclude deleted rows.
                val record = patientDao.getPatient(patientId)?.toDomain()
                patientDao.softDelete(patientId, now)
                // Deleting a patient is one of the actions section 9.5 names
                // explicitly; the entry outlives the record it describes.
                audit(AuditAction.DELETE, patientId)
                record?.let { pushBestEffort(it.copy(deletedAt = now, updatedAt = now)) }
                Unit
            }
        }

    override suspend fun refreshPatients(userId: String): DataResult<Unit> =
        withContext(dispatchers.io) {
            DataResult.catching(UserRemoteDataSource::mapFirestoreError) {
                val remote = patientRemote.getAll(userId)
                if (remote.isEmpty()) return@catching

                // Last-write-wins on updatedAt (spec section 6.5). A local row
                // edited offline is newer than the server copy and is left alone
                // for the sync worker to push, rather than being overwritten.
                val toApply = remote.filter { candidate ->
                    val local = patientDao.getPatient(candidate.patientId)
                    local == null || candidate.updatedAt >= local.updatedAt
                }
                patientDao.upsertAll(toApply.map { it.toEntity(syncStatus = SyncStatus.SYNCED) })
            }
        }

    private suspend fun audit(action: AuditAction, patientId: String) {
        auditLogger.log(
            action = action,
            entityType = AuditEntityType.PATIENT,
            entityId = patientId,
            patientId = patientId,
        )
    }

    /**
     * Pushes to Firestore without letting a network failure fail the write the
     * user just made. The row stays PENDING and the sync worker retries.
     */
    private suspend fun pushBestEffort(patient: Patient) {
        runCatching { patientRemote.upsert(patient) }
            .onSuccess {
                patientDao.upsert(patient.toEntity(syncStatus = SyncStatus.SYNCED))
            }
    }
}
