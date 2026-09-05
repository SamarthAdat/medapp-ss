package com.ss.medrecord.data.repository

import com.ss.medrecord.core.common.DataResult
import com.ss.medrecord.core.common.DispatcherProvider
import com.ss.medrecord.data.local.dao.FacilityDao
import com.ss.medrecord.data.local.entity.toDomain
import com.ss.medrecord.data.local.entity.toEntity
import com.ss.medrecord.data.remote.FacilityRemoteDataSource
import com.ss.medrecord.data.remote.UserRemoteDataSource
import com.ss.medrecord.domain.audit.AuditLogger
import com.ss.medrecord.domain.model.AuditAction
import com.ss.medrecord.domain.model.AuditEntityType
import com.ss.medrecord.domain.model.Facility
import com.ss.medrecord.domain.model.FacilityType
import com.ss.medrecord.domain.model.SyncStatus
import com.ss.medrecord.domain.repository.FacilityRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class FacilityRepositoryImpl @Inject constructor(
    private val facilityDao: FacilityDao,
    private val facilityRemote: FacilityRemoteDataSource,
    private val auditLogger: AuditLogger,
    private val dispatchers: DispatcherProvider,
) : FacilityRepository {

    override fun observeFacilities(userId: String): Flow<List<Facility>> =
        facilityDao.observeFacilities(userId).map { rows -> rows.map { it.toDomain() } }

    override fun observeFacility(facilityId: String): Flow<Facility?> =
        facilityDao.observeFacility(facilityId).map { it?.toDomain() }

    override suspend fun getFacility(facilityId: String): Facility? =
        withContext(dispatchers.io) { facilityDao.getFacility(facilityId)?.toDomain() }

    override suspend fun createFacility(facility: Facility): DataResult<String> =
        withContext(dispatchers.io) {
            DataResult.catching(UserRemoteDataSource::mapFirestoreError) {
                val now = System.currentTimeMillis()
                val record = facility.copy(
                    facilityId = facility.facilityId.ifBlank { UUID.randomUUID().toString() },
                    createdAt = now,
                    updatedAt = now,
                )
                facilityDao.upsert(record.toEntity(syncStatus = SyncStatus.PENDING))
                audit(AuditAction.CREATE, record.facilityId)
                pushBestEffort(record)
                record.facilityId
            }
        }

    override suspend fun updateFacility(facility: Facility): DataResult<Unit> =
        withContext(dispatchers.io) {
            DataResult.catching(UserRemoteDataSource::mapFirestoreError) {
                val record = facility.copy(updatedAt = System.currentTimeMillis())
                facilityDao.upsert(record.toEntity(syncStatus = SyncStatus.PENDING))
                audit(AuditAction.UPDATE, record.facilityId)
                pushBestEffort(record)
            }
        }

    override suspend fun deleteFacility(facilityId: String): DataResult<Unit> =
        withContext(dispatchers.io) {
            DataResult.catching(UserRemoteDataSource::mapFirestoreError) {
                val now = System.currentTimeMillis()
                val record = facilityDao.getFacility(facilityId)?.toDomain()
                facilityDao.softDelete(facilityId, now)
                audit(AuditAction.DELETE, facilityId)
                record?.let { pushBestEffort(it.copy(deletedAt = now, updatedAt = now)) }
                Unit
            }
        }

    override suspend fun findOrCreateByName(
        userId: String,
        name: String,
        type: FacilityType,
    ): DataResult<Facility> = withContext(dispatchers.io) {
        val trimmed = name.trim()
        val existing = facilityDao.findByName(userId, trimmed)
        if (existing != null) return@withContext DataResult.Success(existing.toDomain())

        val now = System.currentTimeMillis()
        val created = Facility(
            facilityId = UUID.randomUUID().toString(),
            userId = userId,
            name = trimmed,
            type = type,
            createdAt = now,
            updatedAt = now,
        )
        when (val result = createFacility(created)) {
            is DataResult.Success -> DataResult.Success(created)
            is DataResult.Error -> result
        }
    }

    override suspend fun refreshFacilities(userId: String): DataResult<Unit> =
        withContext(dispatchers.io) {
            DataResult.catching(UserRemoteDataSource::mapFirestoreError) {
                val remote = facilityRemote.getAll(userId)
                if (remote.isEmpty()) return@catching
                val toApply = remote.filter { candidate ->
                    val local = facilityDao.getFacilityIncludingDeleted(candidate.facilityId)
                    local == null || candidate.updatedAt >= local.updatedAt
                }
                facilityDao.upsertAll(toApply.map { it.toEntity(syncStatus = SyncStatus.SYNCED) })
            }
        }

    private suspend fun audit(action: AuditAction, facilityId: String) {
        auditLogger.log(
            action = action,
            entityType = AuditEntityType.FACILITY,
            entityId = facilityId,
        )
    }

    private suspend fun pushBestEffort(facility: Facility) {
        runCatching { facilityRemote.upsert(facility) }
            .onSuccess {
                facilityDao.upsert(facility.toEntity(syncStatus = SyncStatus.SYNCED))
            }
    }
}
