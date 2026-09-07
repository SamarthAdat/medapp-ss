package com.ss.medrecord.data.local

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.ss.medrecord.data.local.entity.FacilityEntity
import com.ss.medrecord.data.local.entity.PatientEntity
import com.ss.medrecord.data.local.entity.UserEntity
import com.ss.medrecord.data.local.entity.VisitEntity
import com.ss.medrecord.domain.model.FacilityType
import com.ss.medrecord.domain.model.Relationship
import com.ss.medrecord.domain.model.SyncStatus
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.time.LocalDate

/**
 * The account-wide visit query behind the dashboard and the timeline.
 *
 * Instrumented because everything worth checking here is SQL: the joins, the
 * deleted-row filter and the per-user scoping. A mocked DAO would happily
 * return whatever the test wanted.
 *
 * The joins are LEFT defensively rather than out of necessity - the foreign
 * keys make a dangling facility_id or patient_id impossible to insert - but a
 * history screen should degrade to a nameless row rather than drop a medical
 * record if that ever stops being true.
 */
@RunWith(AndroidJUnit4::class)
class VisitContextQueryTest {

    private lateinit var database: MedRecordDatabase

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
    fun aVisitResolvesItsFacilityAndPatientNames() = runTest {
        seed()
        insertVisit("v1")

        val row = database.visitDao().observeVisitsWithContext(USER_ID).first().single()

        assertEquals("Sunrise Clinic", row.facilityName)
        assertEquals("Asha", row.patientName)
    }

    @Test
    fun aVisitStillNamesItsFacilityAfterTheClinicIsRemoved() = runTest {
        // Why facilities are soft-deleted and why this key is NO ACTION: a
        // visit has to keep resolving where it happened even after the clinic
        // is taken out of the picker. The row survives, so the join still
        // names it.
        seed()
        insertVisit("v1")

        database.facilityDao().softDelete(FACILITY_ID, deletedAt = 1L)

        val row = database.visitDao().observeVisitsWithContext(USER_ID).first().single()

        assertEquals("v1", row.visit.visitId)
        assertEquals("Sunrise Clinic", row.facilityName)
    }

    @Test
    fun deletedVisitsAreExcluded() = runTest {
        seed()
        insertVisit("v1")
        insertVisit("v2")
        database.visitDao().softDelete("v2", deletedAt = 1L)

        val rows = database.visitDao().observeVisitsWithContext(USER_ID).first()

        assertEquals(listOf("v1"), rows.map { it.visit.visitId })
    }

    @Test
    fun anotherAccountsVisitsAreNotReturned() = runTest {
        // The whole per-user scoping rests on this WHERE clause; a device that
        // has held two accounts must never mix them.
        seed()
        insertVisit("mine")
        seedOtherAccount()
        database.visitDao().upsert(
            VisitEntity(
                visitId = "theirs",
                userId = OTHER_USER_ID,
                patientId = OTHER_PATIENT_ID,
                facilityId = FACILITY_ID,
                visitDateEpochDay = LocalDate.now().toEpochDay(),
                createdAt = 0L,
                updatedAt = 0L,
                syncStatus = SyncStatus.SYNCED,
            ),
        )

        val rows = database.visitDao().observeVisitsWithContext(USER_ID).first()

        assertEquals(listOf("mine"), rows.map { it.visit.visitId })
    }

    @Test
    fun visitsComeBackNewestFirst() = runTest {
        seed()
        insertVisit("older", on = LocalDate.now().minusDays(10))
        insertVisit("newer", on = LocalDate.now().minusDays(1))

        val rows = database.visitDao().observeVisitsWithContext(USER_ID).first()

        assertEquals(listOf("newer", "older"), rows.map { it.visit.visitId })
    }

    private suspend fun insertVisit(id: String, on: LocalDate = LocalDate.now()) {
        database.visitDao().upsert(
            VisitEntity(
                visitId = id,
                userId = USER_ID,
                patientId = PATIENT_ID,
                facilityId = FACILITY_ID,
                visitDateEpochDay = on.toEpochDay(),
                createdAt = 0L,
                updatedAt = 0L,
                syncStatus = SyncStatus.SYNCED,
            ),
        )
    }

    private suspend fun seed() {
        database.userDao().upsert(user(USER_ID))
        database.patientDao().upsert(patient(PATIENT_ID, USER_ID, "Asha"))
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

    private suspend fun seedOtherAccount() {
        database.userDao().upsert(user(OTHER_USER_ID))
        database.patientDao().upsert(patient(OTHER_PATIENT_ID, OTHER_USER_ID, "Someone else"))
    }

    private fun user(userId: String) = UserEntity(
        userId = userId,
        name = "Owner",
        email = "$userId@example.com",
        createdAt = 0L,
        updatedAt = 0L,
        syncStatus = SyncStatus.SYNCED,
    )

    private fun patient(patientId: String, userId: String, name: String) = PatientEntity(
        patientId = patientId,
        userId = userId,
        name = name,
        relationship = Relationship.SELF,
        createdAt = 0L,
        updatedAt = 0L,
        syncStatus = SyncStatus.SYNCED,
    )

    private companion object {
        const val USER_ID = "u1"
        const val PATIENT_ID = "p1"
        const val FACILITY_ID = "f1"
        const val OTHER_USER_ID = "u2"
        const val OTHER_PATIENT_ID = "p2"
    }
}
