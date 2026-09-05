package com.ss.medrecord.ui.feature.consent

import com.ss.medrecord.domain.model.ConsentType

/**
 * The consent copy, held in code alongside the version constant it belongs to.
 *
 * Section 9.5 requires a re-prompt whenever this text changes, so editing any
 * string here means bumping
 * [com.ss.medrecord.core.common.AppConstants.CURRENT_CONSENT_VERSION] in the
 * same commit - otherwise existing users silently keep an acceptance that no
 * longer describes what they agreed to.
 *
 * The wording is a working draft that needs legal review before production use
 * with real patient data (spec section 9.7), and the grievance contact below is
 * a placeholder until a real one is designated as DPDP requires.
 */
object ConsentTexts {

    const val GRIEVANCE_CONTACT = "privacy@medrecordkeeper.example"

    data class ConsentClause(
        val type: ConsentType,
        val title: String,
        val body: String,
    )

    val clauses: List<ConsentClause> = listOf(
        ConsentClause(
            type = ConsentType.DATA_PROCESSING,
            title = "Processing your health records",
            body = "You are asking this app to store the medical information you enter - patient " +
                "profiles, visits, reports, medicines and reminders - so that you can keep and " +
                "retrieve your own records. That is the only purpose it is used for. Records are " +
                "encrypted on this device and in your private cloud account, and are never sold, " +
                "shared with advertisers, or used to train anything.",
        ),
        ConsentClause(
            type = ConsentType.HIPAA_ACK,
            title = "Handling of protected health information",
            body = "Your records are kept under access controls that scope every read and write to " +
                "your own account. Sensitive actions - viewing a report, exporting data, deleting " +
                "a patient - are written to an audit trail that you cannot edit and that is " +
                "retained for the period the regulations require, even after you delete a record.",
        ),
        ConsentClause(
            type = ConsentType.DPDP_ACK,
            title = "Your rights under the DPDP Act, 2023",
            body = "You can download everything held about you, correct it, or ask for it to be " +
                "erased at any time from Settings. Erasure removes your records after a short " +
                "grace period, though a redacted audit stub is kept where the law requires it. " +
                "Data is stored in the Mumbai region. Grievances can be raised at " +
                "$GRIEVANCE_CONTACT.",
        ),
    )
}
