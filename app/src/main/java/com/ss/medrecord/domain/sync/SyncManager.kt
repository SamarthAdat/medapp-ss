package com.ss.medrecord.domain.sync

import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.ss.medrecord.core.common.AppConstants
import com.ss.medrecord.core.connectivity.ConnectivityObserver
import com.ss.medrecord.core.connectivity.NetworkStatus
import com.ss.medrecord.data.local.dao.AuditLogDao
import com.ss.medrecord.data.local.dao.ConsentDao
import com.ss.medrecord.data.local.dao.FacilityDao
import com.ss.medrecord.data.local.dao.PatientDao
import com.ss.medrecord.data.local.dao.UserDao
import com.ss.medrecord.data.local.dao.VisitDao
import com.ss.medrecord.data.sync.SyncStateHolder
import com.ss.medrecord.data.sync.SyncWorker
import com.ss.medrecord.di.ApplicationScope
import com.ss.medrecord.domain.model.AuthSession
import com.ss.medrecord.domain.session.SessionManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.stateIn
import java.time.Duration
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Owns when sync runs and exposes what the UI needs to describe it.
 *
 * Three triggers, per spec section 6.2: a periodic pass, a one-shot when the
 * network comes back, and a one-shot when the app is brought to the foreground.
 * All three funnel into the same worker under a KEEP policy, so a burst of
 * triggers collapses into one run rather than stacking up.
 */
@Singleton
class SyncManager @Inject constructor(
    private val workManager: WorkManager,
    private val sessionManager: SessionManager,
    private val connectivityObserver: ConnectivityObserver,
    private val syncStateHolder: SyncStateHolder,
    userDao: UserDao,
    consentDao: ConsentDao,
    patientDao: PatientDao,
    auditLogDao: AuditLogDao,
    facilityDao: FacilityDao,
    visitDao: VisitDao,
    @param:ApplicationScope private val scope: CoroutineScope,
) {

    /** Everything still waiting to reach Firestore, across every table. */
    val pendingCount: StateFlow<Int> = combine(
        userDao.observePendingCount(),
        consentDao.observePendingCount(),
        patientDao.observePendingCount(),
        auditLogDao.observePendingCount(),
        facilityDao.observePendingCount(),
        visitDao.observePendingCount(),
    ) { counts -> counts.sum() }
        .distinctUntilChanged()
        .stateIn(scope, SharingStarted.Eagerly, 0)

    val conflictCount: StateFlow<Int> = combine(
        patientDao.observeConflictCount(),
        visitDao.observeConflictCount(),
    ) { counts -> counts.sum() }
        .distinctUntilChanged()
        .stateIn(scope, SharingStarted.Eagerly, 0)

    val syncState = syncStateHolder.state

    /** Combined view for the status indicator. */
    val status: StateFlow<SyncStatusUi> = combine(
        pendingCount,
        conflictCount,
        syncState,
        connectivityObserver.status,
    ) { pending, conflicts, run, network ->
        SyncStatusUi(
            pendingCount = pending,
            conflictCount = conflicts,
            isSyncing = run.isSyncing,
            isOnline = network == NetworkStatus.AVAILABLE,
            lastSuccessAt = run.lastSuccessAt,
        )
    }.stateIn(scope, SharingStarted.Eagerly, SyncStatusUi())

    fun initialize() {
        schedulePeriodicSync()

        // Sync as soon as an account is usable. Before consent there is nothing
        // the rules would accept anyway.
        sessionManager.session
            .filter { it is AuthSession.Authenticated }
            .map { (it as AuthSession.Authenticated).userId }
            .distinctUntilChanged()
            .onEach { syncNow() }
            .launchIn(scope)

        // Reconnect trigger. distinctUntilChanged keeps a flapping connection
        // from enqueuing repeatedly.
        connectivityObserver.status
            .distinctUntilChanged()
            .filter { it == NetworkStatus.AVAILABLE }
            .onEach { syncNow() }
            .launchIn(scope)
    }

    /** Called when the app comes to the foreground, and by "Sync now". */
    fun syncNow(expedited: Boolean = false) {
        if (sessionManager.session.value !is AuthSession.Authenticated) return

        val request = OneTimeWorkRequestBuilder<SyncWorker>()
            .setConstraints(networkConstraints())
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, BACKOFF_SECONDS, java.util.concurrent.TimeUnit.SECONDS)
            .build()

        workManager.enqueueUniqueWork(
            SyncWorker.WORK_NAME_ONE_SHOT,
            // KEEP, not REPLACE: replacing would cancel a pass that is already
            // partway through pushing, and the triggers overlap by design.
            if (expedited) ExistingWorkPolicy.REPLACE else ExistingWorkPolicy.KEEP,
            request,
        )
    }

    private fun schedulePeriodicSync() {
        val request = PeriodicWorkRequestBuilder<SyncWorker>(
            Duration.ofMinutes(AppConstants.SYNC_INTERVAL_MINUTES),
        )
            .setConstraints(networkConstraints())
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, BACKOFF_SECONDS, java.util.concurrent.TimeUnit.SECONDS)
            .build()

        workManager.enqueueUniquePeriodicWork(
            SyncWorker.WORK_NAME_PERIODIC,
            // KEEP so an app restart does not reset the interval and starve the
            // periodic pass on a device that is opened often.
            ExistingPeriodicWorkPolicy.KEEP,
            request,
        )
    }

    private fun networkConstraints() = Constraints.Builder()
        .setRequiredNetworkType(NetworkType.CONNECTED)
        .build()

    private companion object {
        const val BACKOFF_SECONDS = 30L
    }
}

/** What the sync indicator renders. */
data class SyncStatusUi(
    val pendingCount: Int = 0,
    val conflictCount: Int = 0,
    val isSyncing: Boolean = false,
    val isOnline: Boolean = false,
    val lastSuccessAt: Long? = null,
) {
    val hasPendingWork: Boolean get() = pendingCount > 0
    val hasConflicts: Boolean get() = conflictCount > 0
}
