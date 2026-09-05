package com.ss.medrecord.domain.model

/**
 * What the splash screen resolves to, and what every guarded destination checks.
 *
 * [PendingConsent] exists as its own state rather than a flag on [Authenticated]
 * so that the navigation graph cannot accidentally let an unconsented user reach
 * a data-entry screen - section 9.5 requires consent before any processing.
 */
sealed interface AuthSession {
    /** Session not resolved yet; splash is still deciding. */
    data object Unknown : AuthSession

    /** No Firebase user. */
    data object SignedOut : AuthSession

    /** Signed in, but the accepted consent version is missing or stale. */
    data class PendingConsent(val userId: String) : AuthSession

    /** Signed in with current consent on file. Full app access. */
    data class Authenticated(val userId: String) : AuthSession
}
