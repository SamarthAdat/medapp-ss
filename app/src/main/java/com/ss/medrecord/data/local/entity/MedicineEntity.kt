package com.ss.medrecord.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import com.ss.medrecord.domain.model.Medicine
import com.ss.medrecord.domain.model.MedicineFrequency
import com.ss.medrecord.domain.model.SyncStatus

/**
 * Deleting a patient cascades to their medicines (spec 9.6). The visit link
 * does not: a medicine outlives the appointment that prescribed it, and a visit
 * removed by mistake must not take the prescription with it. SET NULL is the
 * honest outcome - the medicine is still being taken, it just no longer knows
 * which visit it came from.
 */
@Entity(
    tableName = "medicines",
    foreignKeys = [
        ForeignKey(
            entity = PatientEntity::class,
            parentColumns = ["patient_id"],
            childColumns = ["patient_id"],
            onDelete = ForeignKey.CASCADE,
        ),
        ForeignKey(
            entity = VisitEntity::class,
            parentColumns = ["visit_id"],
            childColumns = ["visit_id"],
            onDelete = ForeignKey.SET_NULL,
        ),
    ],
    indices = [
        Index("user_id"),
        Index("visit_id"),
        // The medicine list filters by patient and deletion, then sorts by name.
        Index(value = ["patient_id", "deleted_at", "is_active"]),
    ],
)
data class MedicineEntity(
    @PrimaryKey
    @ColumnInfo(name = "medicine_id")
    val medicineId: String,
    @ColumnInfo(name = "user_id")
    val userId: String,
    @ColumnInfo(name = "patient_id")
    val patientId: String,
    @ColumnInfo(name = "visit_id")
    val visitId: String? = null,
    val name: String,
    val dosage: String? = null,
    val frequency: MedicineFrequency,
    /**
     * Minutes from midnight, comma-separated, e.g. "480,1200".
     *
     * A child table would be the textbook answer, but the list is at most eight
     * small integers, is always read and written whole with its medicine, and is
     * never queried across rows. A join table here would buy nothing and add a
     * second cascade path to reason about.
     */
    @ColumnInfo(name = "reminder_times")
    val reminderTimes: String = "",
    @ColumnInfo(name = "start_date_epoch_day")
    val startDateEpochDay: Long,
    @ColumnInfo(name = "end_date_epoch_day")
    val endDateEpochDay: Long? = null,
    val instructions: String? = null,
    @ColumnInfo(name = "is_active")
    val isActive: Boolean = true,
    @ColumnInfo(name = "created_at")
    val createdAt: Long,
    @ColumnInfo(name = "updated_at")
    val updatedAt: Long,
    @ColumnInfo(name = "deleted_at")
    val deletedAt: Long? = null,
    @ColumnInfo(name = "sync_status")
    val syncStatus: SyncStatus = SyncStatus.PENDING,
)

fun MedicineEntity.toDomain(): Medicine = Medicine(
    medicineId = medicineId,
    userId = userId,
    patientId = patientId,
    visitId = visitId,
    name = name,
    dosage = dosage,
    frequency = frequency,
    reminderTimes = decodeReminderTimes(reminderTimes),
    startDateEpochDay = startDateEpochDay,
    endDateEpochDay = endDateEpochDay,
    instructions = instructions,
    isActive = isActive,
    createdAt = createdAt,
    updatedAt = updatedAt,
    deletedAt = deletedAt,
)

fun Medicine.toEntity(syncStatus: SyncStatus = SyncStatus.PENDING): MedicineEntity = MedicineEntity(
    medicineId = medicineId,
    userId = userId,
    patientId = patientId,
    visitId = visitId,
    name = name,
    dosage = dosage,
    frequency = frequency,
    reminderTimes = encodeReminderTimes(reminderTimes),
    startDateEpochDay = startDateEpochDay,
    endDateEpochDay = endDateEpochDay,
    instructions = instructions,
    isActive = isActive,
    createdAt = createdAt,
    updatedAt = updatedAt,
    deletedAt = deletedAt,
    syncStatus = syncStatus,
)

/** Sorted and de-duplicated on the way in, so ordering is a storage invariant. */
fun encodeReminderTimes(times: List<Int>): String =
    times.filter { it in 0 until MINUTES_IN_DAY }.distinct().sorted().joinToString(",")

/**
 * Tolerant of anything unparseable: a malformed entry costs one reminder time,
 * whereas throwing would make the whole medicine unreadable.
 */
fun decodeReminderTimes(encoded: String): List<Int> =
    encoded.split(',')
        .mapNotNull { it.trim().toIntOrNull() }
        .filter { it in 0 until MINUTES_IN_DAY }
        .distinct()
        .sorted()

private const val MINUTES_IN_DAY = 24 * 60
