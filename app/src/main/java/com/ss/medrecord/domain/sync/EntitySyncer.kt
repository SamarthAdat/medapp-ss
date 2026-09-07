package com.ss.medrecord.domain.sync

/**
 * One entity type's half of the sync cycle.
 *
 * The worker knows nothing about patients or consents; it collects every
 * [EntitySyncer] bound into the graph and runs them in order. Adding visits in
 * Phase 4, reports in Phase 5 or medicines in Phase 6 means adding a syncer and
 * a binding, not touching the worker.
 */
interface EntitySyncer {

    /** Used in logs and in the per-type breakdown of a [SyncReport]. */
    val name: String

    /**
     * Ordering within one sync pass. Records that others reference must land
     * first, or a push can be rejected for a parent that does not exist yet.
     */
    val order: Int

    /** Pushes everything marked PENDING or FAILED. */
    suspend fun push(userId: String): SyncCounts

    /** Merges remote changes into the local database. */
    suspend fun pull(userId: String): SyncCounts
}

/** What one syncer did, for reporting and for deciding whether to retry. */
data class SyncCounts(
    val pushed: Int = 0,
    val pulled: Int = 0,
    val failed: Int = 0,
    val conflicts: Int = 0,
) {
    operator fun plus(other: SyncCounts) = SyncCounts(
        pushed = pushed + other.pushed,
        pulled = pulled + other.pulled,
        failed = failed + other.failed,
        conflicts = conflicts + other.conflicts,
    )

    companion object {
        val NONE = SyncCounts()
    }
}

/** The outcome of a full sync pass. */
data class SyncReport(
    val counts: SyncCounts = SyncCounts.NONE,
    val perEntity: Map<String, SyncCounts> = emptyMap(),
) {
    /** A pass with failures is retried with backoff; conflicts are not retried. */
    val hasFailures: Boolean get() = counts.failed > 0
}
