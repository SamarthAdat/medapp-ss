package com.ss.medrecord.data.reminder

import android.util.Log
import com.ss.medrecord.core.common.DispatcherProvider
import com.ss.medrecord.data.local.dao.MedicineDao
import com.ss.medrecord.data.local.dao.PatientDao
import com.ss.medrecord.data.local.dao.ReminderDao
import com.ss.medrecord.data.local.dao.VisitDao
import com.ss.medrecord.data.local.entity.ReminderEntity
import com.ss.medrecord.data.local.entity.VisitEntity
import com.ss.medrecord.data.local.entity.toDomain
import com.ss.medrecord.domain.model.Medicine
import com.ss.medrecord.domain.model.Reminder
import com.ss.medrecord.domain.model.ReminderStatus
import com.ss.medrecord.domain.model.ReminderType
import com.ss.medrecord.domain.repository.ReminderRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import java.time.Duration
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

private const val TAG = "ReminderRepository"

/**
 * Derives the reminder schedule from medicines and follow-up visits.
 *
 * The whole design rests on [rebuild] being idempotent and cheap enough to run
 * often. It is called after every medicine write, after every sync pull that
 * changed something, on boot, and once a day - so it must be safe to run at any
 * moment and must never produce a duplicate notification. Two things make that
 * true:
 *
 *  - only *future* SCHEDULED rows are cleared before regenerating, so anything
 *    already posted or already due is untouched;
 *  - the insert is IGNORE against a unique (source, trigger) index, so a row
 *    that survives regeneration keeps its identity and its status.
 *
 * Times are resolved in the device's zone at generation time rather than stored
 * as UTC offsets. A person taking a tablet at 08:00 means 08:00 where they are;
 * flying to another timezone should move the reminder, and it does, because the
 * next rebuild re-resolves every future row against the new zone.
 */
