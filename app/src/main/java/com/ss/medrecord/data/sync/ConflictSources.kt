package com.ss.medrecord.data.sync

import com.ss.medrecord.data.local.dao.FacilityDao
import com.ss.medrecord.data.local.dao.MedicineDao
import com.ss.medrecord.data.local.dao.PatientDao
import com.ss.medrecord.data.local.dao.ReportDao
import com.ss.medrecord.data.local.dao.VisitDao
import com.ss.medrecord.data.local.entity.toEntity
import com.ss.medrecord.data.remote.FacilityRemoteDataSource
import com.ss.medrecord.data.remote.MedicineRemoteDataSource
import com.ss.medrecord.data.remote.PatientRemoteDataSource
import com.ss.medrecord.data.remote.ReportRemoteDataSource
import com.ss.medrecord.data.remote.VisitRemoteDataSource
import com.ss.medrecord.domain.model.ConflictEntityType
import com.ss.medrecord.domain.model.SyncConflict
import com.ss.medrecord.domain.model.SyncStatus
import com.ss.medrecord.domain.sync.ConflictSource
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import javax.inject.Inject
import javax.inject.Singleton

/**
 * The five record types that can end up parked in CONFLICT.
 *
 * Kept in one file rather than five: each is forty lines of the same shape, and
 * the thing a reviewer needs to check - that every type resolves both ways and
 * bumps its timestamp on keepLocal - is only checkable if they sit side by
 * side.
 *
 * A note on [ConflictSource.keepRemote] throughout: it re-reads the collection
 * rather than fetching one document. Conflict resolution is rare and
 * user-initiated, the payloads are metadata, and the alternative is duplicating
 * five Firestore document mappers that already exist inside each getAll. If
 * conflicts ever stop being rare, this is the first thing to change.
 */

/** Bumped so the resolved row wins the next comparison rather than re-parking. */
private fun now() = System.currentTimeMillis()

private val DATE_FORMAT: DateTimeFormatter = DateTimeFormatter.ofPattern("d MMM yyyy")

private fun epochDayLabel(epochDay: Long): String =
    LocalDate.ofEpochDay(epochDay).format(DATE_FORMAT)

/**
 * Remote timestamps by id, or an empty map if the server could not be reached.
 *
 * Failure is not an error here: the conflict list is still worth showing
 * offline, and an absent timestamp renders as "server copy unknown" rather than
 * as a fabricated one.
 */
private fun <T> remoteTimestamps(
    fetch: Result<List<T>>,
    key: (T) -> Pair<String, Long>,
): Map<String, Long> = fetch.getOrNull()?.associate(key).orEmpty()

@Singleton
class PatientConflictSource @Inject constructor(
    private val patientDao: PatientDao,
    private val patientRemote: PatientRemoteDataSource,
) : ConflictSource {

    override val entityType = ConflictEntityType.PATIENT

    override suspend fun conflicts(userId: String): List<SyncConflict> {
        val parked = patientDao.getConflicts().filter { it.userId == userId }
        if (parked.isEmpty()) return emptyList()
        val remoteById = remoteTimestamps(runCatching { patientRemote.getAll(userId) }) {
            it.patientId to it.updatedAt
        }

        return parked.map { entity ->
            SyncConflict(
                entityType = entityType,
                entityId = entity.patientId,
                label = entity.name,
                detail = entity.relationship.label,
                localUpdatedAt = entity.updatedAt,
                remoteUpdatedAt = remoteById[entity.patientId],
            )
        }
    }

    override suspend fun keepLocal(entityId: String) {
        val local = patientDao.getPatientIncludingDeleted(entityId) ?: return
        patientDao.upsert(local.copy(updatedAt = now(), syncStatus = SyncStatus.PENDING))
    }

    override suspend fun keepRemote(userId: String, entityId: String) {
        val remote = patientRemote.getAll(userId).firstOrNull { it.patientId == entityId } ?: return
        patientDao.upsert(remote.toEntity(syncStatus = SyncStatus.SYNCED))
    }
}

@Singleton
class FacilityConflictSource @Inject constructor(
    private val facilityDao: FacilityDao,
    private val facilityRemote: FacilityRemoteDataSource,
) : ConflictSource {

    override val entityType = ConflictEntityType.FACILITY

    override suspend fun conflicts(userId: String): List<SyncConflict> {
        val parked = facilityDao.getConflicts().filter { it.userId == userId }
        if (parked.isEmpty()) return emptyList()
        val remoteById = remoteTimestamps(runCatching { facilityRemote.getAll(userId) }) {
            it.facilityId to it.updatedAt
        }

        return parked.map { entity ->
            SyncConflict(
                entityType = entityType,
                entityId = entity.facilityId,
                label = entity.name,
                detail = entity.type.label,
                localUpdatedAt = entity.updatedAt,
                remoteUpdatedAt = remoteById[entity.facilityId],
            )
        }
    }

    override suspend fun keepLocal(entityId: String) {
        val local = facilityDao.getFacilityIncludingDeleted(entityId) ?: return
        facilityDao.upsert(local.copy(updatedAt = now(), syncStatus = SyncStatus.PENDING))
    }

    override suspend fun keepRemote(userId: String, entityId: String) {
        val remote = facilityRemote.getAll(userId)
            .firstOrNull { it.facilityId == entityId } ?: return
        facilityDao.upsert(remote.toEntity(syncStatus = SyncStatus.SYNCED))
    }
}

