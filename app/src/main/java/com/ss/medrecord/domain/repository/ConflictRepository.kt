package com.ss.medrecord.domain.repository

import com.ss.medrecord.core.common.DataResult
import com.ss.medrecord.domain.model.ConflictResolution
import com.ss.medrecord.domain.model.SyncConflict

interface ConflictRepository {

    /**
     * Every parked row on the account, across record types.
     *
     * A suspend read rather than a Flow: resolving a conflict reads the server
     * to show both timestamps, and a Flow would re-issue that on every
     * unrelated database write. The screen refreshes on open and after each
     * resolution, which is when the answer can actually have changed.
     */
    suspend fun conflicts(userId: String): DataResult<List<SyncConflict>>

    suspend fun resolve(
        userId: String,
        conflict: SyncConflict,
        resolution: ConflictResolution,
    ): DataResult<Unit>
}
