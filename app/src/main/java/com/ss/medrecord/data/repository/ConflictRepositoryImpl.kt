package com.ss.medrecord.data.repository

import com.ss.medrecord.core.common.AppError
import com.ss.medrecord.core.common.DataResult
import com.ss.medrecord.core.common.DispatcherProvider
import com.ss.medrecord.domain.audit.AuditLogger
import com.ss.medrecord.domain.model.AuditAction
import com.ss.medrecord.domain.model.AuditEntityType
import com.ss.medrecord.domain.model.ConflictEntityType
import com.ss.medrecord.domain.model.ConflictResolution
import com.ss.medrecord.domain.model.SyncConflict
import com.ss.medrecord.domain.repository.ConflictRepository
import com.ss.medrecord.domain.sync.ConflictSource
import com.ss.medrecord.domain.sync.SyncManager
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Fans conflict handling out over the [ConflictSource] set, so this class never
 * learns what a patient or a visit is.
 *
 * Resolving is audited as an UPDATE. Choosing which of two versions of a
 * medical record survives is a change to that record, and an audit trail that
 * recorded the original edits but not the decision between them would have a
 * hole exactly where a reviewer would look.
 */
@Singleton
class ConflictRepositoryImpl @Inject constructor(
    private val sources: Set<@JvmSuppressWildcards ConflictSource>,
    private val auditLogger: AuditLogger,
    private val syncManager: SyncManager,
    private val dispatchers: DispatcherProvider,
) : ConflictRepository {

    private val byType: Map<ConflictEntityType, ConflictSource> =
        sources.associateBy { it.entityType }

    override suspend fun conflicts(userId: String): DataResult<List<SyncConflict>> =
        withContext(dispatchers.io) {
            DataResult.catching({ AppError.Database(it) }) {
                sources.flatMap { it.conflicts(userId) }
                    // Oldest first: the longest-stuck record is the one most
                    // likely to be silently missing from another device.
                    .sortedBy { it.localUpdatedAt }
            }
        }

    override suspend fun resolve(
        userId: String,
        conflict: SyncConflict,
        resolution: ConflictResolution,
    ): DataResult<Unit> = withContext(dispatchers.io) {
        DataResult.catching({ AppError.Database(it) }) {
            val source = byType[conflict.entityType]
                ?: throw IllegalStateException("No source for ${conflict.entityType}")

            when (resolution) {
                ConflictResolution.KEEP_LOCAL -> source.keepLocal(conflict.entityId)
                ConflictResolution.KEEP_REMOTE -> source.keepRemote(userId, conflict.entityId)
            }

            auditLogger.log(
                action = AuditAction.UPDATE,
                entityType = conflict.entityType.toAuditType(),
                entityId = conflict.entityId,
            )

            // Keeping the local copy puts the row back in the outbox; pushing it
            // now is what makes the resolution take effect rather than waiting
            // for the next periodic pass.
            if (resolution == ConflictResolution.KEEP_LOCAL) {
                syncManager.syncNow(expedited = true)
            }
        }
    }
}

private fun ConflictEntityType.toAuditType(): AuditEntityType = when (this) {
    ConflictEntityType.PATIENT -> AuditEntityType.PATIENT
    ConflictEntityType.FACILITY -> AuditEntityType.FACILITY
    ConflictEntityType.VISIT -> AuditEntityType.VISIT
    ConflictEntityType.REPORT -> AuditEntityType.REPORT
    ConflictEntityType.MEDICINE -> AuditEntityType.MEDICINE
}
