package com.ss.medrecord.data.retention

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.ss.medrecord.core.common.AppConstants
import com.ss.medrecord.data.local.MedRecordDatabase
import com.ss.medrecord.data.local.entity.FacilityEntity
import com.ss.medrecord.data.local.entity.MedicineEntity
import com.ss.medrecord.data.local.entity.PatientEntity
import com.ss.medrecord.data.local.entity.ReportEntity
import com.ss.medrecord.data.local.entity.UserEntity
import com.ss.medrecord.data.local.entity.VisitEntity
import com.ss.medrecord.domain.model.FacilityType
import com.ss.medrecord.domain.model.MedicineFrequency
import com.ss.medrecord.domain.model.Relationship
import com.ss.medrecord.domain.model.ReportFileType
import com.ss.medrecord.domain.model.SyncStatus
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.time.Duration
import java.time.LocalDate

/**
 * The 30-day grace period (spec section 9.6).
 *
 * Worth instrumented tests because until this phase the queries existed and
 * nothing called them: the app marked records deleted and kept them forever, so
 * "recoverable for 30 days, then removed" was only half true. These pin both
 * halves - that a recent deletion survives, and that an expired one actually
 * goes.
 */
@RunWith(AndroidJUnit4::class)
class RetentionPurgeTest {

    private lateinit var database: MedRecordDatabase

    private val graceMillis =
        Duration.ofDays(AppConstants.SOFT_DELETE_GRACE_PERIOD_DAYS.toLong()).toMillis()

    @Before
    fun setUp() {
        database = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            MedRecordDatabase::class.java,
        ).build()
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun aRecentlyDeletedRecordIsNotPurged() = runTest {
        seed()
        insertVisit("v1")
        deleteVisitAndSync("v1", at = System.currentTimeMillis())

        val purgeable = database.visitDao().getPurgeable(cutoff())

        assertEquals(emptyList<String>(), purgeable.map { it.visitId })
    }

    @Test
    fun aRecordPastTheGracePeriodIsPurgeable() = runTest {
        seed()
        insertVisit("v1")
        deleteVisitAndSync("v1", at = expired())

        val purgeable = database.visitDao().getPurgeable(cutoff())

        assertEquals(listOf("v1"), purgeable.map { it.visitId })
    }

    @Test
    fun aRecordThatWasNeverDeletedIsNeverPurgeable() = runTest {
        // The filter is deleted_at IS NOT NULL; getting that wrong would erase
        // live medical records on a timer.
        seed()
        insertVisit("v1")

        assertEquals(emptyList<String>(), database.visitDao().getPurgeable(cutoff()).map { it.visitId })
    }

    @Test
    fun aDeletionThatHasNotReachedTheServerIsNeverPurged() = runTest {
        // Purging it here would erase the row locally while the server still
        // holds it live, and the next pull would bring it straight back - for a
        // report, after its encrypted file had already been destroyed.
        seed()
        insertVisit("v1")
        database.visitDao().softDelete("v1", deletedAt = expired())

        assertEquals(emptyList<String>(), database.visitDao().getPurgeable(cutoff()).map { it.visitId })
    }

    @Test
    fun hardDeleteActuallyRemovesTheRow() = runTest {
        seed()
        insertVisit("v1")
        deleteVisitAndSync("v1", at = expired())

        database.visitDao().hardDelete("v1")

        assertNull(database.visitDao().getVisitIncludingDeleted("v1"))
    }

    @Test
    fun everyRecordTypeSupportsThePurge() = runTest {
        // One expired row of each kind, to catch a table added in a later phase
        // that quietly never gets purged.
        seed()
        insertVisit("v1")
        insertReport("r1")
        insertMedicine("m1")

        val expiredAt = expired()
        database.visitDao().softDelete("v1", deletedAt = expiredAt)
        database.reportDao().softDelete("r1", deletedAt = expiredAt)
        database.medicineDao().softDelete("m1", deletedAt = expiredAt)
        database.patientDao().softDelete(PATIENT_ID, deletedAt = expiredAt)
        database.facilityDao().softDelete(FACILITY_ID, deletedAt = expiredAt)
        markEverythingSynced()

        val cutoff = cutoff()
        assertEquals(1, database.visitDao().getPurgeable(cutoff).size)
        assertEquals(1, database.reportDao().getPurgeable(cutoff).size)
        assertEquals(1, database.medicineDao().getPurgeable(cutoff).size)
        assertEquals(1, database.patientDao().getPurgeablePatients(cutoff).size)
        assertEquals(1, database.facilityDao().getPurgeable(cutoff).size)
    }

