package com.ss.medrecord.data.remote

import com.google.firebase.firestore.FirebaseFirestore
import com.ss.medrecord.domain.model.AuditLog
import kotlinx.coroutines.tasks.await
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Ships audit entries to Firestore, where the security rules make them
 * append-only and therefore tamper-resistant (spec section 9.5).
 *
 *   users/{userId}/auditLogs/{logId}
 *
 * There is no read path here. The remote trail is evidence for compliance
 * review, not something the app displays; the local copy covers the in-app
 * history and is discarded once it has been pushed.
 */
@Singleton
class AuditRemoteDataSource @Inject constructor(
    private val firestore: FirebaseFirestore,
) {

    /** Writes up to [BATCH_LIMIT] entries atomically. */
    suspend fun insertAll(entries: List<AuditLog>) {
        if (entries.isEmpty()) return
        require(entries.size <= BATCH_LIMIT) { "Batch of ${entries.size} exceeds $BATCH_LIMIT" }

        val batch = firestore.batch()
        entries.forEach { entry ->
            val ref = firestore.collection(UserRemoteDataSource.USERS)
                .document(entry.userId)
                .collection(AUDIT_LOGS)
                .document(entry.logId)
            batch.set(ref, entry.toFirestoreMap())
        }
        batch.commit().await()
    }

    private fun AuditLog.toFirestoreMap(): Map<String, Any?> = mapOf(
        FIELD_LOG_ID to logId,
        FIELD_USER_ID to userId,
        FIELD_PATIENT_ID to patientId,
        FIELD_ACTION to action.name,
        FIELD_ENTITY_TYPE to entityType.name,
        FIELD_ENTITY_ID to entityId,
        FIELD_TIMESTAMP to timestamp,
        FIELD_DEVICE_ID_HASH to deviceIdHash,
    )

    companion object {
        const val AUDIT_LOGS = "auditLogs"

        /** Firestore caps a write batch at 500 operations. */
        const val BATCH_LIMIT = 400

        private const val FIELD_LOG_ID = "logId"
        private const val FIELD_USER_ID = "userId"
        private const val FIELD_PATIENT_ID = "patientId"
        private const val FIELD_ACTION = "action"
        private const val FIELD_ENTITY_TYPE = "entityType"
        private const val FIELD_ENTITY_ID = "entityId"
        private const val FIELD_TIMESTAMP = "timestamp"
        private const val FIELD_DEVICE_ID_HASH = "deviceIdHash"
    }
}
