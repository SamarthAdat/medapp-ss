package com.ss.medrecord.domain.model

/**
 * The account holder (spec section 4.1). One user owns N patient profiles.
 *
 * [consentVersion] is null until the versioned consent has been accepted; the
 * session gate compares it against [com.ss.medrecord.core.common.AppConstants.CURRENT_CONSENT_VERSION]
 * to decide whether to re-prompt.
 */
data class AppUser(
    val userId: String,
    val name: String,
    val email: String,
    val phone: String? = null,
    val createdAt: Long,
    val updatedAt: Long,
    val consentAcceptedAt: Long? = null,
    val consentVersion: Int? = null,
) {
    fun hasAcceptedConsent(currentVersion: Int): Boolean =
        consentVersion != null && consentVersion >= currentVersion
}
