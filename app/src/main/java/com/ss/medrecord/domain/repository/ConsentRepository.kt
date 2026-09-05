package com.ss.medrecord.domain.repository

import com.ss.medrecord.core.common.DataResult
import com.ss.medrecord.domain.model.ConsentRecord
import kotlinx.coroutines.flow.Flow

/**
 * The versioned consent ledger required before any data processing begins
 * (spec section 9.5).
 */
interface ConsentRepository {

    fun observeConsentHistory(userId: String): Flow<List<ConsentRecord>>

    /**
     * Records acceptance of every consent type at [version]. Writes to Room
     * first so acceptance survives being offline; the Firestore copy is pushed
     * opportunistically and retried by the Phase 3 sync worker otherwise.
     */
    suspend fun acceptCurrentConsent(userId: String, version: Int): DataResult<Unit>

    /** Highest consent version this user has accepted, or null if none. */
    suspend fun latestAcceptedVersion(userId: String): Int?
}
