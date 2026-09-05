package com.ss.medrecord.domain.model

/**
 * Outbox state of a locally stored record (spec section 6.2). Every mutable
 * entity carries one so the Phase 3 SyncWorker can find what still needs pushing.
 */
enum class SyncStatus {
    /** Written locally, not yet pushed to Firestore. */
    PENDING,

    /** Local and remote copies agree. */
    SYNCED,

    /** Remote changed underneath a pending local edit; needs user resolution. */
    CONFLICT,

    /** Push attempted and rejected; will be retried with backoff. */
    FAILED,
}
