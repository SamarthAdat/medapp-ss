package com.ss.medrecord.data.reminder

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.ss.medrecord.core.common.DispatcherProvider
import com.ss.medrecord.data.local.MedRecordDatabase
import com.ss.medrecord.data.local.entity.FacilityEntity
import com.ss.medrecord.data.local.entity.MedicineEntity
import com.ss.medrecord.data.local.entity.PatientEntity
import com.ss.medrecord.data.local.entity.UserEntity
import com.ss.medrecord.data.local.entity.VisitEntity
import com.ss.medrecord.domain.model.FacilityType
import com.ss.medrecord.domain.model.MedicineFrequency
import com.ss.medrecord.domain.model.Relationship
import com.ss.medrecord.domain.model.ReminderStatus
import com.ss.medrecord.domain.model.ReminderType
import com.ss.medrecord.domain.model.SyncStatus
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId

/**
 * The reminder generator against a real database.
 *
 * These are instrumented rather than JVM tests because the behaviour that
 * matters is the interaction with SQLite: the unique (source, trigger) index
 * that makes regeneration idempotent, and the ON CONFLICT IGNORE that depends
 * on it. Mocking the DAO would test the mock.
 */
@RunWith(AndroidJUnit4::class)
class ReminderGenerationTest {

    private lateinit var database: MedRecordDatabase
    private lateinit var repository: ReminderRepositoryImpl

    @Before
    fun setUp() {
        database = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            MedRecordDatabase::class.java,
        ).build()

        repository = ReminderRepositoryImpl(
            reminderDao = database.reminderDao(),
            medicineDao = database.medicineDao(),
            visitDao = database.visitDao(),
            patientDao = database.patientDao(),
            dispatchers = TestDispatchers,
        )
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun aTwiceDailyCourseGeneratesTwoRemindersADayAcrossTheHorizon() = runTest {
        seedPatient()
        insertMedicine(times = "480,1200")

        repository.rebuild(USER_ID)

        val scheduled = allReminders()
        // Seven days ahead, two a day, minus whatever has already passed today.
        assertTrue("expected a week of doses, got ${scheduled.size}", scheduled.size >= 12)
        assertTrue(scheduled.all { it.type == ReminderType.MEDICINE })
        assertTrue(scheduled.all { it.triggerAtMillis > System.currentTimeMillis() })
    }

    @Test
    fun rebuildingTwiceDoesNotDuplicateAnything() = runTest {
        seedPatient()
        insertMedicine(times = "480,1200")

        repository.rebuild(USER_ID)
        val first = allReminders().size
        repository.rebuild(USER_ID)

        assertEquals(first, allReminders().size)
    }

    @Test
    fun anAlreadyFiredReminderIsNotResurrectedByARebuild() = runTest {
        // The bug this guards: regeneration that deletes and re-inserts would
        // reset a dismissed dose to SCHEDULED and notify about it again.
        seedPatient()
        insertMedicine(times = "480,1200")
        repository.rebuild(USER_ID)

        val target = allReminders().first()
        repository.markFired(listOf(target.reminderId))

        repository.rebuild(USER_ID)

        val after = database.reminderDao().getReminder(target.reminderId)
        assertEquals(ReminderStatus.FIRED, after?.status)
    }

    @Test
    fun anAsNeededMedicineGeneratesNothing() = runTest {
        seedPatient()
        insertMedicine(times = "480", frequency = MedicineFrequency.AS_NEEDED)

        repository.rebuild(USER_ID)

        assertEquals(0, allReminders().size)
    }

    @Test
    fun aPausedMedicineGeneratesNothing() = runTest {
        seedPatient()
        insertMedicine(times = "480", isActive = false)

        repository.rebuild(USER_ID)

        assertEquals(0, allReminders().size)
    }

    @Test
    fun aFinishedCourseGeneratesNothing() = runTest {
        seedPatient()
        insertMedicine(
            times = "480",
            startDate = LocalDate.now().minusDays(30),
            endDate = LocalDate.now().minusDays(1),
        )

        repository.rebuild(USER_ID)

        assertEquals(0, allReminders().size)
    }

    @Test
    fun editingTheTimesReplacesTheFutureSchedule() = runTest {
        seedPatient()
        insertMedicine(times = "480")
        repository.rebuild(USER_ID)

        val zone = ZoneId.systemDefault()
        val originalMinutes = allReminders()
            .map { minuteOfDay(it.triggerAtMillis, zone) }
            .toSet()
        assertTrue(originalMinutes.contains(8 * 60))

        insertMedicine(times = "1140")
        repository.rebuild(USER_ID)

        val updatedMinutes = allReminders()
            .filter { it.status == ReminderStatus.SCHEDULED }
            .map { minuteOfDay(it.triggerAtMillis, zone) }
            .toSet()

        assertTrue("19:00 should now be scheduled", updatedMinutes.contains(19 * 60))
        assertTrue("08:00 should no longer be scheduled", !updatedMinutes.contains(8 * 60))
    }

