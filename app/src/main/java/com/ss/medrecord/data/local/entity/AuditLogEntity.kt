package com.ss.medrecord.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.ss.medrecord.domain.model.AuditAction
import com.ss.medrecord.domain.model.AuditEntityType
import com.ss.medrecord.domain.model.AuditLog
import com.ss.medrecord.domain.model.SyncStatus

/**
 * Local audit trail (spec sections 4.9 and 9.5).
 *
 * No foreign key onto users or patients, and no cascade: the trail has to
 * outlive the records it describes, or deleting a patient would erase the
 * evidence that the patient was deleted. Retention is handled by the
 * compliance sweep, never by referential integrity.
 */
@Entity(
    tableName = "audit_logs",
    indices = [
        Index("user_id"),
        Index("patient_id"),
        Index("sync_status"),
        Index("timestamp"),
    ],
)
data class AuditLogEntity(
    @PrimaryKey
    @ColumnInfo(name = "log_id")
    val logId: String,
    @ColumnInfo(name = "user_id")
    val userId: String,
    @ColumnInfo(name = "patient_id")
    val patientId: String? = null,
    val action: AuditAction,
    @ColumnInfo(name = "entity_type")
    val entityType: AuditEntityType,
    @ColumnInfo(name = "entity_id")
    val entityId: String,
    val timestamp: Long,
    @ColumnInfo(name = "device_id_hash")
    val deviceIdHash: String,
    @ColumnInfo(name = "sync_status")
    val syncStatus: SyncStatus = SyncStatus.PENDING,
)

fun AuditLogEntity.toDomain(): AuditLog = AuditLog(
    logId = logId,
    userId = userId,
    patientId = patientId,
    action = action,
    entityType = entityType,
    entityId = entityId,
    timestamp = timestamp,
    deviceIdHash = deviceIdHash,
)

fun AuditLog.toEntity(syncStatus: SyncStatus = SyncStatus.PENDING): AuditLogEntity =
    AuditLogEntity(
        logId = logId,
        userId = userId,
        patientId = patientId,
        action = action,
        entityType = entityType,
        entityId = entityId,
        timestamp = timestamp,
        deviceIdHash = deviceIdHash,
        syncStatus = syncStatus,
    )
