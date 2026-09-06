package com.ss.medrecord.data.local

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.ss.medrecord.data.local.entity.FacilityEntity
import com.ss.medrecord.data.local.entity.PatientEntity
import com.ss.medrecord.data.local.entity.ReportEntity
import com.ss.medrecord.data.local.entity.UserEntity
import com.ss.medrecord.data.local.entity.VisitEntity
import com.ss.medrecord.domain.model.FacilityType
import com.ss.medrecord.domain.model.Relationship
import com.ss.medrecord.domain.model.ReportFileType
import com.ss.medrecord.domain.model.SyncStatus
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Guards against a data-loss bug that reached a device: DAOs used
 * `@Insert(onConflict = REPLACE)`, which SQLite implements as DELETE followed
 * by INSERT. The delete fired ON DELETE CASCADE, so re-applying a parent row
 * during an ordinary sync pass silently destroyed every child row under it -
 * including records created on this device that had never been pushed and so
 * could not be pulled back.
 *
 * The tables here are the deepest chain in the schema (user -> patient -> visit
 * -> report), which is where the damage was worst: refreshing patients on
 * sign-in wiped a patient's whole history.
 */
@RunWith(AndroidJUnit4::class)
class CascadeSafetyTest {

    private lateinit var database: MedRecordDatabase

    @Before
    fun setUp() {
        database = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            MedRecordDatabase::class.java,
        ).build()
        // Room turns foreign keys on for every connection it opens; the
        // deleting-a-patient case below fails loudly if that ever stops being
        // true, which is what makes the other two cases meaningful.
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun reApplyingAPatientKeepsTheirVisitsAndReports() = runTest {
        seed()

        // What a sign-in does: the remote patient copy is merged back in.
        database.patientDao().upsertAll(listOf(patient(name = "Asha Rao")))

        assertEquals("Asha Rao", database.patientDao().getPatient(PATIENT_ID)?.name)
        assertEquals(1, database.visitDao().observeVisitCount(PATIENT_ID).first())
        assertEquals(1, database.reportDao().observeReportCountForVisit(VISIT_ID).first())
    }

    @Test
    fun reApplyingAVisitKeepsItsReports() = runTest {
        seed()

        // What a pull sync does when the server copy is newer.
        database.visitDao().upsert(visit(doctorName = "Dr Mehta"))

        assertEquals("Dr Mehta", database.visitDao().getVisit(VISIT_ID)?.doctorName)
        assertEquals(1, database.reportDao().observeReportCountForVisit(VISIT_ID).first())
    }

    @Test
    fun deletingAPatientStillCascadesToTheirRecords() = runTest {
        // The cascade itself is wanted (spec 9.6) - only the accidental one is
        // not. A hard delete of the account holder must still reach everything.
        seed()

        database.userDao().deleteAll()

        assertEquals(0, database.visitDao().observeVisitCount(PATIENT_ID).first())
        assertEquals(0, database.reportDao().observeReportCountForVisit(VISIT_ID).first())
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
        database.patientDao().upsert(patient())
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
        database.visitDao().upsert(visit())
        database.reportDao().upsert(
            ReportEntity(
                reportId = "r1",
                userId = USER_ID,
                patientId = PATIENT_ID,
                visitId = VISIT_ID,
                fileName = "lab_result.pdf",
                fileType = ReportFileType.PDF,
                fileSizeBytes = 616,
                localFilePath = "/data/r1.enc",
                createdAt = 0L,
                updatedAt = 0L,
            ),
        )
    }

    private fun patient(name: String = "Asha") = PatientEntity(
        patientId = PATIENT_ID,
        userId = USER_ID,
        name = name,
        relationship = Relationship.SELF,
        createdAt = 0L,
        updatedAt = 0L,
        syncStatus = SyncStatus.SYNCED,
    )

    private fun visit(doctorName: String? = null) = VisitEntity(
        visitId = VISIT_ID,
        userId = USER_ID,
        patientId = PATIENT_ID,
        facilityId = FACILITY_ID,
        doctorName = doctorName,
        visitDateEpochDay = 20_000L,
        createdAt = 0L,
        updatedAt = 0L,
        syncStatus = SyncStatus.SYNCED,
    )

    private companion object {
        const val USER_ID = "u1"
        const val PATIENT_ID = "p1"
        const val VISIT_ID = "v1"
        const val FACILITY_ID = "f1"
    }
}
