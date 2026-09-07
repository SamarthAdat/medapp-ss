package com.ss.medrecord.domain.model

/**
 * A record that was edited in two places and cannot be merged automatically
 * (spec section 6.4).
 *
 * These arise when a row with unsent local changes is found to have a newer
 * copy on the server. The sync engine refuses to guess: overwriting either side
 * silently would destroy a medical record someone deliberately wrote, so the
 * row is parked and the choice is put to the user.
 *
 * The consequence of parking it is why this screen has to exist. A CONFLICT row
 * is excluded from the outbox - which only selects PENDING and FAILED - so it
 * never pushes again, and the next pull marks it CONFLICT once more rather than
 * overwriting. Without a way to resolve it, a conflicted record stops syncing
 * permanently and the only trace is a number on the dashboard.
 */
data class SyncConflict(
    val entityType: ConflictEntityType,
    val entityId: String,
    /** What the row is, in the user's words: a name, a date, a file name. */
    val label: String,
    /** Extra context that distinguishes two rows with the same label. */
    val detail: String?,
    val localUpdatedAt: Long,
    /**
     * When the server copy was last written, or null if it could not be read -
     * the device is offline, or the row has since been removed server-side.
     *
     * Nullable on purpose. Substituting the local timestamp here would put a
     * number on screen that looks like a fact about the server and is not, and
     * the user is being asked to choose between two copies on exactly this
     * basis.
     */
    val remoteUpdatedAt: Long?,
) {
    /**
     * True when the server copy is the newer of the two. Shown rather than
     * acted on: newer is not the same as right, and the device that edited last
     * is not necessarily the one that was correct.
     */
    val remoteIsNewer: Boolean
        get() = remoteUpdatedAt != null && remoteUpdatedAt > localUpdatedAt

    /** The server copy could not be read, so only one side can be described. */
    val isRemoteUnknown: Boolean get() = remoteUpdatedAt == null
}

/**
 * The record types that can conflict.
 *
 * Deliberately not all of [AuditEntityType]. Consents and audit entries are
 * append-only and can never be edited twice, and the user document is written
 * only by its owner - none of them can reach this state, and listing them here
 * would imply a resolution path that will never run.
 */
enum class ConflictEntityType(val label: String) {
    PATIENT("Patient"),
    FACILITY("Clinic"),
    VISIT("Visit"),
    REPORT("Report"),
    MEDICINE("Medicine"),
}

/** Which copy the user chose to keep. */
enum class ConflictResolution {
    /**
     * Keep what is on this device and overwrite the server. The row returns to
     * the outbox with a fresh timestamp so the next pull does not immediately
     * re-conflict it.
     */
    KEEP_LOCAL,

    /** Take the server's copy and discard the local edit. */
    KEEP_REMOTE,
}
