package com.ss.medrecord.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import com.ss.medrecord.domain.model.Reminder
import com.ss.medrecord.domain.model.ReminderStatus
import com.ss.medrecord.domain.model.ReminderType

/**
 * Scheduled notifications for this device only.
 *
 * There is no sync_status column, and that is deliberate rather than an
 * oversight - see [Reminder] for why reminders never leave the device. The
 * absence of the column is what stops a future syncer being wired up by
 * reflex.
 *
 * The patient foreign key cascades so that deleting a profile silently takes
 * its pending alarms' rows with it. The alarm itself is re-armed from the table
 * on the next sweep, so a row that vanishes is an alarm that stops.
 */
@Entity(
    tableName = "reminders",
    foreignKeys = [
        ForeignKey(
            entity = PatientEntity::class,
            parentColumns = ["patient_id"],
            childColumns = ["patient_id"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [
        Index("user_id"),
        Index("patient_id"),
        // The sweep asks one question: what is scheduled, soonest first.
        Index(value = ["status", "trigger_at_millis"]),
        // Regeneration replaces everything derived from one medicine or visit.
        Index(value = ["source_id", "trigger_at_millis"], unique = true),
    ],
)
data class ReminderEntity(
    @PrimaryKey(autoGenerate = true)
    @ColumnInfo(name = "reminder_id")
    val reminderId: Long = 0L,
    @ColumnInfo(name = "user_id")
    val userId: String,
    @ColumnInfo(name = "patient_id")
    val patientId: String,
    val type: ReminderType,
    @ColumnInfo(name = "source_id")
    val sourceId: String,
    @ColumnInfo(name = "trigger_at_millis")
    val triggerAtMillis: Long,
    val title: String,
    val message: String,
    val status: ReminderStatus = ReminderStatus.SCHEDULED,
)

fun ReminderEntity.toDomain(): Reminder = Reminder(
    reminderId = reminderId,
    userId = userId,
    patientId = patientId,
    type = type,
    sourceId = sourceId,
    triggerAtMillis = triggerAtMillis,
    title = title,
    message = message,
    status = status,
)

fun Reminder.toEntity(): ReminderEntity = ReminderEntity(
    reminderId = reminderId,
    userId = userId,
    patientId = patientId,
    type = type,
    sourceId = sourceId,
    triggerAtMillis = triggerAtMillis,
    title = title,
    message = message,
    status = status,
)
