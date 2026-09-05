package com.ss.medrecord.domain.model

/**
 * An immutable record that the user accepted a specific version of a specific
 * consent text (spec sections 4.8 and 9.5). Never updated or deleted - a new
 * version produces a new row, which is what makes consent history auditable.
 */
data class ConsentRecord(
    val consentId: String,
    val userId: String,
    val consentType: ConsentType,
    val version: Int,
    val acceptedAt: Long,
    /**
     * Optional hashed source IP. Left null by the app: resolving the caller IP
     * would mean handing the user address to a third-party echo service, which
     * is exactly the kind of leak section 9.1 rules out. If ABDM later requires
     * it, it is populated server-side where the IP is already known.
     */
    val ipHash: String? = null,
)

/** The distinct acknowledgements the user must give (spec section 4.8). */
enum class ConsentType {
    /** Core permission to process personal health data for record-keeping. */
    DATA_PROCESSING,

    /** Acknowledgement of the HIPAA-aligned handling notice. */
    HIPAA_ACK,

    /** Acknowledgement of DPDP Act 2023 / ABDM rights and grievance contact. */
    DPDP_ACK,
}
