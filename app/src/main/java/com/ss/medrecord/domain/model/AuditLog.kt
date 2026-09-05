package com.ss.medrecord.domain.model

/**
 * An immutable record that something happened to patient data (spec section
 * 4.9). Written for every sensitive action and never updated or deleted, which
 * is what makes the trail worth anything under HIPAA's six-year retention rule.
 *
 * Audit entries deliberately record *that* a record was touched, never its
 * contents: an audit trail that copies clinical values just doubles the amount
 * of health data to protect.
 */
data class AuditLog(
    val logId: String,
    val userId: String,
    val patientId: String? = null,
    val action: AuditAction,
    val entityType: AuditEntityType,
    val entityId: String,
    val timestamp: Long,
    /**
     * A hash of a per-install random identifier - enough to tell devices apart
     * in a trail, not enough to identify the hardware. Never the Android ID.
     */
    val deviceIdHash: String,
)

enum class AuditAction {
    VIEW,
    CREATE,
    UPDATE,
    DELETE,
    EXPORT,
    SHARE,
}

/** The kinds of record an audit entry can refer to. Grows with each phase. */
enum class AuditEntityType {
    USER,
    CONSENT,
    PATIENT,
    FACILITY,
    VISIT,
    REPORT,
    MEDICINE,
    REMINDER,
}