@Singleton
class VisitConflictSource @Inject constructor(
    private val visitDao: VisitDao,
    private val visitRemote: VisitRemoteDataSource,
) : ConflictSource {

    override val entityType = ConflictEntityType.VISIT

    override suspend fun conflicts(userId: String): List<SyncConflict> {
        val parked = visitDao.getConflicts().filter { it.userId == userId }
        if (parked.isEmpty()) return emptyList()
        val remoteById = remoteTimestamps(runCatching { visitRemote.getAll(userId) }) {
            it.visitId to it.updatedAt
        }

        return parked.map { entity ->
            SyncConflict(
                entityType = entityType,
                entityId = entity.visitId,
                label = epochDayLabel(entity.visitDateEpochDay),
                detail = entity.doctorName?.takeIf { it.isNotBlank() },
                localUpdatedAt = entity.updatedAt,
                remoteUpdatedAt = remoteById[entity.visitId],
            )
        }
    }

    override suspend fun keepLocal(entityId: String) {
        val local = visitDao.getVisitIncludingDeleted(entityId) ?: return
        visitDao.upsert(local.copy(updatedAt = now(), syncStatus = SyncStatus.PENDING))
    }

    override suspend fun keepRemote(userId: String, entityId: String) {
        val remote = visitRemote.getAll(userId).firstOrNull { it.visitId == entityId } ?: return
        visitDao.upsert(remote.toEntity(syncStatus = SyncStatus.SYNCED))
    }
}

@Singleton
class ReportConflictSource @Inject constructor(
    private val reportDao: ReportDao,
    private val reportRemote: ReportRemoteDataSource,
) : ConflictSource {

    override val entityType = ConflictEntityType.REPORT

    override suspend fun conflicts(userId: String): List<SyncConflict> {
        val parked = reportDao.getConflicts().filter { it.userId == userId }
        if (parked.isEmpty()) return emptyList()
        val remoteById = remoteTimestamps(runCatching { reportRemote.getAll(userId) }) {
            it.reportId to it.updatedAt
        }

        return parked.map { entity ->
            SyncConflict(
                entityType = entityType,
                entityId = entity.reportId,
                label = entity.fileName,
                detail = entity.fileType.label,
                localUpdatedAt = entity.updatedAt,
                remoteUpdatedAt = remoteById[entity.reportId],
            )
        }
    }

    override suspend fun keepLocal(entityId: String) {
        val local = reportDao.getReportIncludingDeleted(entityId) ?: return
        reportDao.upsert(local.copy(updatedAt = now(), syncStatus = SyncStatus.PENDING))
    }

    override suspend fun keepRemote(userId: String, entityId: String) {
        val remote = reportRemote.getAll(userId).firstOrNull { it.reportId == entityId } ?: return
        val local = reportDao.getReportIncludingDeleted(entityId)
        reportDao.upsert(
            remote.toEntity(syncStatus = SyncStatus.SYNCED).copy(
                // Upload state and the cached file path belong to this device
                // and are not the server's to overwrite - the same rule the
                // report syncer follows on an ordinary pull.
                localFilePath = local?.localFilePath,
                uploadStatus = local?.uploadStatus ?: remote.uploadStatus,
            ),
        )
    }
}

@Singleton
class MedicineConflictSource @Inject constructor(
    private val medicineDao: MedicineDao,
    private val medicineRemote: MedicineRemoteDataSource,
) : ConflictSource {

    override val entityType = ConflictEntityType.MEDICINE

    override suspend fun conflicts(userId: String): List<SyncConflict> {
        val parked = medicineDao.getConflicts().filter { it.userId == userId }
        if (parked.isEmpty()) return emptyList()
        val remoteById = remoteTimestamps(runCatching { medicineRemote.getAll(userId) }) {
            it.medicineId to it.updatedAt
        }

        return parked.map { entity ->
            SyncConflict(
                entityType = entityType,
                entityId = entity.medicineId,
                label = entity.name,
                detail = entity.dosage?.takeIf { it.isNotBlank() },
                localUpdatedAt = entity.updatedAt,
                remoteUpdatedAt = remoteById[entity.medicineId],
            )
        }
    }

    override suspend fun keepLocal(entityId: String) {
        val local = medicineDao.getMedicineIncludingDeleted(entityId) ?: return
        medicineDao.upsert(local.copy(updatedAt = now(), syncStatus = SyncStatus.PENDING))
    }

    override suspend fun keepRemote(userId: String, entityId: String) {
        val remote = medicineRemote.getAll(userId)
            .firstOrNull { it.medicineId == entityId } ?: return
        medicineDao.upsert(remote.toEntity(syncStatus = SyncStatus.SYNCED))
    }
}
