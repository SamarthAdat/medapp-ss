package com.ss.medrecord.data.repository

import com.ss.medrecord.core.common.DataResult
import com.ss.medrecord.core.common.DispatcherProvider
import com.ss.medrecord.data.local.dao.VisitDao
import com.ss.medrecord.data.local.dao.VisitWithContextRow
import com.ss.medrecord.data.local.dao.VisitWithFacilityRow
import com.ss.medrecord.data.local.entity.toDomain
import com.ss.medrecord.data.local.entity.toEntity
import com.ss.medrecord.data.remote.UserRemoteDataSource
import com.ss.medrecord.data.remote.VisitRemoteDataSource
import com.ss.medrecord.domain.audit.AuditLogger
import com.ss.medrecord.domain.model.AuditAction
import com.ss.medrecord.domain.model.AuditEntityType
import com.ss.medrecord.domain.model.SyncStatus
import com.ss.medrecord.domain.model.Visit
import com.ss.medrecord.domain.model.VisitWithContext
import com.ss.medrecord.domain.model.VisitWithFacility
import com.ss.medrecord.domain.repository.VisitRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class VisitRepositoryImpl @Inject constructor(
    private val visitDao: VisitDao,
    private val visitRemote: VisitRemoteDataSource,
    private val auditLogger: AuditLogger,
    private val dispatchers: DispatcherProvider,
) : VisitRepository {

    override fun observeVisitsForPatient(patientId: String): Flow<List<VisitWithFacility>> =
        visitDao.observeVisitsForPatient(patientId).map { rows -> rows.map { it.toDomain() } }

    override fun observeVisit(visitId: String): Flow<VisitWithFacility?> =
        visitDao.observeVisit(visitId).map { it?.toDomain() }

    override fun observeVisitsAtFacility(facilityId: String): Flow<List<Visit>> =
        visitDao.observeVisitsAtFacility(facilityId).map { rows -> rows.map { it.toDomain() } }

    override fun observeVisitsWithContext(userId: String): Flow<List<VisitWithContext>> =
        visitDao.observeVisitsWithContext(userId).map { rows -> rows.map { it.toDomain() } }

    override fun observeVisitCount(patientId: String): Flow<Int> =
        visitDao.observeVisitCount(patientId)

    override suspend fun getVisit(visitId: String): Visit? =
        withContext(dispatchers.io) { visitDao.getVisit(visitId)?.toDomain() }

    override suspend fun createVisit(visit: Visit): DataResult<String> =
        withContext(dispatchers.io) {
            DataResult.catching(UserRemoteDataSource::mapFirestoreError) {
                val now = System.currentTimeMillis()
                val record = visit.copy(
                    visitId = visit.visitId.ifBlank { UUID.randomUUID().toString() },
                    createdAt = now,
                    updatedAt = now,
                )
                visitDao.upsert(record.toEntity(syncStatus = SyncStatus.PENDING))
                audit(AuditAction.CREATE, record.visitId, record.patientId)
                pushBestEffort(record)
                record.visitId
            }
        }

    override suspend fun updateVisit(visit: Visit): DataResult<Unit> =
        withContext(dispatchers.io) {
            DataResult.catching(UserRemoteDataSource::mapFirestoreError) {
                val record = visit.copy(updatedAt = System.currentTimeMillis())
                visitDao.upsert(record.toEntity(syncStatus = SyncStatus.PENDING))
                audit(AuditAction.UPDATE, record.visitId, record.patientId)
                pushBestEffort(record)
            }
        }

    override suspend fun deleteVisit(visitId: String): DataResult<Unit> =
        withContext(dispatchers.io) {
            DataResult.catching(UserRemoteDataSource::mapFirestoreError) {
                val now = System.currentTimeMillis()
                // Read before the soft delete: the query filters deleted rows.
                val record = visitDao.getVisit(visitId)?.toDomain()
                visitDao.softDelete(visitId, now)
                audit(AuditAction.DELETE, visitId, record?.patientId)
                record?.let { pushBestEffort(it.copy(deletedAt = now, updatedAt = now)) }
                Unit
            }
        }

    override suspend fun refreshVisits(userId: String): DataResult<Unit> =
        withContext(dispatchers.io) {
            DataResult.catching(UserRemoteDataSource::mapFirestoreError) {
                val remote = visitRemote.getAll(userId)
                if (remote.isEmpty()) return@catching
                val toApply = remote.filter { candidate ->
                    val local = visitDao.getVisitIncludingDeleted(candidate.visitId)
                    local == null || candidate.updatedAt >= local.updatedAt
                }
                visitDao.upsertAll(toApply.map { it.toEntity(syncStatus = SyncStatus.SYNCED) })
            }
        }

    private suspend fun audit(action: AuditAction, visitId: String, patientId: String?) {
        auditLogger.log(
            action = action,
            entityType = AuditEntityType.VISIT,
            entityId = visitId,
            patientId = patientId,
        )
    }

    private suspend fun pushBestEffort(visit: Visit) {
        runCatching { visitRemote.upsert(visit) }
            .onSuccess { visitDao.upsert(visit.toEntity(syncStatus = SyncStatus.SYNCED)) }
    }
}

/** Maps the joined row, tolerating a facility that has not synced down yet. */
fun VisitWithFacilityRow.toDomain(): VisitWithFacility = VisitWithFacility(
    visit = visit.toDomain(),
    facility = facility?.takeIf { it.facilityId.isNotBlank() }?.toDomain(),
)

fun VisitWithContextRow.toDomain(): VisitWithContext = VisitWithContext(
    visit = visit.toDomain(),
    facilityName = facilityName,
    patientName = patientName,
)
