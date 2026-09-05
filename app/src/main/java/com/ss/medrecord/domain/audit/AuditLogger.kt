package com.ss.medrecord.domain.audit

import com.ss.medrecord.domain.model.AuditAction
import com.ss.medrecord.domain.model.AuditEntityType
import com.ss.medrecord.domain.model.AuditLog
import kotlinx.coroutines.flow.Flow

/**
 * Records sensitive actions to the immutable trail (spec section 9.5).
 *
 * Logging never fails a caller. If the trail cannot be written the user's
 * action still succeeds - refusing to save a medical record because an audit
 * row could not be inserted would be worse for the patient than a gap in the
 * log, and the gap is recoverable while the lost record is not.
 */
interface AuditLogger {

    suspend fun log(
        action: AuditAction,
        entityType: AuditEntityType,
        entityId: String,
        patientId: String? = null,
    )

    fun observeRecent(userId: String): Flow<List<AuditLog>>

    fun observeForPatient(userId: String, patientId: String): Flow<List<AuditLog>>
}