@Singleton
class ReminderRepositoryImpl @Inject constructor(
    private val reminderDao: ReminderDao,
    private val medicineDao: MedicineDao,
    private val visitDao: VisitDao,
    private val patientDao: PatientDao,
    private val dispatchers: DispatcherProvider,
) : ReminderRepository {

    override suspend fun rebuild(userId: String): Int = withContext(dispatchers.io) {
        val zone = ZoneId.systemDefault()
        val now = System.currentTimeMillis()
        val today = LocalDate.now(zone)
        val horizonEnd = today.plusDays(HORIZON_DAYS)

        reminderDao.deleteOlderThan(now - RETENTION_MILLIS)
        reminderDao.deleteFutureForUser(userId, now)

        // Patient names are denormalised into the notification text so the
        // receiver can post without a join - and so a notification still names
        // the right person if the profile is renamed after it was scheduled.
        val patientNames = mutableMapOf<String, String?>()
        suspend fun nameOf(patientId: String): String? = patientNames.getOrPut(patientId) {
            patientDao.getPatient(patientId)?.name
        }

        val generated = mutableListOf<ReminderEntity>()

        medicineDao.getSchedulable(userId, today.toEpochDay()).forEach { entity ->
            val medicine = entity.toDomain()
            if (!medicine.frequency.schedulesDoses || medicine.reminderTimes.isEmpty()) {
                return@forEach
            }
            generated += medicineReminders(
                medicine = medicine,
                patientName = nameOf(medicine.patientId),
                from = today,
                until = horizonEnd,
                notBeforeMillis = now,
                zone = zone,
            )
        }

        visitDao.getUpcomingFollowUps(
            userId = userId,
            fromEpochDay = today.toEpochDay(),
            // Lead days mean a visit just past the horizon still needs its
            // earlier reminder generated now.
            toEpochDay = horizonEnd.toEpochDay() + MAX_VISIT_LEAD_DAYS,
        ).forEach { visit ->
            generated += visitReminders(
                visit = visit,
                patientName = nameOf(visit.patientId),
                notBeforeMillis = now,
                horizonEndMillis = horizonEnd.atStartOfDay(zone).toInstant().toEpochMilli(),
                zone = zone,
            )
        }

        if (generated.isEmpty()) return@withContext 0

        reminderDao.insertAll(generated)
        Log.d(TAG, "Rebuilt schedule: ${generated.size} reminder(s) through $horizonEnd")
        generated.size
    }

    private fun medicineReminders(
        medicine: Medicine,
        patientName: String?,
        from: LocalDate,
        until: LocalDate,
        notBeforeMillis: Long,
        zone: ZoneId,
    ): List<ReminderEntity> {
        val out = mutableListOf<ReminderEntity>()
        var day = from
        while (!day.isAfter(until)) {
            if (medicine.fallsOn(day)) {
                medicine.times.forEach { time ->
                    val triggerAt = day.atTime(time).atZone(zone).toInstant().toEpochMilli()
                    if (triggerAt > notBeforeMillis) {
                        out += ReminderEntity(
                            userId = medicine.userId,
                            patientId = medicine.patientId,
                            type = ReminderType.MEDICINE,
                            sourceId = medicine.medicineId,
                            triggerAtMillis = triggerAt,
                            title = medicineTitle(medicine),
                            message = medicineMessage(medicine, patientName, time),
                        )
                    }
                }
            }
            day = day.plusDays(1)
        }
        return out
    }

    private fun visitReminders(
        visit: VisitEntity,
        patientName: String?,
        notBeforeMillis: Long,
        horizonEndMillis: Long,
        zone: ZoneId,
    ): List<ReminderEntity> {
        val nextDate = visit.nextVisitDateEpochDay?.let(LocalDate::ofEpochDay) ?: return emptyList()
        return VISIT_LEAD_DAYS.mapNotNull { leadDays ->
            val at = nextDate.minusDays(leadDays)
                .atTime(VISIT_REMINDER_TIME)
                .atZone(zone)
                .toInstant()
                .toEpochMilli()
            if (at <= notBeforeMillis || at > horizonEndMillis) return@mapNotNull null
            ReminderEntity(
                userId = visit.userId,
                patientId = visit.patientId,
                type = ReminderType.VISIT,
                sourceId = visit.visitId,
                triggerAtMillis = at,
                title = if (leadDays == 0L) "Appointment today" else "Appointment tomorrow",
                message = buildString {
                    patientName?.let { append(it).append(" · ") }
                    append("Follow-up visit")
                    visit.doctorName?.takeIf { it.isNotBlank() }?.let { append(" with ").append(it) }
                },
            )
        }
    }

    private fun medicineTitle(medicine: Medicine): String = "Time for ${medicine.name}"

    private fun medicineMessage(
        medicine: Medicine,
        patientName: String?,
        time: LocalTime,
    ): String = buildString {
        patientName?.let { append(it).append(" · ") }
        append(Medicine.formatTime(time))
        medicine.dosage?.takeIf { it.isNotBlank() }?.let { append(" · ").append(it) }
        medicine.instructions?.takeIf { it.isNotBlank() }?.let { append(" · ").append(it) }
    }

    override suspend fun due(nowMillis: Long): List<Reminder> = withContext(dispatchers.io) {
        reminderDao.getDue(
            untilMillis = nowMillis + DUE_SLACK_MILLIS,
            notBeforeMillis = nowMillis - STALE_AFTER_MILLIS,
        ).map { it.toDomain() }
    }

    override suspend fun next(afterMillis: Long): Reminder? = withContext(dispatchers.io) {
        reminderDao.nextScheduled(afterMillis)?.toDomain()
    }

    override fun observeToday(userId: String): Flow<List<Reminder>> {
        val zone = ZoneId.systemDefault()
        val startOfDay = LocalDate.now(zone).atStartOfDay(zone).toInstant().toEpochMilli()
        return reminderDao
            .observeBetween(userId, startOfDay, startOfDay + TimeUnit.DAYS.toMillis(1))
            .map { rows -> rows.map { it.toDomain() } }
    }

    override suspend fun getReminder(reminderId: Long): Reminder? = withContext(dispatchers.io) {
        reminderDao.getReminder(reminderId)?.toDomain()
    }

    override suspend fun markFired(reminderIds: List<Long>) = withContext(dispatchers.io) {
        if (reminderIds.isNotEmpty()) reminderDao.setStatus(reminderIds, ReminderStatus.FIRED)
    }

    override suspend fun dismiss(reminderId: Long) = withContext(dispatchers.io) {
        reminderDao.setStatus(listOf(reminderId), ReminderStatus.DISMISSED)
    }

    override suspend fun clear() = withContext(dispatchers.io) {
        reminderDao.deleteAll()
    }

    private companion object {
        /**
         * How far ahead rows are generated. A week is enough that a device left
         * offline and unopened keeps reminding, and small enough that the table
         * stays in the hundreds of rows.
         */
        const val HORIZON_DAYS = 7L

        /** Fired rows are kept this long so a sweep cannot re-post them. */
        val RETENTION_MILLIS: Long = Duration.ofDays(2).toMillis()

        /**
         * Alarms are approximate even when exact, and a sweep may run a moment
         * early. Everything within this window of the alarm is posted together,
         * which is also what groups two medicines due at 08:00 into one wake-up.
         */
        val DUE_SLACK_MILLIS: Long = Duration.ofMinutes(2).toMillis()

        /**
         * Past this, a missed reminder is not posted at all. Telling someone at
         * breakfast about a dose due two nights ago is noise, and noise is what
         * gets a medication reminder muted.
         */
        val STALE_AFTER_MILLIS: Long = Duration.ofHours(4).toMillis()

        /** Day before, and the morning of. */
        val VISIT_LEAD_DAYS = listOf(1L, 0L)

        /** The earliest of [VISIT_LEAD_DAYS]; widens the follow-up query. */
        const val MAX_VISIT_LEAD_DAYS = 1L

        /** Early enough to act on, late enough not to wake anyone. */
        val VISIT_REMINDER_TIME: LocalTime = LocalTime.of(9, 0)
    }
}
