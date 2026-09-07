package com.ss.medrecord.data.repository

import com.ss.medrecord.core.common.DataResult
import com.ss.medrecord.core.common.DispatcherProvider
import com.ss.medrecord.data.local.dao.MedicineDao
import com.ss.medrecord.data.local.dao.MedicineWithContextRow
import com.ss.medrecord.data.local.entity.toDomain
import com.ss.medrecord.data.local.entity.toEntity
import com.ss.medrecord.data.remote.MedicineRemoteDataSource
import com.ss.medrecord.data.remote.UserRemoteDataSource
import com.ss.medrecord.domain.audit.AuditLogger
import com.ss.medrecord.domain.model.AuditAction
import com.ss.medrecord.domain.model.AuditEntityType
import com.ss.medrecord.domain.model.Medicine
import com.ss.medrecord.domain.model.MedicineWithContext
import com.ss.medrecord.domain.model.SyncStatus
import com.ss.medrecord.domain.reminder.ReminderScheduler
import com.ss.medrecord.domain.repository.MedicineRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Every write here ends with [ReminderScheduler.requestRebuild]. That is the
 * single rule that keeps the alarm schedule honest: a medicine's times, its
 * date range, whether it is paused, and whether it exists at all are the only
 * inputs to the schedule, and all four change through this class.
 *
 * The rebuild is requested rather than performed, so saving a medicine returns
 * as soon as the row is in Room. A rebuild that is slow, or that fails because
 * the device denied exact alarms, must not be able to fail the save - the
 * record is the thing that matters, the reminder is a convenience on top of it.
 */
@Singleton
class MedicineRepositoryImpl @Inject constructor(
    private val medicineDao: MedicineDao,
    private val medicineRemote: MedicineRemoteDataSource,
    private val auditLogger: AuditLogger,
    private val reminderScheduler: ReminderScheduler,
    private val dispatchers: DispatcherProvider,
) : MedicineRepository {

    override fun observeMedicines(userId: String): Flow<List<MedicineWithContext>> =
        medicineDao.observeMedicinesWithContext(userId).map { rows -> rows.map { it.toDomain() } }

    override fun observeMedicinesForPatient(patientId: String): Flow<List<Medicine>> =
        medicineDao.observeMedicinesForPatient(patientId).map { rows -> rows.map { it.toDomain() } }

    override fun observeMedicinesForVisit(visitId: String): Flow<List<Medicine>> =
        medicineDao.observeMedicinesForVisit(visitId).map { rows -> rows.map { it.toDomain() } }

    override fun observeMedicine(medicineId: String): Flow<Medicine?> =
        medicineDao.observeMedicine(medicineId).map { it?.toDomain() }

    override fun observeActiveCountForPatient(patientId: String): Flow<Int> =
        medicineDao.observeActiveCountForPatient(patientId)

    override fun observeCountForVisit(visitId: String): Flow<Int> =
        medicineDao.observeCountForVisit(visitId)

    override suspend fun getMedicine(medicineId: String): Medicine? =
        withContext(dispatchers.io) { medicineDao.getMedicine(medicineId)?.toDomain() }

    override suspend fun createMedicine(medicine: Medicine): DataResult<String> =
        withContext(dispatchers.io) {
            DataResult.catching(UserRemoteDataSource::mapFirestoreError) {
                val now = System.currentTimeMillis()
                val record = medicine.copy(
                    medicineId = medicine.medicineId.ifBlank { UUID.randomUUID().toString() },
                    createdAt = now,
                    updatedAt = now,
                )
                medicineDao.upsert(record.toEntity(syncStatus = SyncStatus.PENDING))
                audit(AuditAction.CREATE, record.medicineId, record.patientId)
                reminderScheduler.requestRebuild()
                pushBestEffort(record)
                record.medicineId
            }
        }

    override suspend fun updateMedicine(medicine: Medicine): DataResult<Unit> =
        withContext(dispatchers.io) {
            DataResult.catching(UserRemoteDataSource::mapFirestoreError) {
                val record = medicine.copy(updatedAt = System.currentTimeMillis())
                medicineDao.upsert(record.toEntity(syncStatus = SyncStatus.PENDING))
                audit(AuditAction.UPDATE, record.medicineId, record.patientId)
                reminderScheduler.requestRebuild()
                pushBestEffort(record)
            }
        }

    override suspend fun setActive(medicineId: String, isActive: Boolean): DataResult<Unit> =
        withContext(dispatchers.io) {
            DataResult.catching(UserRemoteDataSource::mapFirestoreError) {
                val now = System.currentTimeMillis()
                medicineDao.setActive(medicineId, isActive, now)
                val record = medicineDao.getMedicine(medicineId)?.toDomain() ?: return@catching
                audit(AuditAction.UPDATE, medicineId, record.patientId)
                reminderScheduler.requestRebuild()
                pushBestEffort(record)
            }
        }

    override suspend fun deleteMedicine(medicineId: String): DataResult<Unit> =
        withContext(dispatchers.io) {
            DataResult.catching(UserRemoteDataSource::mapFirestoreError) {
                val now = System.currentTimeMillis()
                // Read before the soft delete: the query filters deleted rows.
                val record = medicineDao.getMedicine(medicineId)?.toDomain()
                medicineDao.softDelete(medicineId, now)
                audit(AuditAction.DELETE, medicineId, record?.patientId)
                reminderScheduler.requestRebuild()
                record?.let { pushBestEffort(it.copy(deletedAt = now, updatedAt = now)) }
                Unit
            }
        }

    override suspend fun refreshMedicines(userId: String): DataResult<Unit> =
        withContext(dispatchers.io) {
            DataResult.catching(UserRemoteDataSource::mapFirestoreError) {
                val remote = medicineRemote.getAll(userId)
                if (remote.isEmpty()) return@catching
                val toApply = remote.filter { candidate ->
                    val local = medicineDao.getMedicineIncludingDeleted(candidate.medicineId)
                    local == null || candidate.updatedAt >= local.updatedAt
                }
                if (toApply.isEmpty()) return@catching
                medicineDao.upsertAll(toApply.map { it.toEntity(syncStatus = SyncStatus.SYNCED) })
                reminderScheduler.requestRebuild()
            }
        }

    private suspend fun audit(action: AuditAction, medicineId: String, patientId: String?) {
        auditLogger.log(
            action = action,
            entityType = AuditEntityType.MEDICINE,
            entityId = medicineId,
            patientId = patientId,
        )
    }

    private suspend fun pushBestEffort(medicine: Medicine) {
        runCatching { medicineRemote.upsert(medicine) }
            .onSuccess { medicineDao.upsert(medicine.toEntity(syncStatus = SyncStatus.SYNCED)) }
    }
}

fun MedicineWithContextRow.toDomain(): MedicineWithContext = MedicineWithContext(
    medicine = medicine.toDomain(),
    patientName = patientName,
    facilityName = facilityName,
    visitDateEpochDay = visitDateEpochDay,
)