    @Test
    fun purgeableReportsStillCarryTheirFilePath() = runTest {
        // The worker needs the path to delete the encrypted file before the row
        // goes; a cascade would take the row and orphan the file forever.
        seed()
        insertReport("r1")
        database.reportDao().softDelete("r1", deletedAt = expired())
        database.reportDao().markSyncStatus(listOf("r1"), SyncStatus.SYNCED)

        val report = database.reportDao().getPurgeable(cutoff()).single()

        assertNotNull(report.localFilePath)
        assertEquals("/data/r1.enc", report.localFilePath)
    }


    /** Soft delete, then mark it pushed - the state a purge requires. */
    private suspend fun deleteVisitAndSync(id: String, at: Long) {
        database.visitDao().softDelete(id, deletedAt = at)
        database.visitDao().markSyncStatus(listOf(id), SyncStatus.SYNCED)
    }

    private suspend fun markEverythingSynced() {
        database.visitDao().markSyncStatus(listOf("v1"), SyncStatus.SYNCED)
        database.reportDao().markSyncStatus(listOf("r1"), SyncStatus.SYNCED)
        database.medicineDao().markSyncStatus(listOf("m1"), SyncStatus.SYNCED)
        database.patientDao().markSyncStatus(listOf(PATIENT_ID), SyncStatus.SYNCED)
        database.facilityDao().markSyncStatus(listOf(FACILITY_ID), SyncStatus.SYNCED)
    }

    private fun cutoff() = System.currentTimeMillis() - graceMillis

    /** A deletion old enough to be past the grace period. */
    private fun expired() = System.currentTimeMillis() - graceMillis - Duration.ofDays(1).toMillis()

    private suspend fun insertVisit(id: String) {
        database.visitDao().upsert(
            VisitEntity(
                visitId = id,
                userId = USER_ID,
                patientId = PATIENT_ID,
                facilityId = FACILITY_ID,
                visitDateEpochDay = LocalDate.now().toEpochDay(),
                createdAt = 0L,
                updatedAt = 0L,
                syncStatus = SyncStatus.SYNCED,
            ),
        )
    }

    private suspend fun insertReport(id: String) {
        insertVisit(VISIT_ID)
        database.reportDao().upsert(
            ReportEntity(
                reportId = id,
                userId = USER_ID,
                patientId = PATIENT_ID,
                visitId = VISIT_ID,
                fileName = "lab.pdf",
                fileType = ReportFileType.PDF,
                fileSizeBytes = 616,
                localFilePath = "/data/$id.enc",
                createdAt = 0L,
                updatedAt = 0L,
            ),
        )
    }

    private suspend fun insertMedicine(id: String) {
        database.medicineDao().upsert(
            MedicineEntity(
                medicineId = id,
                userId = USER_ID,
                patientId = PATIENT_ID,
                name = "Metformin",
                frequency = MedicineFrequency.DAILY,
                reminderTimes = "480",
                startDateEpochDay = LocalDate.now().toEpochDay(),
                createdAt = 0L,
                updatedAt = 0L,
            ),
        )
    }

    private suspend fun seed() {
        database.userDao().upsert(
            UserEntity(
                userId = USER_ID,
                name = "Owner",
                email = "owner@example.com",
                createdAt = 0L,
                updatedAt = 0L,
                syncStatus = SyncStatus.SYNCED,
            ),
        )
        database.patientDao().upsert(
            PatientEntity(
                patientId = PATIENT_ID,
                userId = USER_ID,
                name = "Asha",
                relationship = Relationship.SELF,
                createdAt = 0L,
                updatedAt = 0L,
                syncStatus = SyncStatus.SYNCED,
            ),
        )
        database.facilityDao().upsert(
            FacilityEntity(
                facilityId = FACILITY_ID,
                userId = USER_ID,
                name = "Sunrise Clinic",
                type = FacilityType.CLINIC,
                createdAt = 0L,
                updatedAt = 0L,
                syncStatus = SyncStatus.SYNCED,
            ),
        )
    }

    private companion object {
        const val USER_ID = "u1"
        const val PATIENT_ID = "p1"
        const val FACILITY_ID = "f1"
        const val VISIT_ID = "v-parent"
    }
}
