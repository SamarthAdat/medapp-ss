package com.ss.medrecord.domain.sync

import com.ss.medrecord.domain.model.ConflictEntityType
import com.ss.medrecord.domain.model.SyncConflict

/**
 * One record type's half of conflict resolution.
 *
 * Contributed into a set, like [EntitySyncer], so the resolution screen knows
 * nothing about patients or visits and a new record type is a binding rather
 * than an edit to the screen.
 *
 * Deliberately a separate interface rather than more methods on [EntitySyncer]:
 * users, consents and audit entries are synced but cannot conflict - consents
 * and audit rows are append-only, and the user document is written only by its
 * owner - and folding this in would force three implementations that can never
 * run.
 */
interface ConflictSource {

    val entityType: ConflictEntityType

    /** Parked rows, with enough context for the user to tell them apart. */
    suspend fun conflicts(userId: String): List<SyncConflict>

    /**
     * Keep this device's copy: return the row to the outbox with a fresh
     * timestamp so the next push overwrites the server and the next pull does
     * not immediately re-conflict it.
     */
    suspend fun keepLocal(entityId: String)

    /** Take the server's copy and discard the local edit. */
    suspend fun keepRemote(userId: String, entityId: String)
}
