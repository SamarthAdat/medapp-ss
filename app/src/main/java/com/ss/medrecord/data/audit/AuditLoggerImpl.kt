package com.ss.medrecord.data.audit

import android.util.Log
import com.ss.medrecord.core.device.DeviceIdProvider
import com.ss.medrecord.data.local.dao.AuditLogDao
import com.ss.medrecord.data.local.entity.toDomain
import com.ss.medrecord.data.local.entity.toEntity
import com.ss.medrecord.data.remote.FirebaseAuthDataSource
import com.ss.medrecord.domain.audit.AuditLogger
import com.ss.medrecord.domain.model.AuditAction
import com.ss.medrecord.domain.model.AuditEntityType
import com.ss.medrecord.domain.model.AuditLog
import com.ss.medrecord.domain.model.SyncStatus
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

private const val TAG = "AuditLogger"

/**
 * Writes audit entries to the encrypted local database, marked PENDING so the
 * sync worker pushes them to Firestore where they become tamper-resistant.
 */
@Singleton
class AuditLoggerImpl @Inject constructor(
    private val auditLogDao: AuditLogDao,
    private val authDataSource: FirebaseAuthDataSource,
    private val deviceIdProvider: DeviceIdProvider,
) : AuditLogger {

    override suspend fun log(
        action: AuditAction,
        entityType: AuditEntityType,
        entityId: String,
        patientId: String?,
    ) {
        val userId = authDataSource.currentUserId
        if (userId == null) {
            // Nothing to attribute the action to. Signed-out code paths do not
            // touch patient data, so there is nothing auditable to lose here.
            Log.w(TAG, "Skipping audit entry for $action $entityType: no session")
            return
        }

        val entry = AuditLog(
            logId = UUID.randomUUID().toString(),
            userId = userId,
            patientId = patientId,
            action = action,
            entityType = entityType,
            entityId = entityId,
            timestamp = System.currentTimeMillis(),
            deviceIdHash = deviceIdProvider.deviceIdHash,
        )

        // The caller's operation has already succeeded by this point; a failure
        // to record it must not be turned into a failure of the operation.
        runCatching { auditLogDao.insert(entry.toEntity(syncStatus = SyncStatus.PENDING)) }
            .onFailure { Log.e(TAG, "Failed to write audit entry for $action $entityType", it) }
    }

    override fun observeRecent(userId: String): Flow<List<AuditLog>> =
        auditLogDao.observeRecent(userId).map { rows -> rows.map { it.toDomain() } }

    override fun observeForPatient(userId: String, patientId: String): Flow<List<AuditLog>> =
        auditLogDao.observeForPatient(userId, patientId).map { rows -> rows.map { it.toDomain() } }
}
