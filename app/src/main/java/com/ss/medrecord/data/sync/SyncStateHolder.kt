package com.ss.medrecord.data.sync

import com.ss.medrecord.domain.sync.SyncReport
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import java.util.concurrent.atomic.AtomicBoolean
import javax.inject.Inject
import javax.inject.Singleton

/**
 * In-memory record of what the sync engine is doing, so the UI can show it
 * (spec section 6.6).
 *
 * Deliberately not persisted. "Last synced 3 hours ago" is only meaningful for
 * the current process; after a restart the pending counts read from the
 * database are the honest answer, and a stale timestamp would imply the app had
 * checked more recently than it had.
 */
@Singleton
class SyncStateHolder @Inject constructor() {

    private val _state = MutableStateFlow(SyncRunState())
    val state: StateFlow<SyncRunState> = _state.asStateFlow()

    /**
     * The periodic pass and the one-shot triggers are separate WorkManager
     * entries, so WorkManager will happily run them at the same time. Without
     * this guard both passes read the same PENDING rows and push each record
     * twice.
     */
    private val running = AtomicBoolean(false)

    /** Returns false if a pass is already in flight; the caller should stand down. */
    fun tryBeginSync(): Boolean {
        val acquired = running.compareAndSet(false, true)
        if (acquired) _state.update { it.copy(isSyncing = true) }
        return acquired
    }

    fun onSyncFinished(report: SyncReport) {
        running.set(false)
        _state.update {
            it.copy(
                isSyncing = false,
                lastReport = report,
                lastSuccessAt = if (report.hasFailures) it.lastSuccessAt else System.currentTimeMillis(),
                lastAttemptAt = System.currentTimeMillis(),
            )
        }
    }
}

data class SyncRunState(
    val isSyncing: Boolean = false,
    val lastReport: SyncReport? = null,
    val lastSuccessAt: Long? = null,
    val lastAttemptAt: Long? = null,
)
