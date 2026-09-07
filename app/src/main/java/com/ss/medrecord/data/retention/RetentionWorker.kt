package com.ss.medrecord.data.retention

import android.content.Context
import android.util.Log
import androidx.hilt.work.HiltWorker
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.ss.medrecord.core.common.AppConstants
import com.ss.medrecord.core.common.DispatcherProvider
import com.ss.medrecord.core.file.EncryptedFileStore
import com.ss.medrecord.data.local.dao.FacilityDao
import com.ss.medrecord.data.local.dao.MedicineDao
import com.ss.medrecord.data.local.dao.PatientDao
import com.ss.medrecord.data.local.dao.ReportDao
import com.ss.medrecord.data.local.dao.VisitDao
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.withContext
import java.time.Duration
import javax.inject.Inject
import javax.inject.Singleton

private const val TAG = "RetentionWorker"

/**
 * Carries out the 30-day grace period (spec section 9.6).
 *
 * Deleting a record marks `deleted_at` and stops showing it. Until this worker
 * existed, that is all that ever happened: soft-deleted rows sat on the device
 * indefinitely, so the app promised erasure and delivered concealment. Under
 * DPDP's right to erasure and HIPAA's disposal requirements, the difference
 * matters.
 *
 * Order is deliberate. Children are purged before their parents, even though
 * every one of these tables would cascade: a report's encrypted file lives on
 * the filesystem and no SQL cascade will ever reach it, so the rows have to be
 * read and their files removed before the row that would take them silently.
 *
 * Local only. The Firestore copy and the Cloud Storage object are purged
 * server-side, which needs credentials no client should hold - a client that
 * could hard-delete server rows could also erase someone else's audit trail.
 * Until that function exists the remote copy survives, and the README says so.
 */
@HiltWorker
class RetentionWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted workerParams: WorkerParameters,
    private val patientDao: PatientDao,
    private val facilityDao: FacilityDao,
    private val visitDao: VisitDao,
    private val reportDao: ReportDao,
    private val medicineDao: MedicineDao,
    private val fileStore: EncryptedFileStore,
    private val dispatchers: DispatcherProvider,
) : CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result = withContext(dispatchers.io) {
        val cutoff = System.currentTimeMillis() -
            Duration.ofDays(AppConstants.SOFT_DELETE_GRACE_PERIOD_DAYS.toLong()).toMillis()

        var purged = 0

        // Reports first, and one at a time: each carries an encrypted file that
        // has to be removed before the row pointing at it is gone.
        reportDao.getPurgeable(cutoff).forEach { report ->
            fileStore.delete(report.localFilePath)
            reportDao.hardDelete(report.reportId)
            purged++
        }

        medicineDao.getPurgeable(cutoff).forEach { medicine ->
            medicineDao.hardDelete(medicine.medicineId)
            purged++
        }

        visitDao.getPurgeable(cutoff).forEach { visit ->
            visitDao.hardDelete(visit.visitId)
            purged++
        }

        // Patients last of the record types: deleting one cascades to anything
        // still hanging off it, which by now is only rows deleted more recently
        // than the patient and therefore also past their own grace period.
        patientDao.getPurgeablePatients(cutoff).forEach { patient ->
            patientDao.hardDelete(patient.patientId)
            purged++
        }

        facilityDao.getPurgeable(cutoff).forEach { facility ->
            facilityDao.hardDelete(facility.facilityId)
            purged++
        }

        if (purged > 0) Log.d(TAG, "Purged $purged record(s) past the grace period")

        // Audit entries are deliberately never purged here. They are evidence
        // under a six-year retention rule, they contain no clinical values, and
        // an erasure routine that quietly deleted the record of what was erased
        // would defeat its own purpose.
        Result.success()
    }

    companion object {
        const val WORK_NAME = "medrecord_retention_purge"
    }
}

/**
 * Registers the daily purge.
 *
 * Daily rather than on-demand because the grace period expires by the calendar,
 * not in response to anything the user does - nothing would otherwise trigger
 * it on a device that is simply left alone.
 */
@Singleton
class RetentionScheduler @Inject constructor(
    private val workManager: WorkManager,
) {
    fun initialize() {
        val request = PeriodicWorkRequestBuilder<RetentionWorker>(
            Duration.ofHours(24),
        )
            .setConstraints(
                Constraints.Builder()
                    // Purging touches the encrypted file store; doing it while
                    // the battery is nearly flat risks being killed mid-pass.
                    // A day's delay costs nothing against a 30-day period.
                    .setRequiresBatteryNotLow(true)
                    .build(),
            )
            .build()

        workManager.enqueueUniquePeriodicWork(
            RetentionWorker.WORK_NAME,
            ExistingPeriodicWorkPolicy.KEEP,
            request,
        )
    }
}