    @Test
    fun anUpcomingFollowUpVisitGeneratesAppointmentReminders() = runTest {
        seedPatient()
        seedFacility()
        database.visitDao().upsert(
            VisitEntity(
                visitId = VISIT_ID,
                userId = USER_ID,
                patientId = PATIENT_ID,
                facilityId = FACILITY_ID,
                visitDateEpochDay = LocalDate.now().minusDays(2).toEpochDay(),
                // Far enough out that both the day-before and day-of reminders
                // are still in the future.
                nextVisitDateEpochDay = LocalDate.now().plusDays(3).toEpochDay(),
                createdAt = 0L,
                updatedAt = 0L,
                syncStatus = SyncStatus.SYNCED,
            ),
        )

        repository.rebuild(USER_ID)

        val visitReminders = allReminders().filter { it.type == ReminderType.VISIT }
        assertEquals(2, visitReminders.size)
        assertTrue(visitReminders.all { it.sourceId == VISIT_ID })
    }

    @Test
    fun aVisitWithNoFollowUpDateGeneratesNothing() = runTest {
        seedPatient()
        seedFacility()
        database.visitDao().upsert(
            VisitEntity(
                visitId = VISIT_ID,
                userId = USER_ID,
                patientId = PATIENT_ID,
                facilityId = FACILITY_ID,
                visitDateEpochDay = LocalDate.now().minusDays(2).toEpochDay(),
                createdAt = 0L,
                updatedAt = 0L,
                syncStatus = SyncStatus.SYNCED,
            ),
        )

        repository.rebuild(USER_ID)

        assertEquals(0, allReminders().count { it.type == ReminderType.VISIT })
    }

    @Test
    fun theNextAlarmIsTheEarliestScheduledReminder() = runTest {
        seedPatient()
        insertMedicine(times = "480,1200")
        repository.rebuild(USER_ID)

        val next = repository.next(System.currentTimeMillis())
        val earliest = allReminders().minByOrNull { it.triggerAtMillis }

        assertEquals(earliest?.reminderId, next?.reminderId)
    }

    @Test
    fun deletingAPatientTakesTheirRemindersWithThem() = runTest {
        seedPatient()
        insertMedicine(times = "480")
        repository.rebuild(USER_ID)
        assertTrue(allReminders().isNotEmpty())

        database.userDao().deleteAll()

        assertEquals(0, allReminders().size)
    }

    /** Every row for this account, whatever its status. */
    private suspend fun allReminders() =
        database.reminderDao().observeBetween(USER_ID, 0L, Long.MAX_VALUE).first()

    private suspend fun seedPatient() {
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
    }

    private suspend fun seedFacility() {
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

    private suspend fun insertMedicine(
        times: String,
        frequency: MedicineFrequency = MedicineFrequency.DAILY,
        isActive: Boolean = true,
        startDate: LocalDate = LocalDate.now().minusDays(1),
        endDate: LocalDate? = null,
    ) {
        database.medicineDao().upsert(
            MedicineEntity(
                medicineId = MEDICINE_ID,
                userId = USER_ID,
                patientId = PATIENT_ID,
                name = "Metformin",
                frequency = frequency,
                reminderTimes = times,
                startDateEpochDay = startDate.toEpochDay(),
                endDateEpochDay = endDate?.toEpochDay(),
                isActive = isActive,
                createdAt = 0L,
                updatedAt = 0L,
                syncStatus = SyncStatus.SYNCED,
            ),
        )
    }

    /** Minute-of-day a trigger falls on, in the device's zone. */
    private fun minuteOfDay(millis: Long, zone: ZoneId): Int {
        val time: LocalTime = Instant.ofEpochMilli(millis).atZone(zone).toLocalTime()
        return time.hour * 60 + time.minute
    }

    private object TestDispatchers : DispatcherProvider {
        override val io: CoroutineDispatcher = Dispatchers.Unconfined
        override val default: CoroutineDispatcher = Dispatchers.Unconfined
        override val main: CoroutineDispatcher = Dispatchers.Unconfined
    }

    private companion object {
        const val USER_ID = "u1"
        const val PATIENT_ID = "p1"
        const val VISIT_ID = "v1"
        const val FACILITY_ID = "f1"
        const val MEDICINE_ID = "m1"
    }
}
